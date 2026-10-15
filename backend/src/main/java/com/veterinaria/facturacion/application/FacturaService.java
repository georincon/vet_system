package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturarPresupuestoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.LineaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PagoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PagoResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.Persona;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.ResolucionResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.TutorResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.VentaPosRequest;
import com.veterinaria.facturacion.domain.FacturacionElectronicaPort;
import com.veterinaria.facturacion.domain.Importes;
import com.veterinaria.inventario.StockApi;
import com.veterinaria.inventario.StockApi.LoteConsumido;
import com.veterinaria.inventario.StockApi.SalidaFefo;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Emision de facturas y ventas POS. Todo ocurre en una transaccion: numero (resolucion con bloqueo), renglones,
 * descuento de stock FEFO, pagos y solicitud al puerto DIAN; si algo falla (p. ej. 409 de stock) no queda nada.
 *
 * <p>Momento del descuento de stock: <b>al emitir la factura</b> (no al cotizar ni al cerrar la consulta). Para no
 * descontar dos veces lo que un acto clinico ya saco del lote (vacuna/antiparasitario con {@code loteId} y consulta),
 * cada renglon con consulta resta lo ya descontado por esos actos ({@code StockApi.descontadoPorActosClinicos}) menos lo
 * ya cubierto por otras facturas, y lo registra en {@code factura_item.cantidad_stock_cubierta}.
 */
@Service
public class FacturaService {

    private static final String CONSUMIDOR_FINAL = "222222222222";
    private static final int DIAS_CREDITO = 30;

    /** Datos de emision ya validados por el llamador. */
    record Emision(String tipo, Long tutorId, Long pacienteId, Long consultaId, Long presupuestoId, String condicion,
            LocalDate vence, String notas, Long bodegaId, List<LineaResuelta> lineas, List<PagoRequest> pagos) {
    }

    private final JdbcClient jdbc;
    private final NumeracionService numeracion;
    private final PagoService pagos;
    private final CajaService caja;
    private final StockApi stock;
    private final LineaResolver resolver;
    private final FacturacionElectronicaPort dian;
    private final SedePrincipal sede;

    public FacturaService(JdbcClient jdbc, NumeracionService numeracion, PagoService pagos, CajaService caja,
            StockApi stock, LineaResolver resolver, FacturacionElectronicaPort dian, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.numeracion = numeracion;
        this.pagos = pagos;
        this.caja = caja;
        this.stock = stock;
        this.resolver = resolver;
        this.dian = dian;
        this.sede = sede;
    }

    // ------------------------------------------------------------------ entradas

    @Transactional
    public FacturaResponse crear(FacturaRequest r) {
        if (r.presupuestoId() != null) {
            if (r.items() != null && !r.items().isEmpty()) {
                throw new BusinessException("Con presupuestoId no se envian items: se factura el presupuesto");
            }
            return facturarPresupuesto(r.presupuestoId(), new FacturarPresupuestoRequest(r.condicionPago(),
                    r.fechaVencimiento(), r.notas(), r.bodegaId(), r.pagos()));
        }
        if (r.tutorId() == null) {
            throw new BusinessException("El tutor es obligatorio");
        }
        List<LineaResuelta> lineas = resolver.resolver(r.items(), r.pacienteId(), r.consultaId());
        return emitir(new Emision("FACTURA", r.tutorId(), r.pacienteId(), r.consultaId(), null,
                r.condicionPago() == null ? "CONTADO" : r.condicionPago(), r.fechaVencimiento(), r.notas(), r.bodegaId(),
                lineas, r.pagos()));
    }

    @Transactional
    public FacturaResponse pos(VentaPosRequest r) {
        Long tutor = r.tutorId() != null ? r.tutorId() : consumidorFinal();
        List<LineaResuelta> lineas = resolver.resolver(r.items(), r.pacienteId(), null);
        return emitir(new Emision("POS", tutor, r.pacienteId(), null, null, "CONTADO", null, r.notas(), r.bodegaId(),
                lineas, r.pagos()));
    }

    @Transactional
    public FacturaResponse facturarPresupuesto(Long presupuestoId, FacturarPresupuestoRequest r) {
        record P(Long tutorId, Long pacienteId, Long consultaId, String estado) {
        }
        P p = jdbc.sql("select tutor_id, paciente_id, consulta_id, estado from presupuesto where id = :id for update")
                .param("id", presupuestoId).query((rs, n) -> new P(rs.getLong(1), (Long) rs.getObject(2),
                        (Long) rs.getObject(3), rs.getString(4))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Presupuesto", presupuestoId));
        if (!List.of("BORRADOR", "ENVIADO", "ACEPTADO").contains(p.estado())) {
            throw new com.veterinaria.shared.error.ConflictException("El presupuesto esta " + p.estado()
                    + ": solo se factura uno BORRADOR, ENVIADO o ACEPTADO");
        }
        List<LineaResuelta> lineas = jdbc.sql("""
                select pi.*, i.codigo, i.controla_stock from presupuesto_item pi left join item i on i.id = pi.item_id
                where pi.presupuesto_id = :id order by pi.id
                """).param("id", presupuestoId).query((rs, n) -> {
                    BigDecimal cantidad = rs.getBigDecimal("cantidad");
                    BigDecimal precio = rs.getBigDecimal("precio_unitario");
                    BigDecimal pct = rs.getBigDecimal("impuesto_pct");
                    Importes.Linea calc = Importes.calcular(cantidad, precio, rs.getBigDecimal("descuento"), pct);
                    boolean controla = rs.getBoolean("controla_stock");
                    return new LineaResuelta((Long) rs.getObject("item_id"), rs.getString("codigo"),
                            rs.getString("descripcion"), cantidad, precio, pct, calc.descuento(), calc, p.pacienteId(),
                            null, p.consultaId(), controla, controla, rs.getString("origen_tabla"),
                            (Long) rs.getObject("origen_id"));
                }).list();
        if (lineas.isEmpty()) {
            throw new BusinessException("El presupuesto no tiene renglones");
        }
        return emitir(new Emision("FACTURA", p.tutorId(), p.pacienteId(), p.consultaId(), presupuestoId,
                r.condicionPago() == null ? "CONTADO" : r.condicionPago(), r.fechaVencimiento(), r.notas(), r.bodegaId(),
                lineas, r.pagos()));
    }

    // ------------------------------------------------------------------ emision

    private FacturaResponse emitir(Emision e) {
        boolean pos = e.tipo().equals("POS");
        record T(boolean activo, boolean bloqueado, String documento) {
        }
        T tutor = jdbc.sql("select activo, bloqueado_cartera, numero_documento from tutor where id = :id")
                .param("id", e.tutorId()).query((rs, n) -> new T(rs.getBoolean(1), rs.getBoolean(2), rs.getString(3)))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Tutor", e.tutorId()));
        if (!tutor.activo()) {
            throw new BusinessException("El tutor esta inactivo");
        }
        boolean consumidorFinal = CONSUMIDOR_FINAL.equals(tutor.documento());
        if (e.pacienteId() != null && jdbc.sql("select count(*) from paciente where id = :id").param("id", e.pacienteId())
                .query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Paciente", e.pacienteId());
        }
        Importes.Linea totales = Importes.suma(e.lineas().stream().map(LineaResuelta::calc).toList());
        BigDecimal total = totales.total();
        List<PagoRequest> pagosReq = e.pagos() == null ? List.of() : e.pagos();
        BigDecimal pagado = pagosReq.stream().map(PagoRequest::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean credito = "CREDITO".equals(e.condicion());
        if (credito && consumidorFinal) {
            throw new BusinessException("No se vende a credito al Consumidor final");
        }
        if (pos && pagado.compareTo(total) != 0) {
            throw new BusinessException("Los pagos (" + pagado + ") deben cuadrar con el total de la venta (" + total + ")");
        }
        if (!credito && !pagosReq.isEmpty() && pagado.compareTo(total) != 0) {
            throw new BusinessException("Factura de contado: los pagos (" + pagado + ") deben cuadrar con el total ("
                    + total + "); use CREDITO para un abono parcial o registre los pagos despues");
        }
        if (credito && pagado.compareTo(total) > 0) {
            throw new BusinessException("Los pagos (" + pagado + ") exceden el total (" + total + ")");
        }
        if (tutor.bloqueado() && (credito || pagado.compareTo(total) < 0)) {
            throw new BusinessException("El tutor tiene la cartera bloqueada: la venta debe pagarse completa en el acto");
        }
        Long turno = pagosReq.isEmpty() ? null : caja.turnoAbiertoDelUsuario();
        LocalDate vence = credito ? (e.vence() != null ? e.vence() : ZonaClinica.hoy().plusDays(DIAS_CREDITO)) : null;
        if (vence != null && vence.isBefore(ZonaClinica.hoy())) {
            throw new BusinessException("La fecha de vencimiento no puede ser anterior a hoy");
        }

        NumeracionService.Numero num = numeracion.siguiente(pos ? "POS" : "FACTURA");
        Long facturaId = jdbc.sql("""
                insert into factura (numero, tutor_id, paciente_id, subtotal, descuento, impuestos, total, saldo, estado, notas,
                                     sede_id, tipo_documento, resolucion_id, consulta_id, presupuesto_id, condicion_pago,
                                     fecha_vencimiento, estado_dian, creado_por)
                values (:numero, :tutor, :paciente, :sub, :desc, :imp, :total, :total, 'EMITIDA', :notas, :sede, :tipo, :res,
                        :consulta, :presupuesto, :condicion, :vence, 'PENDIENTE', :uid)
                returning id
                """).param("numero", num.numero()).param("tutor", e.tutorId()).param("paciente", e.pacienteId())
                .param("sub", totales.bruto()).param("desc", totales.descuento()).param("imp", totales.impuesto())
                .param("total", total).param("notas", Textos.limpiar(e.notas())).param("sede", sede.id())
                .param("tipo", e.tipo()).param("res", num.resolucionId()).param("consulta", e.consultaId())
                .param("presupuesto", e.presupuestoId()).param("condicion", credito ? "CREDITO" : "CONTADO")
                .param("vence", vence).param("uid", UsuarioActual.id().orElse(null)).query(Long.class).single();

        for (LineaResuelta l : e.lineas()) {
            insertarRenglon(facturaId, num.numero(), l, e);
        }
        for (PagoRequest p : pagosReq) {
            pagos.registrar(facturaId, p, turno);
        }
        // Con pagos aplicados o total en 0 el estado se resuelve aqui (PAGADA/PARCIAL/EMITIDA).
        pagos.recalcular(facturaId);
        if (e.presupuestoId() != null) {
            jdbc.sql("update presupuesto set estado = 'FACTURADO', actualizado_en = now() where id = :id")
                    .param("id", e.presupuestoId()).update();
        }
        solicitarDian(facturaId);
        return obtener(facturaId);
    }

    private void insertarRenglon(Long facturaId, String numero, LineaResuelta l, Emision e) {
        Long itemId = jdbc.sql("""
                insert into factura_item (factura_id, consulta_id, item_id, descripcion, cantidad, precio_unitario,
                                          impuesto_pct, descuento, total, veterinario_id, paciente_id)
                values (:f, :c, :i, :d, :q, :p, :pct, :desc, :t, :vet, :pac) returning id
                """).param("f", facturaId).param("c", l.consultaId()).param("i", l.itemId())
                .param("d", l.descripcion()).param("q", l.cantidad()).param("p", l.precio()).param("pct", l.impuestoPct())
                .param("desc", l.descuento()).param("t", l.calc().total()).param("vet", l.veterinarioId())
                .param("pac", l.pacienteId()).query(Long.class).single();
        if (!l.controlaStock() || !l.descontarStock() || l.itemId() == null) {
            return;
        }
        BigDecimal cubierta = BigDecimal.ZERO;
        if (l.consultaId() != null) {
            BigDecimal clinico = stock.descontadoPorActosClinicos(l.consultaId(), l.itemId());
            BigDecimal yaCubierto = jdbc.sql("""
                    select coalesce(sum(fi.cantidad_stock_cubierta), 0) from factura_item fi join factura f on f.id = fi.factura_id
                    where fi.consulta_id = :c and fi.item_id = :i and f.tipo_documento in ('FACTURA', 'POS')
                      and f.estado <> 'ANULADA' and fi.id <> :self
                    """).param("c", l.consultaId()).param("i", l.itemId()).param("self", itemId)
                    .query(BigDecimal.class).single();
            cubierta = clinico.subtract(yaCubierto).max(BigDecimal.ZERO).min(l.cantidad());
        }
        BigDecimal descontar = l.cantidad().subtract(cubierta);
        Long loteId = null;
        if (descontar.signum() > 0) {
            Long vet = l.veterinarioId() != null ? l.veterinarioId() : veterinarioDeConsulta(l.consultaId());
            Long usuario = UsuarioActual.id().orElse(null);
            List<LoteConsumido> usados = stock.descontarFefo(new SalidaFefo(l.itemId(), descontar, e.bodegaId(),
                    "Venta " + numero, "FACTURA:" + numero, usuario, l.pacienteId(), l.consultaId(), vet != null ? vet : usuario,
                    itemId));
            loteId = usados.get(0).loteId();
        }
        jdbc.sql("update factura_item set cantidad_stock_cubierta = :c, lote_id = :l where id = :id")
                .param("c", cubierta).param("l", loteId).param("id", itemId).update();
    }

    private Long veterinarioDeConsulta(Long consultaId) {
        if (consultaId == null) {
            return null;
        }
        return jdbc.sql("select veterinario_id from consulta where id = :id").param("id", consultaId).query(Long.class)
                .optional().orElse(null);
    }

    /** Tutor generico de mostrador (NIT 222222222222); se recrea si falta (la semilla V19 pudo borrarse). */
    private Long consumidorFinal() {
        jdbc.sql("""
                insert into tutor (tipo_documento, numero_documento, nombres, apellidos)
                values ('OTRO', :d, 'Consumidor', 'Final') on conflict (tipo_documento, numero_documento) do nothing
                """).param("d", CONSUMIDOR_FINAL).update();
        return jdbc.sql("select id from tutor where numero_documento = :d order by id limit 1").param("d", CONSUMIDOR_FINAL)
                .query(Long.class).single();
    }

    // ------------------------------------------------------------------ DIAN (puerto)

    @Transactional(propagation = Propagation.MANDATORY)
    void solicitarDian(Long facturaId) {
        FacturaResponse f = obtener(facturaId);
        FacturacionElectronicaPort.Documento doc = new FacturacionElectronicaPort.Documento(f.id(), f.numero(),
                f.tipoDocumento(), f.resolucion() == null ? null : f.resolucion().prefijo(),
                f.resolucion() == null ? null : f.resolucion().numeroResolucion(), f.tutor().documento(), f.tutor().nombre(),
                f.subtotal(), f.descuento(), f.impuestos(), f.total(), f.numeroOrigen(),
                f.items().stream().map(l -> new FacturacionElectronicaPort.Linea(l.descripcion(), l.cantidad(),
                        l.precioUnitario(), l.impuestoPct(), l.descuento(), l.total())).toList());
        FacturacionElectronicaPort.Resultado res = dian.emitir(doc);
        jdbc.sql("update factura set estado_dian = :e, cufe = :c, dian_mensaje = :m where id = :id")
                .param("e", res.estado()).param("c", res.cufe()).param("m", res.mensaje()).param("id", facturaId).update();
    }

    @Transactional
    public FacturaResponse reintentarDian(Long id) {
        FacturaResponse f = obtener(id);
        if (f.estadoDian().equals("ACEPTADA")) {
            throw new com.veterinaria.shared.error.ConflictException("El documento ya fue aceptado por la DIAN");
        }
        solicitarDian(id);
        return obtener(id);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PageResponse<FacturaResumen> listar(Long tutorId, Long pacienteId, String estado, String tipo, String q,
            LocalDate desde, LocalDate hasta, Boolean conSaldo, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
        if (tutorId != null) {
            where.append(" and f.tutor_id = :tutor");
            p.put("tutor", tutorId);
        }
        if (pacienteId != null) {
            where.append(" and f.paciente_id = :pac");
            p.put("pac", pacienteId);
        }
        if (estado != null) {
            where.append(" and f.estado = :estado");
            p.put("estado", estado);
        }
        if (tipo != null) {
            where.append(" and f.tipo_documento = :tipo");
            p.put("tipo", tipo);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and f.numero ilike :q");
            p.put("q", "%" + q.trim() + "%");
        }
        if (desde != null) {
            where.append(" and f.fecha >= :desde");
            p.put("desde", java.sql.Timestamp.from(ZonaClinica.instante(desde, java.time.LocalTime.MIN)));
        }
        if (hasta != null) {
            where.append(" and f.fecha < :hasta");
            p.put("hasta", java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), java.time.LocalTime.MIN)));
        }
        if (Boolean.TRUE.equals(conSaldo)) {
            where.append(" and f.saldo > 0");
        }
        long total = jdbc.sql("select count(*) from factura f" + where).params(p).query(Long.class).single();
        List<FacturaResumen> items = jdbc.sql("""
                select f.id, f.numero, f.tipo_documento, f.estado, f.estado_dian, f.fecha, f.total, f.saldo, f.condicion_pago,
                       f.fecha_vencimiento, t.id as tutor_id, t.nombres || coalesce(' ' || t.apellidos, '') as tutor,
                       t.tipo_documento || ' ' || t.numero_documento as documento
                from factura f join tutor t on t.id = f.tutor_id
                """ + where + " order by f.fecha desc, f.id desc limit :lim offset :off").params(p)
                .param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> new FacturaResumen(rs.getLong("id"), rs.getString("numero"), rs.getString("tipo_documento"),
                        rs.getString("estado"), rs.getString("estado_dian"),
                        new TutorResumen(rs.getLong("tutor_id"), rs.getString("tutor"), rs.getString("documento")),
                        rs.getTimestamp("fecha").toInstant(), rs.getBigDecimal("total"), rs.getBigDecimal("saldo"),
                        rs.getString("condicion_pago"),
                        rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate()))
                .list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public FacturaResponse obtener(Long id) {
        record Cab(Long id, String numero, String tipo, String estado, String dian, Long tutorId, String tutor, String doc,
                Long pacienteId, String paciente, Long consultaId, Long presupuestoId, Long origenId, String numeroOrigen,
                Instant fecha, BigDecimal subtotal, BigDecimal descuento, BigDecimal impuestos, BigDecimal total,
                BigDecimal saldo, String condicion, LocalDate vence, String notas, String motivo, String cufe, String dianMsg,
                Long resId, String prefijo, String numRes, String ambiente) {
        }
        Cab c = jdbc.sql("""
                select f.*, t.nombres || coalesce(' ' || t.apellidos, '') as tutor, t.tipo_documento || ' ' || t.numero_documento as doc,
                       p.nombre as paciente, o.numero as numero_origen, r.prefijo, r.numero_resolucion, r.ambiente
                from factura f join tutor t on t.id = f.tutor_id left join paciente p on p.id = f.paciente_id
                left join factura o on o.id = f.factura_origen_id left join resolucion_facturacion r on r.id = f.resolucion_id
                where f.id = :id
                """).param("id", id).query((rs, n) -> new Cab(rs.getLong("id"), rs.getString("numero"),
                        rs.getString("tipo_documento"), rs.getString("estado"), rs.getString("estado_dian"),
                        rs.getLong("tutor_id"), rs.getString("tutor"), rs.getString("doc"), (Long) rs.getObject("paciente_id"),
                        rs.getString("paciente"), (Long) rs.getObject("consulta_id"), (Long) rs.getObject("presupuesto_id"),
                        (Long) rs.getObject("factura_origen_id"), rs.getString("numero_origen"),
                        rs.getTimestamp("fecha").toInstant(), rs.getBigDecimal("subtotal"), rs.getBigDecimal("descuento"),
                        rs.getBigDecimal("impuestos"), rs.getBigDecimal("total"), rs.getBigDecimal("saldo"),
                        rs.getString("condicion_pago"),
                        rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate(),
                        rs.getString("notas"), rs.getString("motivo_anulacion"), rs.getString("cufe"),
                        rs.getString("dian_mensaje"), (Long) rs.getObject("resolucion_id"), rs.getString("prefijo"),
                        rs.getString("numero_resolucion"), rs.getString("ambiente"))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));
        List<LineaResponse> items = jdbc.sql("""
                select fi.*, i.codigo,
                       coalesce((select sum(n.cantidad) from factura_item n join factura nf on nf.id = n.factura_id
                                 where n.factura_item_origen_id = fi.id and nf.tipo_documento = 'NOTA_CREDITO'), 0) as acreditada
                from factura_item fi left join item i on i.id = fi.item_id where fi.factura_id = :id order by fi.id
                """).param("id", id).query((rs, n) -> {
                    BigDecimal bruto = Importes.dinero(rs.getBigDecimal("cantidad").multiply(rs.getBigDecimal("precio_unitario")));
                    BigDecimal base = bruto.subtract(rs.getBigDecimal("descuento"));
                    return new LineaResponse(rs.getLong("id"), (Long) rs.getObject("item_id"), rs.getString("codigo"),
                            rs.getString("descripcion"), rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario"),
                            rs.getBigDecimal("impuesto_pct"), rs.getBigDecimal("descuento"), base,
                            rs.getBigDecimal("total").subtract(base), rs.getBigDecimal("total"),
                            (Long) rs.getObject("consulta_id"), (Long) rs.getObject("paciente_id"),
                            (Long) rs.getObject("veterinario_id"), (Long) rs.getObject("lote_id"),
                            rs.getBigDecimal("acreditada"), rs.getBigDecimal("cantidad_stock_cubierta"));
                }).list();
        List<PagoResponse> pagosRes = jdbc.sql("""
                select p.*, u.nombre as usuario from pago p left join usuario u on u.id = p.usuario_id
                where p.factura_id = :id order by p.id
                """).param("id", id).query((rs, n) -> new PagoResponse(rs.getLong("id"), rs.getTimestamp("fecha").toInstant(),
                        rs.getString("metodo"), rs.getBigDecimal("monto"), rs.getString("referencia"), rs.getString("estado"),
                        (Long) rs.getObject("turno_id"),
                        rs.getObject("usuario_id") == null ? null : new Persona(rs.getLong("usuario_id"), rs.getString("usuario"))))
                .list();
        BigDecimal pagado = pagosRes.stream().filter(p -> p.estado().equals("APLICADO")).map(PagoResponse::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<NotaResumen> notas = jdbc.sql("""
                select id, numero, fecha, total, motivo_anulacion from factura where factura_origen_id = :id
                  and tipo_documento = 'NOTA_CREDITO' order by id
                """).param("id", id).query((rs, n) -> new NotaResumen(rs.getLong("id"), rs.getString("numero"),
                        rs.getTimestamp("fecha").toInstant(), rs.getBigDecimal("total"), rs.getString("motivo_anulacion")))
                .list();
        return new FacturaResponse(c.id(), c.numero(), c.tipo(), c.estado(), c.dian(),
                new TutorResumen(c.tutorId(), c.tutor(), c.doc()), c.pacienteId(), c.paciente(), c.consultaId(),
                c.presupuestoId(), c.origenId(), c.numeroOrigen(), c.fecha(), c.subtotal(), c.descuento(), c.impuestos(),
                c.total(), c.tipo().equals("NOTA_CREDITO") ? BigDecimal.ZERO : pagado, c.saldo(), c.condicion(), c.vence(),
                c.notas(), c.motivo(), c.cufe(), c.dianMsg(),
                c.resId() == null ? null : new ResolucionResumen(c.resId(), c.prefijo(), c.numRes(), c.ambiente()),
                items, pagosRes, notas);
    }

    /** Registra un pago sobre una factura (pagos mixtos = varias llamadas o la lista al crear) y devuelve la factura. */
    @Transactional
    public FacturaResponse pagar(Long facturaId, PagoRequest p) {
        pagos.registrar(facturaId, p);
        return obtener(facturaId);
    }

    /** Id del tutor de una factura (uso interno de cartera). */
    Optional<Long> tutorDe(Long facturaId) {
        return jdbc.sql("select tutor_id from factura where id = :id").param("id", facturaId).query(Long.class).optional();
    }

    List<Long> idsConSaldo(Long tutorId, List<Long> facturaIds) {
        List<Long> res = new ArrayList<>(jdbc.sql("""
                select id from factura where tutor_id = :t and saldo > 0 and tipo_documento in ('FACTURA', 'POS')
                  and estado in ('EMITIDA', 'PARCIAL') order by fecha, id
                """).param("t", tutorId).query(Long.class).list());
        if (facturaIds != null && !facturaIds.isEmpty()) {
            res.retainAll(facturaIds);
        }
        return res;
    }
}
