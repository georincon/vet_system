package com.veterinaria.inmunizacion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.veterinaria.clinica.ConsultaCerrada;

/**
 * Convierte las vacunas de una consulta recien cerrada en inmunizaciones. Corre tras confirmarse el cierre y y
 * registrarDesdeConsulta abre una transaccion nueva: un fallo aqui no revierte ni toca la consulta cerrada (inmutable); es idempotente y puede
 * reintentarse.
 */
@Component
class ConsultaCerradaListener {

    private static final Logger log = LoggerFactory.getLogger(ConsultaCerradaListener.class);

    private final InmunizacionService service;

    ConsultaCerradaListener(InmunizacionService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCerrarConsulta(ConsultaCerrada evento) {
        if (evento.vacunas().isEmpty()) {
            return;
        }
        try {
            int creadas = service.registrarDesdeConsulta(evento);
            log.info("Consulta {} cerrada: {} inmunizacion(es) generada(s)", evento.consultaId(), creadas);
        } catch (RuntimeException e) {
            log.error("No se pudieron generar las inmunizaciones de la consulta {}", evento.consultaId(), e);
        }
    }
}
