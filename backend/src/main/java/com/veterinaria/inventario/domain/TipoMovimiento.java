package com.veterinaria.inventario.domain;

import java.util.Set;

/** Tipos del kardex ({@code movimiento_stock.tipo}); define el signo del movimiento. */
public enum TipoMovimiento {
    ENTRADA, SALIDA, AJUSTE, TRASLADO, VENCIMIENTO, DEVOLUCION;

    private static final Set<TipoMovimiento> SUMAN = Set.of(ENTRADA, DEVOLUCION);

    /** {@code AJUSTE} se guarda con cantidad con signo; los demas guardan magnitud y el signo lo da el tipo. */
    public boolean suma() {
        return SUMAN.contains(this);
    }
}
