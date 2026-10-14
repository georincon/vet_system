package com.veterinaria.inmunizacion.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.veterinaria.inmunizacion.TipoAplicacion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de una vacuna ({@code tipo=VACUNA}, requiere vacunaId) o antiparasitario
 * ({@code tipo=ANTIPARASITARIO}, requiere antiparasitarioId). Con {@code loteId} se valida y descuenta stock.
 */
public record InmunizacionRequest(
        @NotNull(message = "El tipo es obligatorio (VACUNA o ANTIPARASITARIO)") TipoAplicacion tipo,
        Long vacunaId,
        Long antiparasitarioId,
        LocalDate fechaAplicacion,
        Long loteId,
        @Size(max = 60) String lote,
        LocalDate fechaVencimientoLote,
        @Min(value = 1, message = "La dosis debe ser >= 1") Integer dosisNumero,
        Long veterinarioId,
        @Size(max = 40) String sitioAplicacion,
        @Size(max = 20) String viaAdministracion,
        @DecimalMin(value = "0", inclusive = false, message = "El peso debe ser positivo") BigDecimal pesoKg,
        @Size(max = 120) String laboratorio,
        @Size(max = 80) String dosis,
        String observaciones,
        String reaccionAdversa,
        Boolean externa,
        Long consultaId,
        LocalDate proximaFecha) {
}
