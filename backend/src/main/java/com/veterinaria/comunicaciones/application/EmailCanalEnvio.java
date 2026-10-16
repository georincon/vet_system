package com.veterinaria.comunicaciones.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/** Adaptador EMAIL real (JavaMailSender), activo solo si {@code vet.mail.host} esta configurado. */
@Component
@ConditionalOnExpression("!'${vet.mail.host:}'.isBlank()")
public class EmailCanalEnvio implements CanalEnvio {

    private final JavaMailSender sender;
    private final String from;

    public EmailCanalEnvio(JavaMailSender sender, @Value("${vet.mail.from:no-reply@veterinaria.local}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public String canal() {
        return "EMAIL";
    }

    @Override
    public ResultadoEnvio enviar(Mensaje m) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            boolean conAdjuntos = m.adjuntos() != null && !m.adjuntos().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(mime, conAdjuntos, "UTF-8");
            helper.setFrom(from);
            helper.setTo(m.destinatario());
            helper.setSubject(m.asunto() == null ? "" : m.asunto());
            helper.setText(m.cuerpo(), false);
            if (conAdjuntos) {
                for (Adjunto a : m.adjuntos()) {
                    helper.addAttachment(a.nombre(), new ByteArrayResource(a.contenido()),
                            a.contentType() == null ? "application/pdf" : a.contentType());
                }
            }
            sender.send(mime);
            return new ResultadoEnvio(false, "SMTP", mime.getMessageID());
        } catch (Exception e) {
            throw new EnvioFallidoException("SMTP: " + e.getMessage(), e);
        }
    }
}
