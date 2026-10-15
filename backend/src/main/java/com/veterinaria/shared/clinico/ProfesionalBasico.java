package com.veterinaria.shared.clinico;

/** Profesional que firma un documento: nombre y tarjeta/registro profesional (si esta registrado). */
public record ProfesionalBasico(Long usuarioId, String nombre, String numeroProfesional, String especialidad) {

    /** "Nombre - T.P. 12345" o solo el nombre. */
    public String firmaTexto() {
        return numeroProfesional == null || numeroProfesional.isBlank() ? nombre : nombre + " - T.P. " + numeroProfesional;
    }
}
