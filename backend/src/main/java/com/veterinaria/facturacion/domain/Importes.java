package com.veterinaria.facturacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.veterinaria.shared.error.BusinessException;

/**
 * Aritmetica de renglones: {@code bruto = cantidad x precio (sin IVA)}, {@code base = bruto - descuento (monto)},
 * {@code impuesto = base x IVA%}, {@code total = base + impuesto}; todo redondeado a 2 decimales (HALF_UP).
 */
public final class Importes {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private Importes() {
    }

    public record Linea(BigDecimal bruto, BigDecimal descuento, BigDecimal base, BigDecimal impuesto, BigDecimal total) {
    }

    public static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static Linea calcular(BigDecimal cantidad, BigDecimal precio, BigDecimal descuento, BigDecimal impuestoPct) {
        BigDecimal bruto = dinero(cantidad.multiply(precio));
        BigDecimal desc = dinero(descuento == null ? BigDecimal.ZERO : descuento);
        if (desc.compareTo(bruto) > 0) {
            throw new BusinessException("El descuento (" + desc + ") no puede exceder el valor del renglon (" + bruto + ")");
        }
        BigDecimal base = bruto.subtract(desc);
        BigDecimal pct = impuestoPct == null ? BigDecimal.ZERO : impuestoPct;
        BigDecimal impuesto = dinero(base.multiply(pct).divide(CIEN, 6, RoundingMode.HALF_UP));
        return new Linea(bruto, desc, base, impuesto, base.add(impuesto));
    }

    public static Linea suma(Iterable<Linea> lineas) {
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal desc = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal imp = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Linea l : lineas) {
            bruto = bruto.add(l.bruto());
            desc = desc.add(l.descuento());
            base = base.add(l.base());
            imp = imp.add(l.impuesto());
            total = total.add(l.total());
        }
        return new Linea(bruto, desc, base, imp, total);
    }
}
