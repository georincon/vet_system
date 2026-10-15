package com.veterinaria.facturacion.application;

import java.math.BigDecimal;

import com.veterinaria.facturacion.domain.Importes;

/** Renglon ya validado y valorizado (precio/IVA definitivos) listo para persistir en presupuesto o factura. */
record LineaResuelta(Long itemId, String itemCodigo, String descripcion, BigDecimal cantidad, BigDecimal precio,
        BigDecimal impuestoPct, BigDecimal descuento, Importes.Linea calc, Long pacienteId, Long veterinarioId,
        Long consultaId, boolean controlaStock, boolean descontarStock, String origenTabla, Long origenId) {
}
