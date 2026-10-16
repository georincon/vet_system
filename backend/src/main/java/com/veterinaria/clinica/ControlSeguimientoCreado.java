package com.veterinaria.clinica;

import java.time.LocalDate;

/**
 * Evento (API del modulo clinica) publicado al crear un control de seguimiento con {@code generarRecordatorio}. Lo atiende
 * {@code comunicaciones} (sincrono, misma transaccion) y deja el resultado en el propio evento: asi clinica ofrece el
 * recordatorio sin depender de comunicaciones.
 */
public final class ControlSeguimientoCreado {

    private final Long controlId;
    private final Long consultaId;
    private final Long pacienteId;
    private final LocalDate fechaSugerida;
    private Long recordatorioId;
    private String detalle;

    public ControlSeguimientoCreado(Long controlId, Long consultaId, Long pacienteId, LocalDate fechaSugerida) {
        this.controlId = controlId;
        this.consultaId = consultaId;
        this.pacienteId = pacienteId;
        this.fechaSugerida = fechaSugerida;
    }

    public Long controlId() {
        return controlId;
    }

    public Long consultaId() {
        return consultaId;
    }

    public Long pacienteId() {
        return pacienteId;
    }

    public LocalDate fechaSugerida() {
        return fechaSugerida;
    }

    /** Lo invoca el listener cuando creo el recordatorio. */
    public void recordatorioCreado(Long id) {
        this.recordatorioId = id;
        this.detalle = "Recordatorio programado";
    }

    /** Lo invoca el listener cuando no pudo crearlo (sin consentimiento, sin contacto, baja...). */
    public void omitido(String motivo) {
        this.recordatorioId = null;
        this.detalle = motivo;
    }

    public Long recordatorioId() {
        return recordatorioId;
    }

    public String detalle() {
        return detalle;
    }
}
