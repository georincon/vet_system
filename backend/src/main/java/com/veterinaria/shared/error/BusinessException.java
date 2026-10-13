package com.veterinaria.shared.error;

/** Violacion de una regla de negocio (HTTP 422). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
