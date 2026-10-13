package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.shared.web.Alta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta/edicion de consulta. En el alta son obligatorios paciente, veterinario y fecha; en la
 * edicion los campos ausentes (null) no se modifican y un texto vacio ("") limpia el campo.
 * {@code medicamentos}/{@code vacunas}: null no cambia la lista, una lista (incluso vacia) la reemplaza.
 * Una consulta CERRADA no se puede editar (409): use addendums.
 */
public record ConsultaRequest(
        Long citaId,
        @NotNull(groups = Alta.class, message = "El paciente es obligatorio") Long pacienteId,
        @NotNull(groups = Alta.class, message = "El veterinario es obligatorio") Long veterinarioId,
        @NotNull(groups = Alta.class, message = "La fecha es obligatoria") LocalDate fecha,
        @Size(max = 255, message = "El motivo no puede exceder 255 caracteres") String motivo,
        @Size(max = 2000, message = "La sintomatologia no puede exceder 2000 caracteres") String sintomatologia,
        @Size(max = 2000, message = "El diagnostico no puede exceder 2000 caracteres") String diagnostico,
        @Size(max = 2000, message = "El tratamiento no puede exceder 2000 caracteres") String tratamiento,
        @Size(max = 2000, message = "Las observaciones no pueden exceder 2000 caracteres") String observaciones,
        @DecimalMin(value = "0.0", inclusive = false, message = "El peso debe ser positivo")
        @DecimalMax(value = "999.99", message = "El peso excede el maximo permitido") BigDecimal pesoKg,
        @DecimalMin(value = "0.0", message = "La temperatura no puede ser negativa")
        @DecimalMax(value = "99.99", message = "La temperatura excede el maximo permitido") BigDecimal temperaturaC,
        @Min(value = 0, message = "La frecuencia cardiaca no puede ser negativa")
        @Max(value = 1000, message = "La frecuencia cardiaca excede el maximo permitido") Integer frecuenciaCardiaca,
        @Min(value = 0, message = "La frecuencia respiratoria no puede ser negativa")
        @Max(value = 1000, message = "La frecuencia respiratoria excede el maximo permitido") Integer frecuenciaRespiratoria,
        @Pattern(regexp = "GENERAL|CONTROL|URGENCIA|VACUNACION|PREQUIRURGICA|POSTQUIRURGICA|TELEMEDICINA|DOMICILIO|HOSPITALIZACION",
                message = "Tipo de consulta no valido") String tipoConsulta,
        @Pattern(regexp = "ROJO|NARANJA|AMARILLO|VERDE|AZUL", message = "Triaje no valido") String triaje,
        LocalDate proximaRevision,
        Long plantillaId,
        @Size(max = 10000, message = "La anamnesis no puede exceder 10000 caracteres") String anamnesis,
        @Size(max = 10000, message = "El subjetivo no puede exceder 10000 caracteres") String subjetivo,
        @Size(max = 10000, message = "El objetivo no puede exceder 10000 caracteres") String objetivo,
        @Size(max = 10000, message = "La evaluacion no puede exceder 10000 caracteres") String evaluacion,
        @Size(max = 10000, message = "El plan no puede exceder 10000 caracteres") String plan,
        @Size(max = 4000, message = "El pronostico no puede exceder 4000 caracteres") String pronostico,
        List<@Valid ConsultaMedicamentoDto> medicamentos,
        List<@Valid ConsultaVacunaDto> vacunas) {
}
