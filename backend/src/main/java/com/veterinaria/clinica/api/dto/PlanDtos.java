package com.veterinaria.clinica.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Plan de tratamiento de una consulta (fases/items) y controles de seguimiento. */
public final class PlanDtos {

    private PlanDtos() {
    }

    /** Fase o paso del plan. {@code tipo}: MEDICACION, PROCEDIMIENTO, DIETA, CUIDADO, EXAMEN, CONTROL u OTRO (defecto). */
    public record PlanItemRequest(
            Integer orden,
            @Size(max = 100, message = "La fase no puede exceder 100 caracteres") String fase,
            @Pattern(regexp = "MEDICACION|PROCEDIMIENTO|DIETA|CUIDADO|EXAMEN|CONTROL|OTRO", message = "Tipo de item de plan no valido") String tipo,
            @NotBlank(message = "La descripcion del item es obligatoria")
            @Size(max = 2000, message = "La descripcion no puede exceder 2000 caracteres") String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin) {
    }

    /**
     * Alta/edicion del plan. En PUT los campos null no cambian ("" limpia un texto); {@code items} null conserva los items y
     * una lista los reemplaza. {@code estado}: ACTIVO (defecto), COMPLETADO o CANCELADO.
     */
    public record PlanRequest(
            @Size(max = 2000, message = "El objetivo no puede exceder 2000 caracteres") String objetivo,
            @Size(max = 4000, message = "Las recomendaciones no pueden exceder 4000 caracteres") String recomendaciones,
            @Size(max = 2000, message = "Las advertencias no pueden exceder 2000 caracteres") String advertencias,
            @Pattern(regexp = "ACTIVO|COMPLETADO|CANCELADO", message = "Estado de plan no valido") String estado,
            List<@Valid PlanItemRequest> items) {
    }

    public record PlanItemResponse(Long id, int orden, String fase, String tipo, String descripcion, LocalDate fechaInicio,
            LocalDate fechaFin) {
    }

    /**
     * Control de seguimiento. {@code generarRecordatorio} (solo alta): pide un recordatorio por correo al tutor un dia
     * antes de la fecha sugerida (si tiene consentimiento). {@code estado} (solo PUT): PENDIENTE, AGENDADO, REALIZADO o CANCELADO.
     */
    public record ControlRequest(
            LocalDate fechaSugerida,
            @Size(max = 300, message = "El motivo no puede exceder 300 caracteres") String motivo,
            @Pattern(regexp = "PENDIENTE|AGENDADO|REALIZADO|CANCELADO", message = "Estado de control no valido") String estado,
            Long citaId,
            Boolean generarRecordatorio) {
    }

    /** Datos para precargar el formulario de cita en la agenda ({@code POST /api/citas}); no crea la cita. */
    public record CitaSugerida(Long pacienteId, Long veterinarioId, LocalDate fecha, String motivo) {
    }

    /** Resultado de la oferta de recordatorio: {@code solicitado} si se pidio; {@code generado} si se creo; {@code detalle} el motivo si se omitio. */
    public record RecordatorioControl(boolean solicitado, boolean generado, Long recordatorioId, String detalle) {
    }

    public record ControlResponse(Long id, Long consultaId, Long planId, Long pacienteId, LocalDate fechaSugerida,
            String motivo, String estado, Long citaId, Instant creadoEn, CitaSugerida citaSugerida,
            RecordatorioControl recordatorio) {
    }

    public record PlanResponse(Long id, Long consultaId, Long pacienteId, boolean consultaCerrada, String objetivo,
            String recomendaciones, String advertencias, String estado, List<PlanItemResponse> items,
            List<ControlResponse> controles, Instant creadoEn, Instant actualizadoEn) {
    }
}
