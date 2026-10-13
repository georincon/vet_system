package com.veterinaria.citas.domain;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Codigos del catalogo {@code estado_cita} y su maquina de estados.
 *
 * <p>Flujo normal: PROGRAMADA -> CONFIRMADA -> EN_SALA -> EN_CURSO (en consulta) -> ATENDIDA (completada).
 * PROGRAMADA/CONFIRMADA pueden pasar a CANCELADA, NO_ASISTIO o REAGENDADA; EN_SALA a CANCELADA/NO_ASISTIO.
 */
public enum EstadoCitaCodigo {
    PROGRAMADA,
    CONFIRMADA,
    EN_SALA,
    EN_CURSO,
    ATENDIDA,
    CANCELADA,
    NO_ASISTIO,
    REAGENDADA;

    /** Estados que liberan la franja horaria del veterinario, la sala y el paciente. */
    public static final Set<String> LIBERAN_AGENDA = Set.of(
            CANCELADA.name(), NO_ASISTIO.name(), REAGENDADA.name());

    /** Estados finales: no admiten mas cambios. */
    public static final Set<EstadoCitaCodigo> TERMINALES = EnumSet.of(ATENDIDA, CANCELADA, NO_ASISTIO, REAGENDADA);

    /** Estados siguientes validos desde este. */
    public Set<EstadoCitaCodigo> siguientes() {
        return switch (this) {
            case PROGRAMADA -> EnumSet.of(CONFIRMADA, EN_SALA, CANCELADA, NO_ASISTIO, REAGENDADA);
            case CONFIRMADA -> EnumSet.of(EN_SALA, CANCELADA, NO_ASISTIO, REAGENDADA);
            case EN_SALA -> EnumSet.of(EN_CURSO, CANCELADA, NO_ASISTIO);
            case EN_CURSO -> EnumSet.of(ATENDIDA);
            default -> EnumSet.noneOf(EstadoCitaCodigo.class);
        };
    }

    public boolean puedePasarA(EstadoCitaCodigo destino) {
        return siguientes().contains(destino);
    }

    /** Interpreta un codigo, aceptando los alias EN_CONSULTA (=EN_CURSO), COMPLETADA (=ATENDIDA) y AGENDADA (=PROGRAMADA). */
    public static Optional<EstadoCitaCodigo> parse(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String t = texto.trim().toUpperCase();
        return switch (t) {
            case "EN_CONSULTA" -> Optional.of(EN_CURSO);
            case "COMPLETADA" -> Optional.of(ATENDIDA);
            case "AGENDADA" -> Optional.of(PROGRAMADA);
            default -> {
                try {
                    yield Optional.of(valueOf(t));
                } catch (IllegalArgumentException e) {
                    yield Optional.empty();
                }
            }
        };
    }
}
