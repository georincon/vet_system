package com.veterinaria.archivos.application;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.veterinaria.archivos.ArchivosApi;
import com.veterinaria.archivos.TipoArchivo;
import com.veterinaria.archivos.application.DetectorTipo.Real;
import com.veterinaria.archivos.internal.ArchivoRepository;
import com.veterinaria.archivos.internal.ArchivoRepository.Fila;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;

/**
 * Casos de uso de archivos: subida validada (extension permitida, tipo real por magic bytes, tamano, coherencia con el
 * tipo clinico), almacenamiento con nombre aleatorio, hash SHA-256, miniatura, descarga autenticada y baja logica.
 */
@Service
public class ArchivoService implements ArchivosApi {

    private static final Logger log = LoggerFactory.getLogger(ArchivoService.class);
    private static final int CABECERA = 8192;
    private static final long MAX_PIXELES = 100_000_000L;
    private static final int MOTIVO_MIN = 5;

    /** Archivo listo para transmitir. */
    public record Descarga(Path ruta, String contentType, String nombre, long tamano, boolean descargaForzada) {
    }

    private final ArchivoRepository repository;
    private final AlmacenamientoFisico disco;
    private final PacienteLector pacientes;
    private final SedePrincipal sede;
    private final long maxBytes;
    private final long maxBytesDicom;
    private final int miniaturaPx;

    public ArchivoService(ArchivoRepository repository, AlmacenamientoFisico disco, PacienteLector pacientes,
            SedePrincipal sede,
            @Value("${vet.archivos.max-bytes:52428800}") long maxBytes,
            @Value("${vet.archivos.max-bytes-dicom:268435456}") long maxBytesDicom,
            @Value("${vet.archivos.miniatura-px:400}") int miniaturaPx) {
        this.repository = repository;
        this.disco = disco;
        this.pacientes = pacientes;
        this.sede = sede;
        this.maxBytes = maxBytes;
        this.maxBytesDicom = maxBytesDicom;
        this.miniaturaPx = miniaturaPx;
    }

    // ------------------------------------------------------------------ subida

    @Transactional
    public ArchivoInfo subir(Long pacienteId, Long consultaId, Long ordenExamenId, Long ordenItemId, Long cirugiaId,
            TipoArchivo tipo, String descripcion, MultipartFile archivo) {
        pacientes.obtener(pacienteId);
        validarVinculos(pacienteId, consultaId, ordenExamenId, ordenItemId, cirugiaId);
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo esta vacio o no se envio (campo 'archivo')");
        }
        String nombre = nombreSeguro(archivo.getOriginalFilename());
        String extension = extension(nombre);
        Real esperado = DetectorTipo.porExtension(extension).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Extension no permitida: '." + extension
                        + "'. Permitidas: pdf, jpg, jpeg, png, webp, dcm, txt"));
        long limitePorExtension = esperado == Real.DICOM ? maxBytesDicom : maxBytes;
        if (archivo.getSize() > limitePorExtension) {
            throw tamanoExcedido(limitePorExtension);
        }

        Path temporal = disco.temporal();
        Path destino = null;
        Path destinoMiniatura = null;
        try {
            // 1) copia a disco calculando SHA-256 (sin cargar el archivo completo en memoria)
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long bytes;
            try (InputStream in = new DigestInputStream(archivo.getInputStream(), sha)) {
                bytes = disco.copiar(in, temporal);
            }
            if (bytes == 0) {
                throw new BusinessException("El archivo esta vacio");
            }
            // 2) tipo real por magic bytes; debe coincidir con la extension declarada
            byte[] cabecera = new byte[(int) Math.min(CABECERA, bytes)];
            try (InputStream in = Files.newInputStream(temporal)) {
                in.readNBytes(cabecera, 0, cabecera.length);
            }
            Real real = DetectorTipo.detectar(cabecera, cabecera.length).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido no corresponde a un tipo permitido (pdf, jpg, png, webp, dcm, txt)"));
            if (real != esperado) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido real del archivo ("
                        + real.contentType + ") no coincide con su extension '." + extension + "'");
            }
            if (bytes > (real == Real.DICOM ? maxBytesDicom : maxBytes)) {
                throw tamanoExcedido(real == Real.DICOM ? maxBytesDicom : maxBytes);
            }
            validarCoherencia(tipo, real);

            // 3) miniatura de imagenes decodificables (JPEG/PNG)
            Miniatura miniatura = (real == Real.JPEG || real == Real.PNG) ? miniatura(temporal) : null;

            // 4) publicar con nombre aleatorio
            String uuid = AlmacenamientoFisico.nuevoUuid();
            String ruta = disco.nuevaRutaRelativa(uuid, "", real.extension);
            disco.publicar(temporal, ruta);
            destino = disco.resolver(ruta);
            String rutaMiniatura = null;
            if (miniatura != null) {
                rutaMiniatura = disco.nuevaRutaRelativa(uuid, "_thumb", "jpg");
                disco.escribir(rutaMiniatura, miniatura.jpeg());
                destinoMiniatura = disco.resolver(rutaMiniatura);
            }
            long id = repository.insertar(pacienteId, consultaId, ordenExamenId, ordenItemId, cirugiaId, tipo.name(),
                    nombre, ruta, uuid + "." + real.extension, real.extension, real.contentType, bytes,
                    HexFormat.of().formatHex(sha.digest()), Textos.limpiar(descripcion), rutaMiniatura,
                    miniatura == null ? null : miniatura.anchoOriginal(), miniatura == null ? null : miniatura.altoOriginal(),
                    UsuarioActual.id().orElse(null), sede.id());
            return repository.buscar(id).orElseThrow().info();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        } catch (RuntimeException e) {
            disco.borrarSilencioso(destino);
            disco.borrarSilencioso(destinoMiniatura);
            throw e;
        } finally {
            disco.borrarSilencioso(temporal);
        }
    }

    private static ResponseStatusException tamanoExcedido(long limite) {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamano maximo permitido (" + (limite / (1024 * 1024)) + " MB)");
    }

    private static void validarCoherencia(TipoArchivo tipo, Real real) {
        boolean ok = switch (tipo) {
            case IMAGEN, RADIOGRAFIA, ECOGRAFIA -> real == Real.JPEG || real == Real.PNG || real == Real.WEBP || real == Real.DICOM;
            case DICOM -> real == Real.DICOM;
            case PDF_LABORATORIO -> real == Real.PDF;
            case INFORME, CONSENTIMIENTO, OTRO -> true;
        };
        if (!ok) {
            throw new BusinessException("El tipo " + tipo + " no admite archivos " + real.contentType);
        }
    }

    private void validarVinculos(Long pacienteId, Long consultaId, Long ordenId, Long itemId, Long cirugiaId) {
        if (consultaId != null && !repository.existe("consulta", consultaId, pacienteId, "paciente_id")) {
            throw new BusinessException("La consulta " + consultaId + " no existe o no es del paciente");
        }
        if (ordenId != null && !repository.existe("orden_examen", ordenId, pacienteId, "paciente_id")) {
            throw new BusinessException("La orden " + ordenId + " no existe o no es del paciente");
        }
        if (itemId != null) {
            if (ordenId == null) {
                throw new BusinessException("ordenItemId requiere ordenExamenId");
            }
            if (!repository.itemPerteneceAOrden(itemId, ordenId)) {
                throw new BusinessException("El item " + itemId + " no pertenece a la orden " + ordenId);
            }
        }
        if (cirugiaId != null && !repository.existe("cirugia", cirugiaId, pacienteId, "paciente_id")) {
            throw new BusinessException("El procedimiento/cirugia " + cirugiaId + " no existe o no es del paciente");
        }
    }

    /** Solo el nombre base, sin separadores de ruta ni caracteres de control (se usa unicamente para mostrar). */
    static String nombreSeguro(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            n = "archivo";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i < 0 || i == nombre.length() - 1 ? "" : nombre.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ miniaturas

    private record Miniatura(byte[] jpeg, int anchoOriginal, int altoOriginal) {
    }

    /** {@code null} si no se puede decodificar (p. ej. JPEG CMYK): el archivo se acepta sin miniatura. */
    private Miniatura miniatura(Path archivo) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(archivo.toFile())) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(iis);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(iis);
                int w = lector.getWidth(0);
                int h = lector.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELES) {
                    throw new BusinessException("La imagen excede las dimensiones permitidas");
                }
                BufferedImage original = lector.read(0);
                double escala = Math.min(1.0, (double) miniaturaPx / Math.max(w, h));
                int tw = Math.max(1, (int) Math.round(w * escala));
                int th = Math.max(1, (int) Math.round(h * escala));
                BufferedImage mini = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
                var g = mini.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, tw, th);
                    g.drawImage(original, 0, 0, tw, th, null);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(mini, "jpg", out);
                return new Miniatura(out.toByteArray(), w, h);
            } finally {
                lector.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo generar la miniatura de {}: {}", archivo.getFileName(), e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public ArchivoInfo obtener(Long id) {
        return fila(id).info();
    }

    @Transactional(readOnly = true)
    public List<ArchivoInfo> listarPaciente(Long pacienteId, TipoArchivo tipo, boolean incluirEliminados) {
        pacientes.obtener(pacienteId);
        return repository.listarPaciente(pacienteId, tipo == null ? null : tipo.name(), incluirEliminados)
                .stream().map(Fila::info).toList();
    }

    @Transactional(readOnly = true)
    public List<ArchivoInfo> filtrar(Long consultaId, Long ordenId, Long itemId, Long cirugiaId) {
        if (consultaId == null && ordenId == null && itemId == null && cirugiaId == null) {
            throw new BusinessException("Indique al menos un filtro: consultaId, ordenExamenId, ordenItemId o cirugiaId");
        }
        return repository.filtrar(consultaId, ordenId, itemId, cirugiaId).stream().map(Fila::info).toList();
    }

    /** Prepara la descarga del contenido (o de la miniatura) y deja constancia de la lectura. */
    public Descarga descargar(Long id, boolean miniatura) {
        Fila f = fila(id);
        if (!f.info().activo()) {
            throw new ResourceNotFoundException("Archivo", id);
        }
        if (miniatura && f.miniaturaRuta() == null) {
            throw new ResourceNotFoundException("Miniatura del archivo", id);
        }
        Path ruta = disco.resolver(miniatura ? f.miniaturaRuta() : f.ruta());
        if (!Files.isRegularFile(ruta)) {
            log.error("Archivo {} registrado pero ausente en disco: {}", id, ruta);
            throw new ResourceNotFoundException("Contenido del archivo", id);
        }
        repository.registrarLectura(id, UsuarioActual.id().orElse(null));
        try {
            boolean forzada = !miniatura && "application/dicom".equals(f.info().contentType());
            return new Descarga(ruta, miniatura ? "image/jpeg" : f.info().contentType(),
                    miniatura ? "miniatura-" + id + ".jpg" : f.info().nombreOriginal(), Files.size(ruta), forzada);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ baja logica

    @Transactional
    public ArchivoInfo eliminar(Long id, String motivo) {
        String m = Textos.limpiar(motivo);
        if (m == null || m.length() < MOTIVO_MIN) {
            throw new BusinessException("El motivo de eliminacion es obligatorio (minimo " + MOTIVO_MIN + " caracteres)");
        }
        Fila f = fila(id);
        if (!f.info().activo()) {
            throw new ConflictException("El archivo " + id + " ya fue eliminado");
        }
        repository.darDeBaja(id, UsuarioActual.id().orElse(null), m.length() > 255 ? m.substring(0, 255) : m);
        return fila(id).info();
    }

    private Fila fila(Long id) {
        return repository.buscar(id).orElseThrow(() -> new ResourceNotFoundException("Archivo", id));
    }

    // ------------------------------------------------------------------ ArchivosApi

    @Override
    @Transactional(readOnly = true)
    public Optional<ArchivoInfo> buscarActivo(Long id) {
        return repository.buscar(id).map(Fila::info).filter(ArchivoInfo::activo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchivoInfo> listarPorOrden(Long ordenExamenId) {
        return repository.listarPorColumna("orden_examen_id", ordenExamenId).stream().map(Fila::info).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchivoInfo> listarPorItem(Long ordenItemId) {
        return repository.listarPorColumna("orden_item_id", ordenItemId).stream().map(Fila::info).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchivoInfo> listarPorCirugia(Long cirugiaId) {
        return repository.listarPorColumna("cirugia_id", cirugiaId).stream().map(Fila::info).toList();
    }

    @Override
    public Optional<byte[]> leerMiniatura(Long id) {
        return repository.buscar(id).filter(f -> f.info().activo() && f.miniaturaRuta() != null)
                .flatMap(f -> leer(f.miniaturaRuta()));
    }

    @Override
    public Optional<byte[]> leerContenido(Long id) {
        return repository.buscar(id).filter(f -> f.info().activo()).flatMap(f -> leer(f.ruta()));
    }

    private Optional<byte[]> leer(String rutaRelativa) {
        try {
            Path p = disco.resolver(rutaRelativa);
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            log.warn("No se pudo leer {}: {}", rutaRelativa, e.toString());
            return Optional.empty();
        }
    }
}
