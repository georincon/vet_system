package com.veterinaria.inventario.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.inventario.domain.TipoMovimiento;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;

/**
 * Unico punto que escribe existencias: actualiza el lote (nunca por debajo de 0), agrega el renglon del kardex
 * ({@code movimiento_stock}, append-only) y, si el item es controlado, el libro ({@code registro_controlado}).
 */
@Repository
public class StockRepository {

    /** Lote con los datos del item necesarios para validar y registrar. */
    public record LoteRow(Long id, Long itemId, Long bodegaId, String numeroLote, LocalDate vencimiento,
            BigDecimal cantidad, String estado, boolean controlado, String itemCodigo, String itemNombre) {
    }

    /** Datos de un movimiento. {@code delta} lleva el signo (+ entra, - sale). */
    public record Mov(Long loteId, TipoMovimiento tipo, BigDecimal delta, String motivo, String referencia,
            Long usuarioId, Long pacienteId, Long consultaId, Long facturaItemId, Long recepcionItemId,
            BigDecimal costo, Long veterinarioId, boolean desecho) {
    }

    private static final String SELECT_LOTE = """
            select l.id, l.item_id, l.bodega_id, l.numero_lote, l.fecha_vencimiento, l.cantidad_actual, l.estado,
                   i.es_controlado, i.codigo as item_codigo, i.nombre as item_nombre
            from lote l join item i on i.id = l.item_id
            """;

    private final JdbcClient jdbc;

    public StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static LoteRow mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date v = rs.getDate("fecha_vencimiento");
        return new LoteRow(rs.getLong("id"), rs.getLong("item_id"), rs.getLong("bodega_id"), rs.getString("numero_lote"),
                v == null ? null : v.toLocalDate(), rs.getBigDecimal("cantidad_actual"), rs.getString("estado"),
                rs.getBoolean("es_controlado"), rs.getString("item_codigo"), rs.getString("item_nombre"));
    }

    public Optional<LoteRow> bloquear(Long id) {
        return jdbc.sql(SELECT_LOTE + " where l.id = :id for update of l").param("id", id)
                .query((rs, n) -> mapear(rs)).optional();
    }

    /** Lotes utilizables del item por FEFO (vencimiento ascendente, sin fecha al final), bloqueados. */
    public List<LoteRow> lotesFefo(Long itemId, Long bodegaId, LocalDate hoy) {
        return jdbc.sql(SELECT_LOTE + """
                 where l.item_id = :item and l.cantidad_actual > 0 and l.estado = 'DISPONIBLE'
                   and (l.fecha_vencimiento is null or l.fecha_vencimiento >= :hoy)
                   and (cast(:bodega as bigint) is null or l.bodega_id = :bodega)
                 order by l.fecha_vencimiento asc nulls last, l.id
                 for update of l
                """).param("item", itemId).param("hoy", hoy).param("bodega", bodegaId)
                .query((rs, n) -> mapear(rs)).list();
    }

    /** Existencias del item que NO son utilizables (vencidas o en estado no disponible), para el mensaje de 409. */
    public BigDecimal existenciasNoUtilizables(Long itemId, Long bodegaId, LocalDate hoy) {
        return jdbc.sql("""
                select coalesce(sum(cantidad_actual), 0) from lote
                where item_id = :item and cantidad_actual > 0
                  and (cast(:bodega as bigint) is null or bodega_id = :bodega)
                  and (estado <> 'DISPONIBLE' or (fecha_vencimiento is not null and fecha_vencimiento < :hoy))
                """).param("item", itemId).param("hoy", hoy).param("bodega", bodegaId)
                .query(BigDecimal.class).single();
    }

    public BigDecimal stockItem(Long itemId) {
        return jdbc.sql("select coalesce(sum(cantidad_actual), 0) from lote where item_id = :i").param("i", itemId)
                .query(BigDecimal.class).single();
    }

    /**
     * Aplica el movimiento. El lote debe estar bloqueado por el llamador (o se bloquea aqui por la actualizacion).
     *
     * @return saldo del lote tras el movimiento
     */
    public BigDecimal mover(Mov m) {
        if (m.delta().signum() == 0) {
            throw new BusinessException("La cantidad del movimiento debe ser distinta de cero");
        }
        LoteRow lote = bloquear(m.loteId()).orElseThrow(() -> new ConflictException("Lote inexistente: " + m.loteId()));
        if (lote.controlado() && m.tipo() == TipoMovimiento.SALIDA && !m.desecho() && m.pacienteId() == null) {
            throw new BusinessException("Medicamento controlado (" + lote.itemNombre()
                    + "): la salida exige el paciente (pacienteId) para el libro de controlados");
        }
        BigDecimal saldo = jdbc.sql("""
                update lote set cantidad_actual = cantidad_actual + :delta,
                       estado = case when estado in ('RETIRADO', 'VENCIDO') then estado
                                     when cantidad_actual + :delta <= 0 then 'AGOTADO'
                                     when estado = 'AGOTADO' then 'DISPONIBLE' else estado end
                where id = :id and cantidad_actual + :delta >= 0
                returning cantidad_actual
                """).param("delta", m.delta()).param("id", m.loteId()).query(BigDecimal.class).optional()
                .orElseThrow(() -> new ConflictException("Stock insuficiente en el lote " + lote.numeroLote()
                        + " de " + lote.itemNombre() + ": disponible " + lote.cantidad().stripTrailingZeros()
                        .toPlainString() + ", solicitado " + m.delta().abs().stripTrailingZeros().toPlainString()));
        BigDecimal cantidad = m.tipo() == TipoMovimiento.AJUSTE ? m.delta() : m.delta().abs();
        Long movimientoId = jdbc.sql("""
                insert into movimiento_stock (lote_id, tipo, cantidad, motivo, referencia, usuario_id, paciente_id,
                                              consulta_id, factura_item_id, recepcion_item_id, costo_unitario, saldo_resultante)
                values (:lote, :tipo, :cantidad, :motivo, :ref, :usuario, :paciente, :consulta, :fi, :ri, :costo, :saldo)
                returning id
                """).param("lote", m.loteId()).param("tipo", m.tipo().name()).param("cantidad", cantidad)
                .param("motivo", m.motivo()).param("ref", m.referencia()).param("usuario", m.usuarioId())
                .param("paciente", m.pacienteId()).param("consulta", m.consultaId()).param("fi", m.facturaItemId())
                .param("ri", m.recepcionItemId()).param("costo", m.costo()).param("saldo", saldo)
                .query(Long.class).single();
        if (lote.controlado()) {
            registrarLibro(lote, m, movimientoId);
        }
        return saldo;
    }

    private void registrarLibro(LoteRow lote, Mov m, Long movimientoId) {
        String tipo = switch (m.tipo()) {
            case ENTRADA, DEVOLUCION -> "ENTRADA";
            case AJUSTE -> "AJUSTE";
            case VENCIMIENTO -> "DESECHO";
            default -> m.desecho() ? "DESECHO" : "SALIDA";
        };
        jdbc.sql("""
                insert into registro_controlado (item_id, lote_id, movimiento_id, paciente_id, veterinario_id, tipo,
                                                 cantidad, saldo, observaciones)
                values (:item, :lote, :mov, :paciente, :vet, :tipo, :cantidad,
                        (select coalesce(sum(cantidad_actual), 0) from lote where item_id = :item), :obs)
                """).param("item", lote.itemId()).param("lote", lote.id()).param("mov", movimientoId)
                .param("paciente", m.pacienteId()).param("vet", m.veterinarioId() != null ? m.veterinarioId() : m.usuarioId())
                .param("tipo", tipo).param("cantidad", m.delta().abs())
                .param("obs", m.motivo() == null ? null : (m.motivo().length() > 500 ? m.motivo().substring(0, 500) : m.motivo()))
                .update();
    }
}
