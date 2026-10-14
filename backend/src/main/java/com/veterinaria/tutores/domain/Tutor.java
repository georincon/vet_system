package com.veterinaria.tutores.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Persona responsable de uno o mas pacientes. */
@Entity
@Table(name = "tutor")
@Getter
@Setter
public class Tutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(length = 120)
    private String apellidos;

    @Column(length = 30)
    private String telefono;

    @Column(name = "telefono_alt", length = 30)
    private String telefonoAlt;

    private String email;

    private String direccion;

    @Column(length = 120)
    private String ciudad;

    @Column(columnDefinition = "text")
    private String notas;

    /** Consentimiento de contacto por canal (Ley 1581/2012; columnas de V13). */
    @Column(name = "acepta_email", nullable = false)
    private boolean aceptaEmail;

    @Column(name = "acepta_whatsapp", nullable = false)
    private boolean aceptaWhatsapp;

    @Column(name = "acepta_sms", nullable = false)
    private boolean aceptaSms;

    @Column(name = "autoriza_tratamiento_datos", nullable = false)
    private boolean autorizaTratamientoDatos;

    @Column(name = "fecha_autorizacion_datos")
    private Instant fechaAutorizacionDatos;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }
}
