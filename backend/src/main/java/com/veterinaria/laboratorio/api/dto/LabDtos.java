package com.veterinaria.laboratorio.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.archivos.ArchivosApi.ArchivoInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs del laboratorio, la imagenologia y la patologia. */
public final class LabDtos {

    private LabDtos() {
    }

    // ================================================================== catalogo

    public record RangoRequest(
            @NotNull(message = "La especie es obligatoria") Long especieId,
            @Pattern(regexp = "M|F", message = "Sexo debe ser M o F") String sexo,
            Integer edadMinMeses,
            Integer edadMaxMeses,
            BigDecimal valorMin,
            BigDecimal valorMax,
            BigDecimal criticoMin,
            BigDecimal criticoMax,
            @Size(max = 100) String fuente) {
    }

    public record RangoResponse(Long id, Long especieId, String especie, String sexo, Integer edadMinMeses,
            Integer edadMaxMeses, BigDecimal valorMin, BigDecimal valorMax, BigDecimal criticoMin,
            BigDecimal criticoMax, String fuente) {
    }

    public record ParametroRequest(
            Long id,
            @NotBlank(message = "El codigo del parametro es obligatorio") @Size(max = 20) String codigo,
            @NotBlank(message = "El nombre del parametro es obligatorio") @Size(max = 120) String nombre,
            @Size(max = 20) String unidad,
            @Size(max = 30) String unidadUcum,
            @Pattern(regexp = "NUMERICO|TEXTO|CUALITATIVO", message = "tipoValor: NUMERICO, TEXTO o CUALITATIVO") String tipoValor,
            Integer orden,
            @Size(max = 20) String codigoLoinc,
            @Pattern(regexp = "POR_VALIDAR|VALIDADO", message = "codificacionEstado: POR_VALIDAR o VALIDADO") String codificacionEstado,
            @Size(max = 100) String valorNormalTexto,
            @Size(max = 255) String opciones,
            Short decimales,
            Boolean activo,
            @Valid List<RangoRequest> rangos) {
    }

    public record ParametroResponse(Long id, String codigo, String nombre, String unidad, String unidadUcum,
            String tipoValor, Integer orden, String codigoLoinc, String codificacionEstado, String valorNormalTexto,
            String opciones, Short decimales, boolean activo, List<RangoResponse> rangos) {
    }

    public record ExamenRequest(
            @NotBlank(message = "El codigo es obligatorio") @Size(max = 30) String codigo,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 200) String nombre,
            @NotNull(message = "El tipo es obligatorio")
            @Pattern(regexp = "LAB|IMAGEN|PATOLOGIA|FUNCIONAL", message = "tipo: LAB, IMAGEN, PATOLOGIA o FUNCIONAL") String tipo,
            @Size(max = 30) String categoria,
            @Size(max = 500) String descripcion,
            @Size(max = 60) String muestra,
            Boolean requiereAyuno,
            Integer tiempoEntregaHoras,
            Boolean esExterno,
            Long itemId,
            @Size(max = 20) String codigoLoinc,
            @Pattern(regexp = "POR_VALIDAR|VALIDADO", message = "loincEstado: POR_VALIDAR o VALIDADO") String loincEstado,
            Boolean activo,
            @Valid List<ParametroRequest> parametros) {
    }

    public record ExamenResponse(Long id, String codigo, String nombre, String tipo, String categoria,
            String descripcion, String muestra, boolean requiereAyuno, Integer tiempoEntregaHoras, boolean esExterno,
            boolean activo, Long itemId, String codigoLoinc, String loincEstado, boolean esPanel,
            List<ParametroResponse> parametros) {
    }

    // ================================================================== ordenes

    public record OrdenRequest(
            @NotNull(message = "El paciente es obligatorio") Long pacienteId,
            Long consultaId,
            @Pattern(regexp = "RUTINA|URGENTE|STAT", message = "prioridad: RUTINA, URGENTE o STAT") String prioridad,
            String indicacionClinica,
            @Size(max = 150) String laboratorioExterno,
            List<Long> examenIds,
            List<String> examenCodigos) {
    }

    public record EstadoOrdenRequest(
            @NotBlank(message = "El estado es obligatorio")
            @Pattern(regexp = "MUESTRA_TOMADA|EN_PROCESO|CANCELADA", message = "estado: MUESTRA_TOMADA, EN_PROCESO o CANCELADA") String estado,
            @Size(max = 255) String motivo) {
    }

    public record MuestraRequest(@Size(max = 255) String observaciones) {
    }

    public record Persona(Long id, String nombre) {
    }

    public record OrdenResumen(Long id, String codigo, Long pacienteId, String pacienteNombre, String pacienteCodigo,
            String especie, Long consultaId, String prioridad, String estado, Instant solicitadaEn, Persona solicitadoPor,
            String laboratorioExterno, int itemsTotal, int itemsValidados, boolean hayCriticos, List<String> examenes) {
    }

    public record EventoOrden(Long id, String tipo, String estadoAnterior, String estadoNuevo, String detalle,
            Persona usuario, String origen, Instant creadoEn) {
    }

    public record ResultadoResponse(Long id, Long parametroId, String parametroCodigo, String parametroNombre,
            String codigoLoinc, BigDecimal valorNumerico, String valorTexto, String unidad, BigDecimal refMin,
            BigDecimal refMax, BigDecimal refCriticoMin, BigDecimal refCriticoMax, String bandera, boolean critico,
            String estado, String origen, String observacion, Instant registradoEn, Persona registradoPor,
            Long validacionId, Long reemplazaA, Long reemplazadoPor, String motivoCorreccion) {
    }

    public record InformeImagenResponse(Long id, int version, String estado, String region, String tecnica,
            String hallazgos, String impresionDiagnostica, String recomendaciones, String equipo,
            LocalDate fechaEstudio, Persona informadoPor, Long reemplazaA, String motivoCorreccion, Instant creadoEn) {
    }

    public record InformePatologiaResponse(Long id, int version, String estado, String tipoMuestra,
            String sitioAnatomico, LocalDate fechaToma, String descripcionMacroscopica, String descripcionMicroscopica,
            String diagnosticoMorfologico, String margenes, String comentarios, String laboratorioExterno,
            String numeroCasoExterno, String patologo, LocalDate fechaInforme, Persona informadoPor, Long reemplazaA,
            String motivoCorreccion, Instant creadoEn) {
    }

    public record ItemResponse(Long id, Long examenId, String examenCodigo, String examenNombre, String tipo,
            String estado, String interpretacion, Persona validadoPor, Instant validadoEn, int version,
            boolean corregido, Persona capturadoPor, Instant capturadoEn, int parametrosEsperados,
            List<ResultadoResponse> resultados, List<ResultadoResponse> resultadosCorregidos,
            InformeImagenResponse informeImagen, InformePatologiaResponse informePatologia,
            List<ArchivoInfo> archivos, String motivoCancelacion) {
    }

    public record OrdenResponse(Long id, String codigo, Long pacienteId, String pacienteNombre, String pacienteCodigo,
            String especie, Long consultaId, String prioridad, String estado, String indicacionClinica,
            String laboratorioExterno, Instant solicitadaEn, Persona solicitadoPor, Instant muestraTomadaEn,
            Persona muestraTomadaPor, String muestraObservaciones, Instant completadaEn, Instant canceladaEn,
            String motivoCancelacion, boolean hayCriticos, List<ItemResponse> items, List<EventoOrden> eventos,
            List<ArchivoInfo> archivos) {
    }

    // ================================================================== resultados

    public record ResultadoEntrada(
            Long parametroId,
            @Size(max = 20) String parametroCodigo,
            BigDecimal valorNumerico,
            @Size(max = 255) String valorTexto,
            @Size(max = 20) String unidad,
            @Size(max = 500) String observacion) {
    }

    public record CapturaRequest(@NotEmpty(message = "Indique al menos un resultado") @Valid List<ResultadoEntrada> resultados) {
    }

    public record ValidacionRequest(String interpretacion) {
    }

    public record CorreccionRequest(
            @NotBlank(message = "El motivo de la correccion es obligatorio") @Size(max = 500) String motivo,
            String interpretacion,
            @Valid List<ResultadoEntrada> resultados,
            @Valid InformeImagenRequest informeImagen,
            @Valid InformePatologiaRequest informePatologia) {
    }

    public record InformeImagenRequest(
            @NotBlank(message = "La region u organo es obligatoria") @Size(max = 150) String region,
            String tecnica,
            String hallazgos,
            String impresionDiagnostica,
            String recomendaciones,
            @Size(max = 150) String equipo,
            LocalDate fechaEstudio,
            Long informadoPorId) {
    }

    public record InformePatologiaRequest(
            @NotBlank(message = "El tipo de muestra es obligatorio") @Size(max = 100) String tipoMuestra,
            @Size(max = 150) String sitioAnatomico,
            LocalDate fechaToma,
            String descripcionMacroscopica,
            String descripcionMicroscopica,
            String diagnosticoMorfologico,
            String margenes,
            String comentarios,
            @Size(max = 150) String laboratorioExterno,
            @Size(max = 60) String numeroCasoExterno,
            @Size(max = 150) String patologo,
            LocalDate fechaInforme,
            Long informadoPorId) {
    }

    // ================================================================== importacion

    /** Linea de resultado en JSON (importacion y endpoint de integracion). */
    public record LineaJson(
            @Size(max = 30) String examenCodigo,
            @Size(max = 20) String parametroCodigo,
            @Size(max = 20) String loinc,
            @NotBlank(message = "El valor es obligatorio") @Size(max = 255) String valor,
            @Size(max = 20) String unidad,
            @Size(max = 500) String observacion) {
    }

    public record ImportacionJson(
            Long ordenId,
            @Size(max = 20) String ordenCodigo,
            @NotEmpty(message = "Indique al menos un resultado") @Valid List<LineaJson> resultados) {
    }

    /** Importacion con cuerpo JSON: {@code contenido} (HL7/CSV como texto) o {@code resultados} (JSON). */
    public record ImportacionRequest(
            @NotBlank(message = "El formato es obligatorio")
            @Pattern(regexp = "HL7|CSV|JSON", message = "formato: HL7, CSV o JSON") String formato,
            Long ordenId,
            String ordenCodigo,
            Boolean dryRun,
            @Pattern(regexp = "ARCHIVO|INTEGRACION", message = "origen: ARCHIVO o INTEGRACION") String origen,
            String contenido,
            @Valid List<LineaJson> resultados) {
    }

    public record LineaImportacion(int linea, String ordenCodigo, String examenCodigo, String parametroCodigo,
            String loinc, String valor, String unidad, String estado, Long itemId, Long parametroId, String parametro,
            String bandera, Boolean critico, String mensaje) {
    }

    public record ImportacionResponse(String formato, String origen, boolean dryRun, int total, int importados,
            int noCoinciden, int rechazados, List<Long> ordenIds, Long importacionId, List<LineaImportacion> lineas,
            List<String> advertencias) {
    }

    // ================================================================== historial y tendencia

    public record PuntoTendencia(Long resultadoId, Instant fecha, Long ordenId, String ordenCodigo,
            BigDecimal valorNumerico, String valorTexto, String unidad, String bandera, boolean critico,
            BigDecimal refMin, BigDecimal refMax) {
    }

    public record ResumenTendencia(int puntos, BigDecimal ultimo, BigDecimal anterior, BigDecimal variacionAbsoluta,
            BigDecimal variacionPorcentual, String direccion, BigDecimal minimo, BigDecimal maximo) {
    }

    public record ParametroTendencia(Long id, String codigo, String nombre, String unidad, String tipoValor) {
    }

    public record TendenciaResponse(Long pacienteId, ParametroTendencia parametro, List<PuntoTendencia> puntos,
            ResumenTendencia resumen) {
    }
}
