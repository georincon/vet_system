package com.veterinaria.archivos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** API publica del modulo de archivos para laboratorio, procedimientos y futuras etapas (lectura de adjuntos). */
public interface ArchivosApi {

    /** Metadatos de un adjunto; las URL son relativas a la API. */
    record ArchivoInfo(
            Long id,
            Long pacienteId,
            Long consultaId,
            Long ordenExamenId,
            Long ordenItemId,
            Long cirugiaId,
            String tipo,
            String nombreOriginal,
            String contentType,
            long tamanoBytes,
            String hashSha256,
            String descripcion,
            boolean tieneMiniatura,
            Integer anchoPx,
            Integer altoPx,
            Long subidoPorId,
            String subidoPorNombre,
            Instant subidoEn,
            boolean activo,
            Instant eliminadoEn,
            String motivoEliminacion,
            String urlContenido,
            String urlMiniatura) {
    }

    /** Adjunto activo (no eliminado) por id. */
    Optional<ArchivoInfo> buscarActivo(Long id);

    List<ArchivoInfo> listarPorOrden(Long ordenExamenId);

    List<ArchivoInfo> listarPorItem(Long ordenItemId);

    List<ArchivoInfo> listarPorCirugia(Long cirugiaId);

    /** Bytes de la miniatura (JPEG); vacio si el archivo no tiene. */
    Optional<byte[]> leerMiniatura(Long id);

    /** Bytes del archivo original (para incrustar imagenes pequenas en PDF). */
    Optional<byte[]> leerContenido(Long id);
}
