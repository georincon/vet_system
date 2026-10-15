package com.veterinaria.facturacion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.veterinaria.clinica.ConsultaCerrada;

/**
 * Genera/completa el presupuesto de la consulta recien cerrada. Corre tras confirmarse el cierre y en transaccion
 * propia: un fallo aqui no revierte ni toca la consulta (inmutable); es idempotente y puede reintentarse.
 */
@Component
class CargosConsultaCerradaListener {

    private static final Logger log = LoggerFactory.getLogger(CargosConsultaCerradaListener.class);

    private final CargoConsultaService cargos;

    CargosConsultaCerradaListener(CargoConsultaService cargos) {
        this.cargos = cargos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCerrarConsulta(ConsultaCerrada evento) {
        try {
            cargos.generar(evento.consultaId());
        } catch (RuntimeException e) {
            log.error("No se pudieron generar los cargos de la consulta {}", evento.consultaId(), e);
        }
    }
}
