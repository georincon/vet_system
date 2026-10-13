package com.veterinaria.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Zona horaria de la clinica; define "hoy" y la interpretacion de fechas/horas locales. */
public final class ZonaClinica {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private ZonaClinica() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** Instante equivalente a una fecha y hora locales de la clinica. */
    public static Instant instante(LocalDate fecha, LocalTime hora) {
        return LocalDateTime.of(fecha, hora).atZone(ZONA).toInstant();
    }

    public static Instant instante(LocalDateTime local) {
        return local.atZone(ZONA).toInstant();
    }

    public static LocalDateTime local(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZONA);
    }
}
