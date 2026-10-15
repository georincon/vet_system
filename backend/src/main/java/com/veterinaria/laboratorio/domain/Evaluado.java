package com.veterinaria.laboratorio.domain;

import java.math.BigDecimal;

/** Valor de un resultado ya normalizado, con el snapshot del rango aplicado y su bandera. */
public record Evaluado(
        BigDecimal valorNumerico,
        String valorTexto,
        String unidad,
        Long rangoId,
        BigDecimal refMin,
        BigDecimal refMax,
        BigDecimal refCriticoMin,
        BigDecimal refCriticoMax,
        String bandera,
        boolean critico) {
}
