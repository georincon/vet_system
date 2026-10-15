package com.veterinaria.comunicaciones.api.dto;

import java.time.LocalDate;

/** Resumen de una ejecucion del job (generacion + envio de la cola). */
public record EjecucionResponse(
        LocalDate fecha,
        int generados,
        int yaExistian,
        int omitidosSinConsentimiento,
        int omitidosSinContacto,
        int reglasSinCanal,
        int enviados,
        int simulados,
        int fallidos,
        int reintentosPendientes,
        int cancelados,
        int omitidosOptout) {
}
