package com.veterinaria.comunicaciones.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.veterinaria.comunicaciones.api.dto.EjecucionResponse;

/**
 * Job de recordatorios. Se desactiva con {@code vet.recordatorios.enabled=false}; la hora se configura con
 * {@code vet.recordatorios.cron} (por defecto 08:00, hora de la clinica) y la cola/reintentos se revisan cada
 * {@code vet.recordatorios.intervalo-envio-ms}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "vet.recordatorios.enabled", havingValue = "true", matchIfMissing = true)
class RecordatorioScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioScheduler.class);

    private final RecordatorioService service;

    RecordatorioScheduler(RecordatorioService service) {
        this.service = service;
    }

    @Scheduled(cron = "${vet.recordatorios.cron:0 0 8 * * *}", zone = "America/Bogota")
    void generarYEnviar() {
        try {
            EjecucionResponse r = service.ejecutar();
            log.info("Job de recordatorios: {}", r);
        } catch (RuntimeException e) {
            log.error("Fallo el job de recordatorios", e);
        }
    }

    @Scheduled(fixedDelayString = "${vet.recordatorios.intervalo-envio-ms:300000}", initialDelayString = "${vet.recordatorios.intervalo-envio-ms:300000}")
    void procesarCola() {
        try {
            service.procesarCola();
        } catch (RuntimeException e) {
            log.error("Fallo el procesamiento de la cola de recordatorios", e);
        }
    }
}
