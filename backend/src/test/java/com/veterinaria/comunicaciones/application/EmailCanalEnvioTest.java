package com.veterinaria.comunicaciones.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class EmailCanalEnvioTest {

    @Test
    void adjuntaLosPdfEnElCorreo() throws Exception {
        List<MimeMessage> enviados = new ArrayList<>();
        JavaMailSenderImpl sender = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage... mensajes) {
                enviados.addAll(List.of(mensajes));
            }
        };
        sender.setSession(Session.getInstance(new Properties()));
        var canal = new EmailCanalEnvio(sender, "clinica@test.local");
        var r = canal.enviar(new CanalEnvio.Mensaje("tutor@correo.test", "Formula", "Hola", List.of(
                new CanalEnvio.Adjunto("formula.pdf", "application/pdf", "%PDF-1.4 a".getBytes()),
                new CanalEnvio.Adjunto("carnet.pdf", "application/pdf", "%PDF-1.4 b".getBytes())), List.of(), java.util.Map.of()));
        assertEquals("SMTP", r.proveedor());
        MimeMessage m = enviados.get(0);
        m.saveChanges();
        assertEquals("Formula", m.getSubject());
        List<String> nombres = new ArrayList<>();
        recorrer(m.getContent(), nombres);
        assertTrue(nombres.containsAll(List.of("formula.pdf", "carnet.pdf")), nombres.toString());
    }

    private static void recorrer(Object contenido, List<String> nombres) throws Exception {
        if (contenido instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart p = mp.getBodyPart(i);
                if (p.getFileName() != null) {
                    nombres.add(p.getFileName());
                }
                recorrer(p.getContent(), nombres);
            }
        }
    }
}
