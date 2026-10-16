package com.veterinaria.seguridad.api.dto;

/** Perfil profesional del veterinario (tarjeta/registro profesional y especialidad). */
public record PerfilProfesional(Long usuarioId, String nombre, String numeroProfesional, String especialidad) {
}
