package com.veterinaria.comunicaciones.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.veterinaria.clinica.ControlSeguimientoCreado;

/** Atiende la oferta de recordatorio de un control de seguimiento (evento de clinica) y deja el resultado en el evento. */
@Component
class ControlSeguimientoListener {

    private final RecordatorioService recordatorios;

    ControlSeguimientoListener(RecordatorioService recordatorios) {
        this.recordatorios = recordatorios;
    }

    @EventListener
    void alCrearControl(ControlSeguimientoCreado e) {
        var r = recordatorios.programarControl(e.controlId(), e.pacienteId(), e.fechaSugerida());
        if (r.id() != null) {
            e.recordatorioCreado(r.id());
        } else {
            e.omitido(r.detalle());
        }
    }
}
