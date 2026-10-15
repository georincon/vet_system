package com.veterinaria.archivos.application;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/** Determina el tipo REAL de un archivo por sus primeros bytes (magic bytes), sin confiar en nombre ni Content-Type. */
final class DetectorTipo {

    enum Real {
        PDF("pdf", "application/pdf"),
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
        DICOM("dcm", "application/dicom"),
        TXT("txt", "text/plain");

        final String extension;
        final String contentType;

        Real(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        boolean esImagenRaster() {
            return this == JPEG || this == PNG || this == WEBP;
        }
    }

    /** Extensiones aceptadas (en minusculas). */
    static final Set<String> EXTENSIONES = Set.of("pdf", "jpg", "jpeg", "png", "webp", "dcm", "txt");

    private DetectorTipo() {
    }

    /** Tipo esperado segun la extension del nombre original (jpeg se normaliza a jpg). */
    static Optional<Real> porExtension(String extension) {
        return switch (extension) {
            case "pdf" -> Optional.of(Real.PDF);
            case "jpg", "jpeg" -> Optional.of(Real.JPEG);
            case "png" -> Optional.of(Real.PNG);
            case "webp" -> Optional.of(Real.WEBP);
            case "dcm" -> Optional.of(Real.DICOM);
            case "txt" -> Optional.of(Real.TXT);
            default -> Optional.empty();
        };
    }

    /** @param cabecera primeros bytes del archivo (hasta 8 KB) */
    static Optional<Real> detectar(byte[] cabecera, int largo) {
        if (largo >= 5 && cabecera[0] == '%' && cabecera[1] == 'P' && cabecera[2] == 'D' && cabecera[3] == 'F'
                && cabecera[4] == '-') {
            return Optional.of(Real.PDF);
        }
        if (largo >= 3 && (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8 && (cabecera[2] & 0xFF) == 0xFF) {
            return Optional.of(Real.JPEG);
        }
        if (largo >= 8 && (cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 'P' && cabecera[2] == 'N' && cabecera[3] == 'G'
                && cabecera[4] == 0x0D && cabecera[5] == 0x0A && cabecera[6] == 0x1A && cabecera[7] == 0x0A) {
            return Optional.of(Real.PNG);
        }
        if (largo >= 12 && cabecera[0] == 'R' && cabecera[1] == 'I' && cabecera[2] == 'F' && cabecera[3] == 'F'
                && cabecera[8] == 'W' && cabecera[9] == 'E' && cabecera[10] == 'B' && cabecera[11] == 'P') {
            return Optional.of(Real.WEBP);
        }
        if (largo >= 132 && cabecera[128] == 'D' && cabecera[129] == 'I' && cabecera[130] == 'C' && cabecera[131] == 'M') {
            return Optional.of(Real.DICOM);
        }
        if (esTextoPlano(cabecera, largo)) {
            return Optional.of(Real.TXT);
        }
        return Optional.empty();
    }

    /** Texto UTF-8 valido, sin bytes nulos ni caracteres de control (salvo tab, salto de linea y retorno). */
    private static boolean esTextoPlano(byte[] datos, int largo) {
        if (largo == 0) {
            return false;
        }
        for (int i = 0; i < largo; i++) {
            int b = datos[i] & 0xFF;
            if (b == 0 || (b < 0x20 && b != '\t' && b != '\n' && b != '\r' && b != '\f')) {
                return false;
            }
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        // endOfInput=false tolera una secuencia multibyte cortada al final de la cabecera
        var resultado = decoder.decode(ByteBuffer.wrap(datos, 0, largo), java.nio.CharBuffer.allocate(largo + 1), false);
        return !resultado.isError();
    }
}
