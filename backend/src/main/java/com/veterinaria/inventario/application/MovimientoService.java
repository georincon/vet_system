package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.StockApi.LoteConsumido;
import com.veterinaria.inventario.StockApi.SalidaFefo;
import com.veterinaria.inventario.api.dto.StockDtos.AjusteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.AjusteResponse;
import com.veterinaria.inventario.api.dto.StockDtos.LoteUsado;
import com.veterinaria.inventario.api.dto.StockDtos.MovimientoResponse;
import com.veterinaria.inventario.api.dto.StockDtos.Persona;
import com.veterinaria.inventario.api.dto.StockDtos.SalidaRequest;
import com.veterinaria.inventario.api.dto.StockDtos.SalidaResponse;
import com.veterinaria.inventario.api.dto.StockDtos.UsoRequest;
import com.veterinaria.inventario.domain.MotivoSalida;
import com.veterinaria.inventario.domain.TipoMovimiento;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.inventario.internal.StockRepository;
import com.veterinaria.inventario.internal.StockRepository.LoteRow;
import com.veterinaria.inventario.internal.StockRepository.Mov;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/** Kardex (consulta), salidas manuales con motivo, uso clinico y ajustes por conteo fisico. */
@Service
public class MovimientoService {

    private final JdbcClient jdbc;
    private final StockRepository stock;
    private final StockService stockService;

    public MovimientoService(JdbcClient jdbc, StockRepository stock, StockService stockService) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimientoResponse> kardex(Long itemId, Long loteId, String tipo, LocalDate desde, LocalDate hasta,
            Long pacienteId, Long consultaId, String referencia, Pageable pageable) {
        Filtro f = new Filtro().si(itemId, "l.item_id = :item", "item").si(loteId, "m.lote_id = :lote", "lote")
                .si(tipo, "m.tipo = :tipo", "tipo").si(pacienteId, "m.paciente_id = :pac", "pac")
                .si(consultaId, "m.consulta_id = :cons", "cons");
        if (desde != null) {
            f.y("m.ocurrido_en >= :desde", "desde", java.sql.Timestamp.from(ZonaClinica.instante(desde, java.time.LocalTime.MIN)));
        }
        if (hasta != null) {
            f.y("m.ocurrido_en < :hasta", "hasta", java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), java.time.LocalTime.MIN)));
        }
        if (referencia != null && !referencia.isBlank()) {
            f.y("m.referencia ilike :ref", "ref", referencia.trim() + "%");
        }
        String from = """
                 from movimiento_stock m join lote l on l.id = m.lote_id join item i on i.id = l.item_id
                 join bodega b on b.id = l.bodega_id left join usuario u on u.id = m.usuario_id
                """ + f.sql();
        long total = jdbc.sql("select count(*)" + from).params(f.params()).query(Long.class).single();
        boolean costos = Visibilidad.veCostos();
        List<MovimientoResponse> items = jdbc.sql("""
                select m.*, l.numero_lote, l.item_id, i.codigo as item_codigo, i.nombre as item_nombre, b.nombre as bodega,
                       u.nombre as usuario
                """ + from + " order by m.ocurrido_en desc, m.id desc limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> {
                    TipoMovimiento t = TipoMovimiento.valueOf(rs.getString("tipo"));
                    BigDecimal cantidad = rs.getBigDecimal("cantidad");
                    BigDecimal delta = t == TipoMovimiento.AJUSTE ? cantidad : (t.suma() ? cantidad : cantidad.negate());
                    Long usuarioId = (Long) rs.getObject("usuario_id");
                    return new MovimientoResponse(rs.getLong("id"), rs.getTimestamp("ocurrido_en").toInstant(), t.name(),
                            delta, cantidad.abs(), rs.getLong("lote_id"), rs.getString("numero_lote"), rs.getLong("item_id"),
                            rs.getString("item_codigo"), rs.getString("item_nombre"), rs.getString("bodega"),
                            rs.getString("motivo"), rs.getString("referencia"),
                            usuarioId == null ? null : new Persona(usuarioId, rs.getString("usuario")),
                            (Long) rs.getObject("paciente_id"), (Long) rs.getObject("consulta_id"),
                            (Long) rs.getObject("factura_item_id"), (Long) rs.getObject("recepcion_item_id"),
                            costos ? rs.getBigDecimal("costo_unitario") : null, rs.getBigDecimal("saldo_resultante"));
                }).list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    /** Uso clinico (ADMIN, VETERINARIO, AUXILIAR): descuenta FEFO o de un lote concreto. */
    @Transactional
    public SalidaResponse uso(UsoRequest r) {
        return salida(r.itemId(), r.loteId(), r.bodegaId(), r.cantidad(), MotivoSalida.USO_CLINICO, r.observaciones(),
                r.pacienteId(), r.consultaId());
    }

    /** Salida manual con motivo (ADMIN). */
    @Transactional
    public SalidaResponse salida(SalidaRequest r) {
        MotivoSalida motivo = r.motivo() == null ? null : MotivoSalida.valueOf(r.motivo());
        if (motivo == null) {
            throw new BusinessException("El motivo de la salida es obligatorio");
        }
        return salida(r.itemId(), r.loteId(), r.bodegaId(), r.cantidad(), motivo, r.observaciones(), r.pacienteId(),
                r.consultaId());
    }

    private SalidaResponse salida(Long itemId, Long loteId, Long bodegaId, BigDecimal cantidad, MotivoSalida motivo,
            String observaciones, Long pacienteId, Long consultaId) {
        if (itemId == null && loteId == null) {
            throw new BusinessException("Indique itemId (salida FEFO) o loteId");
        }
        if (pacienteId != null && jdbc.sql("select count(*) from paciente where id = :id").param("id", pacienteId)
                .query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Paciente", pacienteId);
        }
        if (consultaId != null && jdbc.sql("select count(*) from consulta where id = :id").param("id", consultaId)
                .query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Consulta", consultaId);
        }
        Long usuario = UsuarioActual.id().orElse(null);
        String texto = motivo.name() + (Textos.limpiar(observaciones) == null ? "" : ": " + observaciones.trim());
        String referencia = "SALIDA_" + motivo.name();
        Long item = itemId;
        List<LoteConsumido> usados;
        if (loteId != null) {
            LoteRow lote = stock.bloquear(loteId).orElseThrow(() -> new ResourceNotFoundException("Lote", loteId));
            if (item != null && !item.equals(lote.itemId())) {
                throw new BusinessException("El lote no corresponde al item indicado");
            }
            item = lote.itemId();
            boolean utilizable = "DISPONIBLE".equals(lote.estado())
                    && (lote.vencimiento() == null || !lote.vencimiento().isBefore(ZonaClinica.hoy()));
            if (!utilizable && !motivo.esDesecho()) {
                throw new BusinessException("El lote " + lote.numeroLote() + " esta vencido o no disponible ("
                        + lote.estado() + "): solo admite salida por DESECHO, MERMA o DANIO");
            }
            stock.mover(new Mov(loteId, TipoMovimiento.SALIDA, cantidad.negate(), texto, referencia, usuario, pacienteId,
                    consultaId, null, null, null, usuario, motivo.esDesecho()));
            usados = List.of(new LoteConsumido(lote.id(), lote.numeroLote(), lote.vencimiento(), cantidad));
        } else {
            usados = stockService.descontar(new SalidaFefo(itemId, cantidad, bodegaId, texto, referencia, usuario,
                    pacienteId, consultaId, usuario, null), motivo.esDesecho());
        }
        Map<String, Object> it = jdbc.sql("select nombre from item where id = :id").param("id", item).query().singleRow();
        return new SalidaResponse(item, (String) it.get("nombre"), motivo.name(), cantidad,
                usados.stream().map(u -> new LoteUsado(u.loteId(), u.numeroLote(), u.vencimiento(), u.cantidad())).toList(),
                stock.stockItem(item));
    }

    /** Ajuste por conteo fisico (cantidadReal) o diferencia (delta), siempre con motivo. Nunca deja el lote en negativo. */
    @Transactional
    public AjusteResponse ajustar(AjusteRequest r) {
        if ((r.cantidadReal() == null) == (r.delta() == null)) {
            throw new BusinessException("Indique cantidadReal (conteo fisico) o delta, no ambos");
        }
        LoteRow lote = stock.bloquear(r.loteId()).orElseThrow(() -> new ResourceNotFoundException("Lote", r.loteId()));
        BigDecimal delta = r.delta() != null ? r.delta() : r.cantidadReal().subtract(lote.cantidad());
        if (delta.signum() == 0) {
            throw new BusinessException("El ajuste no cambia la existencia actual (" + lote.cantidad() + ")");
        }
        Long usuario = UsuarioActual.id().orElse(null);
        BigDecimal saldo = stock.mover(new Mov(r.loteId(), TipoMovimiento.AJUSTE, delta, "AJUSTE: " + r.motivo().trim(),
                "AJUSTE_MANUAL", usuario, null, null, null, null, null, usuario, false));
        return new AjusteResponse(lote.id(), lote.numeroLote(), lote.cantidad(), saldo, delta, r.motivo().trim());
    }
}
