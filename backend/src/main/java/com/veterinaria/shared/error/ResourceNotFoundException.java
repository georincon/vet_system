package com.veterinaria.shared.error;

/** Recurso inexistente (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entidad, Object id) {
        super("%s con id %s no encontrado".formatted(entidad, id));
    }
}
