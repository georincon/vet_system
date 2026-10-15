package com.veterinaria.laboratorio.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reglas puras de interpretacion de resultados: seleccion del rango de referencia aplicable (especie ya filtrada;
 * sexo y edad) y calculo de la bandera N/L/H/LL/HH/A. Sin dependencias de Spring: se prueba como unidad.
 */
public final class Banderas {

    private Banderas() {
    }

    /** Rango candidato del catalogo (ya filtrado por parametro y especie). */
    public record Rango(Long id, String sexo, Integer edadMinMeses, Integer edadMaxMeses, BigDecimal min, BigDecimal max,
            BigDecimal criticoMin, BigDecimal criticoMax) {
    }

    /** Resultado de evaluar un valor: bandera (puede ser null si no hay rango) y marca de critico. */
    public record Evaluacion(String bandera, boolean critico) {
    }

    /**
     * Elige el rango mas especifico: debe coincidir el sexo (si el rango lo fija) y la edad estar dentro de sus
     * limites (si la edad se desconoce, no aplican los rangos con limites de edad). Gana el que fija sexo, luego el
     * de edad mas acotada.
     */
    public static Optional<Rango> elegir(List<Rango> candidatos, String sexo, Integer edadMeses) {
        return candidatos.stream()
                .filter(r -> r.sexo() == null || r.sexo().equals(sexo))
                .filter(r -> r.edadMinMeses() == null || (edadMeses != null && edadMeses >= r.edadMinMeses()))
                .filter(r -> r.edadMaxMeses() == null || (edadMeses != null && edadMeses <= r.edadMaxMeses()))
                .max(Comparator.<Rango>comparingInt(r -> especificidad(r))
                        .thenComparing(r -> -amplitud(r))
                        .thenComparing(Rango::id));
    }

    private static int especificidad(Rango r) {
        return (r.sexo() != null ? 100 : 0) + (r.edadMinMeses() != null ? 10 : 0) + (r.edadMaxMeses() != null ? 10 : 0);
    }

    private static int amplitud(Rango r) {
        int min = r.edadMinMeses() == null ? 0 : r.edadMinMeses();
        int max = r.edadMaxMeses() == null ? 100_000 : r.edadMaxMeses();
        return max - min;
    }

    /** LL/HH si cruza un limite critico; L/H fuera del rango; N dentro; sin rango no hay bandera. */
    public static Evaluacion numerica(BigDecimal valor, Rango r) {
        if (valor == null || r == null) {
            return new Evaluacion(null, false);
        }
        if (r.criticoMin() != null && valor.compareTo(r.criticoMin()) < 0) {
            return new Evaluacion("LL", true);
        }
        if (r.criticoMax() != null && valor.compareTo(r.criticoMax()) > 0) {
            return new Evaluacion("HH", true);
        }
        if (r.min() != null && valor.compareTo(r.min()) < 0) {
            return new Evaluacion("L", false);
        }
        if (r.max() != null && valor.compareTo(r.max()) > 0) {
            return new Evaluacion("H", false);
        }
        if (r.min() == null && r.max() == null) {
            return new Evaluacion(null, false);
        }
        return new Evaluacion("N", false);
    }

    /** Cualitativo: N si coincide con el valor normal del parametro, A si difiere; sin valor normal no hay bandera. */
    public static Evaluacion cualitativa(String valor, String valorNormal) {
        if (valor == null || valorNormal == null || valorNormal.isBlank()) {
            return new Evaluacion(null, false);
        }
        return new Evaluacion(normalizar(valor).equals(normalizar(valorNormal)) ? "N" : "A", false);
    }

    /** Mayusculas, sin acentos ni espacios sobrantes (para comparar valores cualitativos). */
    public static String normalizar(String texto) {
        String t = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
