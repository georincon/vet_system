package com.veterinaria.inmunizacion.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso minimo al inventario (tablas lote y movimiento_stock de V1/V18) para descontar/devolver una dosis.
 * El modulo de inventario completo llega en la Fase 4; aqui solo se toca el lote usado, con bloqueo de fila.
 */
@Repository
public class LoteStockRepository {

    public record Lote(Long id, Long itemId, String numeroLote, LocalDate vencimiento, BigDecimal cantidad,
            String estado) {
    }

    private final JdbcClient jdbc;

    public LoteStockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lote bloqueado ({@code FOR UPDATE}) hasta el fin de la transaccion. */
    public Optional<Lote> bloquear(Long id) {
        return jdbc.sql("""
                select id, item_id, numero_lote, fecha_vencimiento, cantidad_actual, estado
                from lote where id = :id for update
                """).param("id", id)
                .query((rs, n) -> new Lote(rs.getLong("id"), rs.getLong("item_id"), rs.getString("numero_lote"),
                        rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate(),
                        rs.getBigDecimal("cantidad_actual"), rs.getString("estado")))
                .optional();
    }

    public void salida(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        mover(loteId, cantidad.negate(), "SALIDA", motivo, referencia, usuarioId, pacienteId, consultaId);
    }

    public void devolucion(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        mover(loteId, cantidad, "DEVOLUCION", motivo, referencia, usuarioId, pacienteId, consultaId);
    }

    private void mover(Long loteId, BigDecimal delta, String tipo, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        BigDecimal saldo = jdbc.sql("""
                update lote set cantidad_actual = cantidad_actual + :delta,
                       estado = case when cantidad_actual + :delta <= 0 then 'AGOTADO'
                                     when estado = 'AGOTADO' then 'DISPONIBLE' else estado end
                where id = :id returning cantidad_actual
                """).param("delta", delta).param("id", loteId).query(BigDecimal.class).single();
        jdbc.sql("""
                insert into movimiento_stock (lote_id, tipo, cantidad, motivo, referencia, usuario_id, paciente_id,
                                              consulta_id, saldo_resultante)
                values (:lote, :tipo, :cantidad, :motivo, :ref, :usuario, :paciente, :consulta, :saldo)
                """).param("lote", loteId).param("tipo", tipo).param("cantidad", delta.abs()).param("motivo", motivo)
                .param("ref", referencia).param("usuario", usuarioId).param("paciente", pacienteId)
                .param("consulta", consultaId).param("saldo", saldo).update();
    }
}
