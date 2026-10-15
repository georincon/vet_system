package com.veterinaria.comunicaciones.application;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** JavaMailSender configurado con las propiedades {@code vet.mail.*}; solo existe si hay host. */
@Configuration
@ConditionalOnExpression("!'${vet.mail.host:}'.isBlank()")
class MailConfig {

    @Bean
    JavaMailSender vetMailSender(@Value("${vet.mail.host}") String host, @Value("${vet.mail.port:587}") int port,
            @Value("${vet.mail.username:}") String username, @Value("${vet.mail.password:}") String password,
            @Value("${vet.mail.starttls:true}") boolean starttls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
            props.put("mail.smtp.auth", "true");
        }
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        return sender;
    }
}
