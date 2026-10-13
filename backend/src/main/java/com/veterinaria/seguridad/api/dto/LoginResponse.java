package com.veterinaria.seguridad.api.dto;

/**
 * Respuesta de login. {@code expiraEn} es el instante de expiracion en milisegundos
 * desde epoch (el frontend lo compara con {@code Date.now()}).
 */
public record LoginResponse(String token, long expiraEn, UsuarioResponse usuario) {
}
