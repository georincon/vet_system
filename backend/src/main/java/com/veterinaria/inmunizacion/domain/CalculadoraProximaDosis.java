package com.veterinaria.inmunizacion.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Calculo puro de la proxima fecha segun el calendario del esquema.
 * <ul>
 *   <li>Existe la dosis N+1: fecha + intervalo_dias_previa, sin bajar de nacimiento + edad_minima de esa dosis.</li>
 *   <li>Era la ultima: se repite {@code repetir_cada_dias} (refuerzo periodico); si no hay, la serie esta completa
 *       (sin proxima fecha).</li>
 *   <li>Sin esquema: fecha + periodo_eficacia_dias de la vacuna.</li>
 * </ul>
 */
public final class CalculadoraProximaDosis {

    private CalculadoraProximaDosis() {
    }

    /** @param dosis calendario ordenado por {@code orden}; vacio/nulo = sin esquema */
    public static LocalDate proxima(List<EsquemaVacunalDosis> dosis, int dosisNumero, LocalDate aplicacion,
            LocalDate nacimiento, int periodoEficaciaDias) {
        if (dosis == null || dosis.isEmpty()) {
            return aplicacion.plusDays(periodoEficaciaDias);
        }
        Optional<EsquemaVacunalDosis> siguiente = dosis.stream().filter(d -> d.getOrden() == dosisNumero + 1).findFirst();
        if (siguiente.isPresent()) {
            EsquemaVacunalDosis s = siguiente.get();
            LocalDate fecha = aplicacion.plusDays(s.getIntervaloDiasPrevia() == null ? 0 : s.getIntervaloDiasPrevia());
            if (nacimiento != null && s.getEdadMinimaSemanas() != null) {
                LocalDate minima = nacimiento.plusWeeks(s.getEdadMinimaSemanas());
                if (minima.isAfter(fecha)) {
                    fecha = minima;
                }
            }
            return fecha;
        }
        EsquemaVacunalDosis ultima = dosis.get(dosis.size() - 1);
        return ultima.getRepetirCadaDias() == null ? null : aplicacion.plusDays(ultima.getRepetirCadaDias());
    }

    /** Dosis del calendario que corresponde al numero aplicado (la ultima si se paso del final). */
    public static Optional<EsquemaVacunalDosis> dosisDe(List<EsquemaVacunalDosis> dosis, int dosisNumero) {
        if (dosis == null || dosis.isEmpty()) {
            return Optional.empty();
        }
        return dosis.stream().filter(d -> d.getOrden() == dosisNumero).findFirst()
                .or(() -> Optional.of(dosis.get(dosis.size() - 1)));
    }
}
