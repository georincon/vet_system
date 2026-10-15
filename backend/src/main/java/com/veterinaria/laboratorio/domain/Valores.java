package com.veterinaria.laboratorio.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Normalizacion de valores y unidades que llegan de captura manual o de analizadores. */
public final class Valores {

    private static final Set<String> POSITIVOS = Set.of("POS", "POSITIVE", "POSITIVO", "DETECTED", "DETECTADO", "REACTIVO", "REACTIVE");
    private static final Set<String> NEGATIVOS = Set.of("NEG", "NEGATIVE", "NEGATIVO", "NOT DETECTED", "NO DETECTADO", "NO REACTIVO", "NONREACTIVE");

    private Valores() {
    }

    /** Numero con coma o punto decimal; ignora un prefijo de comparador (&lt; &gt; &le; &ge;). */
    public static Optional<BigDecimal> numero(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String t = texto.trim().replaceFirst("^[<>]=?\\s*", "").replace(',', '.');
        try {
            return t.isEmpty() ? Optional.empty() : Optional.of(new BigDecimal(t));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Resultado cualitativo canonico: POS/NEG y sinonimos se llevan a POSITIVO/NEGATIVO si el parametro los usa. */
    public static String cualitativo(String valor, String opciones) {
        String v = Banderas.normalizar(valor);
        List<String> permitidas = opciones == null || opciones.isBlank() ? List.of()
                : Arrays.stream(opciones.split("\\|")).map(Banderas::normalizar).toList();
        if (permitidas.contains("POSITIVO") && POSITIVOS.contains(v)) {
            return "POSITIVO";
        }
        if (permitidas.contains("NEGATIVO") && NEGATIVOS.contains(v)) {
            return "NEGATIVO";
        }
        for (String p : permitidas) {
            if (p.equals(v)) {
                return p;
            }
        }
        return v;
    }

    /** {@code true} si el valor esta entre las opciones del parametro (o no hay opciones definidas). */
    public static boolean permitido(String valorCanonico, String opciones) {
        if (opciones == null || opciones.isBlank()) {
            return true;
        }
        return Arrays.stream(opciones.split("\\|")).map(Banderas::normalizar).anyMatch(valorCanonico::equals);
    }

    /** Unidad comparable: minusculas, sin espacios, "x10^3/uL" ~ "10*3/uL", micro como "u". */
    public static String unidadComparable(String unidad) {
        if (unidad == null) {
            return "";
        }
        String u = unidad.trim().toLowerCase(Locale.ROOT).replace("µ", "u").replace("μ", "u").replace(" ", "");
        u = u.replace("x10^", "10*").replace("10^", "10*");
        return u;
    }

    public static boolean mismaUnidad(String a, String b) {
        return unidadComparable(a).equals(unidadComparable(b));
    }
}
