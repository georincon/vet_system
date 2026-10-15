package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaCreditoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaCreditoResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.NotaItemRequest;
import com.veterinaria.facturacion.domain.Importes;
import com.veterinaria.inventario.StockApi;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;

/**
 * Notas credito: la unica forma de anular o corregir una factura emitida (la factura no cambia montos). La nota es un
 * documento propio (tipo NOTA_CREDITO, numero de su resolucion) con renglones que referencian los de la factura; la
 * factura pasa a ANULADA solo si queda acreditada por completo. Lo ya pagado que exceda el nuevo neto se reporta como
 * {@code montoADevolver} y, si se pide devolucion en EFECTIVO, se registra como egreso del turno abierto.
 */
@Service
public class NotaCreditoService {

    private final JdbcClient jdbc;
    private final NumeracionService numeracion;
    private final PagoService pagos;
    private final CajaService caja;
    private final FacturaService facturas;
    private final StockApi stock;
    private final SedePrincipal sede;

    public NotaCreditoService(JdbcClient jdbc, NumeracionService numeracion, PagoService pagos, CajaService caja,
            FacturaService facturas, StockApi stock, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.numeracion = numeracion;
        this.pagos = pagos;
        this.caja = caja;
        this.facturas = facturas;
        this.stock = stock;
        this.sede = sede;
    }

    private record Renglon(Long id, Long itemId, String descripcion, BigDecimal cantidad, BigDecimal precio,
            BigDecimal pct, BigDecimal descuento, BigDecimal total, BigDecimal cantidadAcreditada,
            BigDecimal descuentoAcreditado, BigDecimal totalAcreditado, boolean controlaStock) {
        BigDecimal pendiente() {
            return cantidad.subtract(cantidadAcreditada);
        }
    }

    @Transactional
    public NotaCreditoResponse acreditar(Long facturaId, NotaCreditoRequest r) {
        record F(String numero, String tipo, String estado, Long tutorId, Long pacienteId, Long consultaId, BigDecimal saldo) {
        }
        F f = jdbc.sql("select numero, tipo_documento, estado, tutor_id, paciente_id, consulta_id, saldo from factura where id = :id for update")
                .param("id", facturaId).query((rs, n) -> new F(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), (Long) rs.getObject(5), (Long) rs.getObject(6), rs.getBigDecimal(7))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Factura", facturaId));
        if (!f.tipo().equals("FACTURA") && !f.tipo().equals("POS")) {
            throw new BusinessException("Solo se acreditan facturas o ventas POS");
        }
        if (f.estado().equals("ANULADA")) {
            throw new ConflictException("La factura " + f.numero() + " ya esta anulada (acreditada por completo)");
        }
        Map<Long, Renglon> renglones = new LinkedHashMap<>();
        jdbc.sql("""
                select fi.id, fi.item_id, fi.descripcion, fi.cantidad, fi.precio_unitario, fi.impuesto_pct, fi.descuento, fi.total,
                       coalesce(sum(n.cantidad), 0) as cant_ac, coalesce(sum(n.descuento), 0) as desc_ac,
                       coalesce(sum(n.total), 0) as total_ac, coalesce(i.controla_stock, false) as controla
                from factura_item fi left join item i on i.id = fi.item_id
                left join factura_item n on n.factura_item_origen_id = fi.id
                where fi.factura_id = :id group by fi.id, i.controla_stock order by fi.id
                """).param("id", facturaId).query((rs, n) -> {
                    Renglon x = new Renglon(rs.getLong("id"), (Long) rs.getObject("item_id"), rs.getString("descripcion"),
                            rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("impuesto_pct"),
                            rs.getBigDecimal("descuento"), rs.getBigDecimal("total"), rs.getBigDecimal("cant_ac"),
                            rs.getBigDecimal("desc_ac"), rs.getBigDecimal("total_ac"), rs.getBoolean("controla"));
                    renglones.put(x.id(), x);
                    return 0;
                }).list();

        // Cantidad a acreditar por renglon
        Map<Long, BigDecimal> aAcreditar = new LinkedHashMap<>();
        if (r.items() == null || r.items().isEmpty()) {
            renglones.values().stream().filter(x -> x.pendiente().signum() > 0)
                    .forEach(x -> aAcreditar.put(x.id(), x.pendiente()));
        } else {
            for (NotaItemRequest it : r.items()) {
                Renglon x = renglones.get(it.facturaItemId());
                if (x == null) {
                    throw new BusinessException("El renglon " + it.facturaItemId() + " no pertenece a la factura");
                }
                if (aAcreditar.containsKey(x.id())) {
                    throw new BusinessException("El renglon " + x.id() + " esta repetido");
                }
                if (it.cantidad().compareTo(x.pendiente()) > 0) {
                    throw new BusinessException("Se acredita mas de lo facturado en '" + x.descripcion() + "' (pendiente "
                            + x.pendiente() + ")");
                }
                aAcreditar.put(x.id(), it.cantidad());
            }
        }
        if (aAcreditar.isEmpty()) {
            throw new ConflictException("La factura no tiene renglones por acreditar");
        }

        // Importes de la nota (el ultimo tramo de un renglon toma el resto exacto para no acumular centavos de redondeo)
        record Calc(Renglon r, BigDecimal cantidad, BigDecimal descuento, BigDecimal total) {
        }
        List<Calc> calcs = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : aAcreditar.entrySet()) {
            Renglon x = renglones.get(e.getKey());
            BigDecimal cant = e.getValue();
            BigDecimal desc;
            BigDecimal total;
            if (cant.compareTo(x.pendiente()) == 0) {
                desc = x.descuento().subtract(x.descuentoAcreditado());
                total = x.total().subtract(x.totalAcreditado());
            } else {
                BigDecimal fraccion = cant.divide(x.cantidad(), 10, RoundingMode.HALF_UP);
                desc = Importes.dinero(x.descuento().multiply(fraccion));
                total = Importes.dinero(x.total().multiply(fraccion));
            }
            calcs.add(new Calc(x, cant, desc, total));
        }
        BigDecimal totalNota = calcs.stream().map(Calc::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descNota = calcs.stream().map(Calc::descuento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal brutoNota = calcs.stream().map(c -> Importes.dinero(c.cantidad().multiply(c.r().precio())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impNota = totalNota.subtract(brutoNota.subtract(descNota));

        NumeracionService.Numero num = numeracion.siguiente("NOTA_CREDITO");
        Long notaId = jdbc.sql("""
                insert into factura (numero, tutor_id, paciente_id, subtotal, descuento, impuestos, total, saldo, estado, notas,
                                     sede_id, tipo_documento, resolucion_id, factura_origen_id, consulta_id, condicion_pago,
                                     estado_dian, motivo_anulacion, creado_por)
                values (:numero, :tutor, :pac, :sub, :desc, :imp, :total, 0, 'EMITIDA', :notas, :sede, 'NOTA_CREDITO', :res,
                        :origen, :consulta, 'CONTADO', 'PENDIENTE', :motivo, :uid) returning id
                """).param("numero", num.numero()).param("tutor", f.tutorId()).param("pac", f.pacienteId())
                .param("sub", brutoNota).param("desc", descNota).param("imp", impNota).param("total", totalNota)
                .param("notas", "Nota credito de " + f.numero()).param("sede", sede.id()).param("res", num.resolucionId())
                .param("origen", facturaId).param("consulta", f.consultaId()).param("motivo", r.motivo().trim())
                .param("uid", UsuarioActual.id().orElse(null)).query(Long.class).single();
        for (Calc c : calcs) {
            jdbc.sql("""
                    insert into factura_item (factura_id, consulta_id, item_id, descripcion, cantidad, precio_unitario,
                                              impuesto_pct, descuento, total, paciente_id, factura_item_origen_id)
                    values (:f, :consulta, :i, :d, :q, :p, :pct, :desc, :t, :pac, :origen)
                    """).param("f", notaId).param("consulta", f.consultaId()).param("i", c.r().itemId())
                    .param("d", c.r().descripcion()).param("q", c.cantidad()).param("p", c.r().precio())
                    .param("pct", c.r().pct()).param("desc", c.descuento()).param("t", c.total()).param("pac", f.pacienteId())
                    .param("origen", c.r().id()).update();
        }

        // Reintegro opcional de stock a los lotes de origen (solo lo que la propia factura desconto)
        BigDecimal reintegrado = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(r.reintegrarStock())) {
            for (Calc c : calcs) {
                if (c.r().controlaStock()) {
                    reintegrado = reintegrado.add(stock.reintegrarDeFacturaItem(c.r().id(), c.cantidad(),
                            "Nota credito " + num.numero() + ": " + r.motivo().trim(), UsuarioActual.id().orElse(null)));
                }
            }
        }

        // Saldo de la factura: la nota primero reduce lo pendiente; el resto es dinero a devolver
        BigDecimal reduccion = f.saldo().min(totalNota);
        BigDecimal aDevolver = totalNota.subtract(reduccion);
        boolean totalmenteAcreditada = renglones.values().stream().allMatch(x -> {
            BigDecimal nueva = aAcreditar.getOrDefault(x.id(), BigDecimal.ZERO);
            return x.cantidadAcreditada().add(nueva).compareTo(x.cantidad()) == 0;
        });
        if (totalmenteAcreditada) {
            jdbc.sql("update factura set estado = 'ANULADA', anulada_en = now(), anulada_por = :u, motivo_anulacion = :m where id = :id")
                    .param("u", UsuarioActual.id().orElse(null)).param("m", r.motivo().trim()).param("id", facturaId).update();
        }
        pagos.recalcular(facturaId);

        boolean enCaja = false;
        if (aDevolver.signum() > 0 && r.devolucion() != null && "EFECTIVO".equals(r.devolucion().metodo())) {
            caja.egresoPorDevolucion(aDevolver, "Devolucion en efectivo por nota credito " + num.numero() + " de " + f.numero());
            enCaja = true;
        }
        facturas.solicitarDian(notaId);
        return new NotaCreditoResponse(facturas.obtener(notaId), facturas.obtener(facturaId), aDevolver, enCaja, reintegrado);
    }
}
