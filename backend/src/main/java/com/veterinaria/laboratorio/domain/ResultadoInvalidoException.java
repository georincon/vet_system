package com.veterinaria.laboratorio.domain;

import com.veterinaria.shared.error.BusinessException;

/** Valor de resultado rechazado; {@code codigo} distingue valor invalido de unidad que no coincide (importacion). */
public class ResultadoInvalidoException extends BusinessException {

    private final String codigo;

    public ResultadoInvalidoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
