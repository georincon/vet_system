package com.veterinaria.seguridad.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.seguridad.domain.Rol;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.seguridad.internal.UsuarioRepository;

/** Crea el usuario {@code admin/admin} si no existe. Solo para desarrollo ({@code vet.security.seed-admin}). */
@Component
@ConditionalOnProperty(name = "vet.security.seed-admin", havingValue = "true")
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String ADMIN = "admin";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.existsByUsername(ADMIN)) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setUsername(ADMIN);
        admin.setPassword(passwordEncoder.encode(ADMIN));
        admin.setNombre("Administrador");
        admin.setRol(Rol.ADMIN);
        usuarioRepository.save(admin);
        log.warn("Usuario admin/admin creado (vet.security.seed-admin=true). No usar en produccion.");
    }
}
