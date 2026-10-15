package com.veterinaria.laboratorio.domain;

/** Una linea de resultado ya interpretada desde HL7, CSV o JSON (antes de buscar el parametro y la orden). */
public record LineaEntrada(
        int linea,
        String ordenCodigo,
        String pacienteCodigo,
        String examenCodigo,
        String parametroCodigo,
        String loinc,
        String nombre,
        String valor,
        String unidad,
        String observacion) {
}
