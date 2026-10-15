package com.veterinaria.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * API publica de existencias para otros modulos (inmunizacion, facturacion, dashboard). Todas las operaciones
 * de escritura participan de la transaccion del llamador; ninguna deja el stock en negativo.
 */
public interface StockApi {

    /** Lote bloqueado ({@code FOR UPDATE}) hasta el fin de la transaccion. */
    record LoteInfo(Long id, Long itemId, String numeroLote, LocalDate vencimiento, BigDecimal cantidad, String estado) {
    }

    /** Peticion de salida por FEFO (primero vence, primero sale; omite lotes vencidos/no disponibles). */
    record SalidaFefo(Long itemId, BigDecimal cantidad, Long bodegaId, String motivo, String referencia,
            Long usuarioId, Long pacienteId, Long consultaId, Long veterinarioId, Long facturaItemId) {
    }

    record LoteConsumido(Long loteId, String numeroLote, LocalDate vencimiento, BigDecimal cantidad) {
    }

    Optional<LoteInfo> bloquearLote(Long loteId);

    /** Salida puntual de un lote ya bloqueado (p. ej. una dosis aplicada). 409 si no hay stock. */
    void salidaLote(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId);

    /** Devuelve unidades a un lote (anulacion). */
    void devolucionLote(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId);

    /**
     * Descuenta {@code cantidad} del item repartiendo entre lotes por FEFO.
     *
     * @throws com.veterinaria.shared.error.ConflictException (409) si el stock vigente no alcanza
     */
    List<LoteConsumido> descontarFefo(SalidaFefo salida);

    /**
     * Unidades netas del item ya descontadas por actos clinicos de la consulta (inmunizacion/desparasitacion con
     * lote: salidas menos devoluciones). Facturacion las resta para no descontar dos veces.
     */
    BigDecimal descontadoPorActosClinicos(Long consultaId, Long itemId);

    /** Devuelve a los lotes de origen hasta {@code cantidad} de lo descontado por un renglon de factura. */
    BigDecimal reintegrarDeFacturaItem(Long facturaItemId, BigDecimal cantidad, String motivo, Long usuarioId);

    /** Items activos con control de stock cuyo stock vigente es menor al minimo (> 0). */
    long contarItemsBajoMinimo();

    /** Lotes con stock que vencen entre hoy y hoy + {@code dias} (inclusive). */
    long contarLotesPorVencer(int dias);
}
