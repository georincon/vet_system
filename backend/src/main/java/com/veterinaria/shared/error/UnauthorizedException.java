package com.veterinaria.shared.error;

/** Credenciales o sesion invalidas (HTTP 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
