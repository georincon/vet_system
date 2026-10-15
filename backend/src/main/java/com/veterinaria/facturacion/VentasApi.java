package com.veterinaria.facturacion;

import java.math.BigDecimal;

/** API publica para tableros: ventas netas (facturas y POS menos notas credito) de hoy y del mes en curso. */
public interface VentasApi {

    record ResumenVentas(BigDecimal hoy, BigDecimal mes) {
    }

    ResumenVentas resumen();
}
