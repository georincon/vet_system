package com.veterinaria.inmunizacion.api.dto;

import java.time.LocalDate;

/**
 * Proxima dosis pendiente (ultima aplicacion vigente por paciente y producto) con datos de contacto del tutor
 * principal. {@code estado}: VENCIDA (proxima < hoy) o POR_VENCER; {@code diasRestantes} es negativo si vencio.
 */
public record PendienteResponse(
        String tipo,
        Long aplicacionId,
        Long pacienteId,
        String paciente,
        String especie,
        Long productoId,
        String producto,
        LocalDate ultimaAplicacion,
        LocalDate proximaFecha,
        long diasRestantes,
        String estado,
        Tutor tutor) {

    public record Tutor(Long id, String nombre, String telefono, String email, boolean aceptaEmail,
            boolean aceptaWhatsapp) {
    }
}
