package com.veterinaria.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/** Registra el transaction manager que propaga el usuario actual a la auditoria por trigger. */
@Configuration
public class PersistenciaConfig {

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new AuditoriaTransactionManager(entityManagerFactory);
    }
}
