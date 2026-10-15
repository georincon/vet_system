package com.veterinaria.archivos.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.veterinaria.shared.time.ZonaClinica;

/**
 * Disco local bajo {@code vet.archivos.ruta}. Todo nombre en disco es aleatorio (UUID) y toda ruta se resuelve
 * contra la raiz verificando que no escape de ella (defensa ante path traversal / enlaces).
 */
@Component
class AlmacenamientoFisico {

    private final Path raiz;

    AlmacenamientoFisico(@Value("${vet.archivos.ruta:./data/archivos}") String ruta) {
        // los directorios se crean al primer uso (evita efectos secundarios al arrancar el contexto)
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
    }

    /** Ruta relativa nueva {@code yyyy/MM/<uuid>[sufijo].<ext>} (nunca incluye datos del cliente). */
    String nuevaRutaRelativa(String uuid, String sufijo, String extension) {
        LocalDate hoy = ZonaClinica.hoy();
        return "%d/%02d/%s%s.%s".formatted(hoy.getYear(), hoy.getMonthValue(), uuid, sufijo, extension);
    }

    static String nuevoUuid() {
        return UUID.randomUUID().toString();
    }

    /** Resuelve una ruta relativa garantizando que queda dentro de la raiz. */
    Path resolver(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank() || rutaRelativa.contains("..") || rutaRelativa.startsWith("/")
                || rutaRelativa.contains("\\") || rutaRelativa.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Ruta de archivo no valida");
        }
        Path p = raiz.resolve(rutaRelativa).normalize();
        if (!p.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta de archivo fuera del almacenamiento");
        }
        return p;
    }

    Path temporal() {
        try {
            Path tmp = raiz.resolve("tmp");
            Files.createDirectories(tmp);
            return Files.createTempFile(tmp, "subida-", ".part");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copia el flujo al destino temporal. */
    long copiar(InputStream in, Path destino) {
        try {
            return Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Mueve el temporal a su ruta definitiva (crea los directorios). */
    void publicar(Path temporal, String rutaRelativa) {
        try {
            Path destino = resolver(rutaRelativa);
            Files.createDirectories(destino.getParent());
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void escribir(String rutaRelativa, byte[] datos) {
        try {
            Path destino = resolver(rutaRelativa);
            Files.createDirectories(destino.getParent());
            Files.write(destino, datos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void borrarSilencioso(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignorado) {
            // limpieza de mejor esfuerzo
        }
    }
}
