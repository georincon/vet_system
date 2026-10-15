package com.veterinaria.procedimientos.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.veterinaria.archivos.ArchivosApi.ArchivoInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de procedimientos y cirugias. Las fechas/horas de entrada son locales de la clinica (America/Bogota). */
public final class ProcDtos {

    private ProcDtos() {
    }

    public record Persona(Long id, String nombre) {
    }

    // ------------------------------------------------------------------ solicitud

    public record InsumoRequest(
            Long itemId,
            @NotBlank(message = "La descripcion del insumo es obligatoria") @Size(max = 255) String descripcion,
            @NotNull(message = "La cantidad es obligatoria") @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a cero") BigDecimal cantidad,
            @Size(max = 60) String lote,
            @Size(max = 255) String notas) {
    }

    public record InsumoResponse(Long id, Long itemId, String descripcion, BigDecimal cantidad, String lote, String notas) {
    }

    /** Alta de un procedimiento ambulatorio o una cirugia. */
    public record ProcedimientoRequest(
            @NotNull(message = "El paciente es obligatorio") Long pacienteId,
            Long consultaId,
            @Pattern(regexp = "PROCEDIMIENTO|CIRUGIA", message = "tipo: PROCEDIMIENTO o CIRUGIA") String tipo,
            Long itemId,
            @Size(max = 255) String procedimiento,
            Long veterinarioId,
            Long ayudanteId,
            Long anestesiologoId,
            LocalDateTime programadaPara,
            LocalDateTime inicioEn,
            LocalDateTime finEn,
            @Min(1) @Max(5) Short asa,
            Boolean urgencia,
            @Min(0) Integer ayunoHoras,
            @DecimalMin("0.01") BigDecimal pesoKg,
            @Pattern(regexp = "NINGUNA|LOCAL|SEDACION|GENERAL|EPIDURAL|OTRA", message = "anestesiaTipo no valido") String anestesiaTipo,
            String premedicacion,
            String induccion,
            String mantenimiento,
            String analgesia,
            String diagnosticoPreoperatorio,
            String descripcionTecnica,
            String hallazgos,
            String complicaciones,
            @Size(max = 255) String materialSutura,
            Boolean muestrasHistopatologia,
            String diagnosticoPostoperatorio,
            String resultado,
            String indicacionesPostoperatorias,
            String controles,
            LocalDate proximoControl,
            @Pattern(regexp = "PROGRAMADA|EN_CURSO|REALIZADA|CANCELADA|APLAZADA", message = "estado no valido") String estado,
            @Valid List<InsumoRequest> insumos) {
    }

    /** Edicion: los campos ausentes no se modifican; {@code insumos} presente reemplaza la lista. */
    public record ProcedimientoUpdate(
            Long itemId,
            @Size(max = 255) String procedimiento,
            Long veterinarioId,
            Long ayudanteId,
            Long anestesiologoId,
            LocalDateTime programadaPara,
            LocalDateTime inicioEn,
            LocalDateTime finEn,
            @Min(1) @Max(5) Short asa,
            Boolean urgencia,
            @Min(0) Integer ayunoHoras,
            @DecimalMin("0.01") BigDecimal pesoKg,
            @Pattern(regexp = "NINGUNA|LOCAL|SEDACION|GENERAL|EPIDURAL|OTRA", message = "anestesiaTipo no valido") String anestesiaTipo,
            String premedicacion,
            String induccion,
            String mantenimiento,
            String analgesia,
            String diagnosticoPreoperatorio,
            String descripcionTecnica,
            String hallazgos,
            String complicaciones,
            @Size(max = 255) String materialSutura,
            Boolean muestrasHistopatologia,
            String diagnosticoPostoperatorio,
            String resultado,
            String indicacionesPostoperatorias,
            String controles,
            LocalDate proximoControl,
            @Valid List<InsumoRequest> insumos) {
    }

    public record EstadoRequest(
            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "PROGRAMADA|EN_CURSO|REALIZADA|CANCELADA|APLAZADA", message = "estado no valido") String estado,
            @Size(max = 255) String motivo) {
    }

    public record MonitoreoRequest(
            LocalDateTime registradoEn,
            @Min(0) @Max(400) Integer frecuenciaCardiaca,
            @Min(0) @Max(200) Integer frecuenciaRespiratoria,
            @DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal spo2,
            @DecimalMin("0") @jakarta.validation.constraints.DecimalMax("150") BigDecimal etco2,
            @Min(0) @Max(400) Integer presionSistolica,
            @DecimalMin("20") @jakarta.validation.constraints.DecimalMax("45") BigDecimal temperatura,
            @DecimalMin("0") @jakarta.validation.constraints.DecimalMax("9.9") BigDecimal agenteInhalatorioPct,
            @DecimalMin("0") BigDecimal fluidoMlH,
            @Size(max = 500) String notas) {
    }

    public record MonitoreoResponse(Long id, Instant registradoEn, Integer frecuenciaCardiaca, Integer frecuenciaRespiratoria,
            BigDecimal spo2, BigDecimal etco2, Integer presionSistolica, BigDecimal temperatura, BigDecimal agenteInhalatorioPct,
            BigDecimal fluidoMlH, String notas, Persona registradoPor) {
    }

    /** Consentimiento informado del tutor para el procedimiento. */
    public record ConsentimientoRequest(
            Long tutorId,
            String texto,
            @NotNull(message = "Indique si el tutor acepta (true) o rechaza (false)") Boolean aceptado,
            @NotBlank(message = "El nombre de quien firma es obligatorio") @Size(max = 255) String firmanteNombre,
            @Size(max = 30) String firmanteDocumento,
            @Pattern(regexp = "TABLET|PAPEL_ESCANEADO|OTP|ENLACE", message = "metodoFirma no valido") String metodoFirma,
            LocalDateTime fecha,
            Long archivoId) {
    }

    public record ConsentimientoResponse(Long id, String tipo, String estado, Persona tutor, String textoFirmado,
            String firmanteNombre, String firmanteDocumento, String metodoFirma, Instant firmadoEn, Long archivoId,
            Persona registradoPor) {
    }

    // ------------------------------------------------------------------ respuesta

    public record ProcedimientoResumen(Long id, String codigo, String tipo, String procedimiento, String estado, Long pacienteId,
            String pacienteNombre, Long consultaId, Persona cirujano, Instant programadaPara, Instant inicioEn, Instant finEn,
            boolean urgencia, String consentimientoEstado) {
    }

    public record ProcedimientoResponse(Long id, String codigo, String tipo, String estado, Long pacienteId, String pacienteNombre,
            String pacienteCodigo, Long consultaId, Long itemId, String procedimiento, Persona cirujano, Persona ayudante,
            Persona anestesiologo, Instant programadaPara, Instant inicioEn, Instant finEn, Short asa, boolean urgencia,
            Integer ayunoHoras, BigDecimal pesoKg, String anestesiaTipo, String premedicacion, String induccion,
            String mantenimiento, String analgesia, String diagnosticoPreoperatorio, String descripcionTecnica, String hallazgos,
            String complicaciones, String materialSutura, boolean muestrasHistopatologia, String diagnosticoPostoperatorio,
            String resultado, String indicacionesPostoperatorias, String controles, LocalDate proximoControl,
            String motivoCancelacion, Instant creadoEn, ConsentimientoResponse consentimiento, List<InsumoResponse> insumos,
            List<MonitoreoResponse> monitoreo, List<ArchivoInfo> archivos) {
    }
}
