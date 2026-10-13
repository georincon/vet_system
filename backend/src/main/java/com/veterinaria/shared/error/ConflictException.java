package com.veterinaria.shared.error;

/** Conflicto con el estado actual, p. ej. un duplicado (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
