package com.veterinaria.comunicaciones.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Adaptador EMAIL de registro: sin SMTP configurado solo deja el mensaje en el log (ENVIADO_SIMULADO). */
@Component
@ConditionalOnExpression("'${vet.mail.host:}'.isBlank()")
class EmailSimuladoCanalEnvio implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(EmailSimuladoCanalEnvio.class);

    @Override
    public String canal() {
        return "EMAIL";
    }

    @Override
    public ResultadoEnvio enviar(Mensaje m) {
        log.info("[EMAIL simulado] para={} asunto='{}' adjuntos={}\n{}", m.destinatario(), m.asunto(),
                m.adjuntos() == null ? List.of() : m.adjuntos().stream().map(a -> a.nombre() + " (" + a.contenido().length + " B)").toList(),
                m.cuerpo());
        return new ResultadoEnvio(true, "LOG_SIMULADO", "sim-" + UUID.randomUUID());
    }
}
