package com.veterinaria.clinica.api.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Receta de la consulta. En el alta {@code items} debe traer al menos una linea. En PUT los campos ausentes
 * (null) no se modifican; {@code items} null conserva las lineas y una lista las reemplaza.
 * {@code estado} (solo PUT): ACTIVA, CUMPLIDA o CANCELADA.
 */
public record RecetaRequest(
        @Size(max = 2000, message = "Las indicaciones no pueden exceder 2000 caracteres") String indicacionesGenerales,
        LocalDate validaHasta,
        Boolean esControlada,
        @Pattern(regexp = "ACTIVA|CUMPLIDA|CANCELADA", message = "Estado de receta no valido") String estado,
        List<@Valid RecetaItemDto> items) {
}
