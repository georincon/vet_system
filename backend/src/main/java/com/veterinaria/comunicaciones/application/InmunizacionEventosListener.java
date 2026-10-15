package com.veterinaria.comunicaciones.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.veterinaria.inmunizacion.InmunizacionAnulada;
import com.veterinaria.inmunizacion.InmunizacionRegistrada;
import com.veterinaria.inmunizacion.TipoAplicacion;

/**
 * Mantiene la cola coherente con inmunizacion: una nueva aplicacion reemplaza a las anteriores del mismo producto y una
 * anulacion invalida la suya; los recordatorios pendientes de esos origenes se cancelan (en la misma transaccion).
 */
@Component
class InmunizacionEventosListener {

    private final RecordatorioService recordatorios;

    InmunizacionEventosListener(RecordatorioService recordatorios) {
        this.recordatorios = recordatorios;
    }

    @EventListener
    void alRegistrar(InmunizacionRegistrada e) {
        recordatorios.cancelarPorOrigen(tabla(e.tipo()), e.reemplazadas(), "Reemplazado por una aplicacion posterior");
    }

    @EventListener
    void alAnular(InmunizacionAnulada e) {
        recordatorios.cancelarPorOrigen(tabla(e.tipo()), java.util.List.of(e.id()), "La aplicacion fue anulada");
    }

    private static String tabla(String tipo) {
        return TipoAplicacion.valueOf(tipo) == TipoAplicacion.VACUNA ? "inmunizacion" : "desparasitacion";
    }
}
