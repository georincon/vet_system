package com.veterinaria.inmunizacion.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.veterinaria.inventario.StockApi;

/**
 * Adaptador hacia el modulo de inventario ({@link StockApi}) para descontar/devolver la dosis aplicada. Desde la
 * Fase 4 el kardex, el bloqueo de fila, el estado del lote y el libro de controlados viven en {@code inventario};
 * aqui solo se conserva la forma que usa {@code InmunizacionService}.
 */
@Component
public class LoteStockRepository {

    public record Lote(Long id, Long itemId, String numeroLote, LocalDate vencimiento, BigDecimal cantidad,
            String estado) {
    }

    private final StockApi stock;

    public LoteStockRepository(StockApi stock) {
        this.stock = stock;
    }

    /** Lote bloqueado ({@code FOR UPDATE}) hasta el fin de la transaccion. */
    public Optional<Lote> bloquear(Long id) {
        return stock.bloquearLote(id).map(l -> new Lote(l.id(), l.itemId(), l.numeroLote(), l.vencimiento(),
                l.cantidad(), l.estado()));
    }

    public void salida(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        stock.salidaLote(loteId, cantidad, motivo, referencia, usuarioId, pacienteId, consultaId);
    }

    public void devolucion(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        stock.devolucionLote(loteId, cantidad, motivo, referencia, usuarioId, pacienteId, consultaId);
    }
}
