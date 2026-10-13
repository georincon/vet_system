package com.veterinaria.clinica.api.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo opcional al iniciar una consulta desde una cita. {@code plantillaId} precarga subjetivo/objetivo/
 * evaluacion/plan con la plantilla SOAP; {@code tipoConsulta}: GENERAL (defecto), CONTROL, URGENCIA, VACUNACION,
 * PREQUIRURGICA, POSTQUIRURGICA, TELEMEDICINA, DOMICILIO u HOSPITALIZACION.
 */
public record IniciarConsultaRequest(
        Long plantillaId,
        @Pattern(regexp = "GENERAL|CONTROL|URGENCIA|VACUNACION|PREQUIRURGICA|POSTQUIRURGICA|TELEMEDICINA|DOMICILIO|HOSPITALIZACION",
                message = "Tipo de consulta no valido") String tipoConsulta) {
}
