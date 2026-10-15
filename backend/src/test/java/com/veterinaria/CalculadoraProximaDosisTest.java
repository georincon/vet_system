package com.veterinaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.veterinaria.inmunizacion.domain.CalculadoraProximaDosis;
import com.veterinaria.inmunizacion.domain.EsquemaVacunalDosis;

/** Unitaria: reglas puras de la proxima dosis. */
class CalculadoraProximaDosisTest {

    private static EsquemaVacunalDosis dosis(int orden, Integer semanas, Integer intervalo, Integer repetir) {
        EsquemaVacunalDosis d = new EsquemaVacunalDosis();
        d.setOrden(orden);
        d.setNombreDosis("D" + orden);
        d.setEdadMinimaSemanas(semanas);
        d.setIntervaloDiasPrevia(intervalo);
        d.setRepetirCadaDias(repetir);
        return d;
    }

    private final LocalDate fecha = LocalDate.of(2026, 1, 10);
    private final List<EsquemaVacunalDosis> calendario = List.of(dosis(1, 6, null, null), dosis(2, 9, 21, null),
            dosis(3, null, 365, 365));

    @Test
    void siguienteDosisUsaElIntervalo() {
        assertEquals(fecha.plusDays(21), CalculadoraProximaDosis.proxima(calendario, 1, fecha, null, 100));
        assertEquals(fecha.plusDays(365), CalculadoraProximaDosis.proxima(calendario, 2, fecha, null, 100));
    }

    @Test
    void laEdadMinimaNoSeAdelanta() {
        LocalDate nacimiento = fecha.minusDays(10);
        assertEquals(nacimiento.plusWeeks(9), CalculadoraProximaDosis.proxima(calendario, 1, fecha, nacimiento, 100));
    }

    @Test
    void ultimaDosisRepiteOTermina() {
        assertEquals(fecha.plusDays(365), CalculadoraProximaDosis.proxima(calendario, 3, fecha, null, 100));
        assertEquals(fecha.plusDays(365), CalculadoraProximaDosis.proxima(calendario, 9, fecha, null, 100));
        assertNull(CalculadoraProximaDosis.proxima(List.of(dosis(1, null, null, null)), 1, fecha, null, 100));
    }

    @Test
    void sinEsquemaUsaElPeriodoDeEficacia() {
        assertEquals(fecha.plusDays(100), CalculadoraProximaDosis.proxima(List.of(), 1, fecha, null, 100));
    }
}
