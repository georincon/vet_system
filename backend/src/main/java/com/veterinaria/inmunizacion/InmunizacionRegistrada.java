package com.veterinaria.inmunizacion;

import java.util.List;

/**
 * Evento (API del modulo): se registro una vacuna o antiparasitario. {@code reemplazadas} son los ids previos
 * del mismo paciente y producto que dejan de ser "la ultima aplicacion"; comunicaciones cancela sus recordatorios.
 */
public record InmunizacionRegistrada(String tipo, Long id, Long pacienteId, List<Long> reemplazadas) {
}
