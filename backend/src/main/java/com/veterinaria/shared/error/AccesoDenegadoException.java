package com.veterinaria.shared.error;

/** El usuario autenticado no puede realizar la operacion sobre este recurso (HTTP 403). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
