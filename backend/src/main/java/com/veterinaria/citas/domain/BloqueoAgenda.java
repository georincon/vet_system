package com.veterinaria.citas.domain;

import java.time.Instant;

import com.veterinaria.seguridad.domain.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Franja en la que un veterinario y/o una sala no admiten citas (vacaciones, mantenimiento...). */
@Entity
@Table(name = "bloqueo_agenda")
@Getter
@Setter
public class BloqueoAgenda {

    public static final java.util.Set<String> TIPOS = java.util.Set.of("VACACIONES", "CAPACITACION", "ALMUERZO",
            "MANTENIMIENTO", "CIRUGIA", "PERSONAL", "OTRO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinario_id")
    private Usuario veterinario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_id")
    private Sala sala;

    @Column(name = "sede_id")
    private Long sedeId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(length = 255)
    private String motivo;

    @Column(nullable = false, length = 20)
    private String tipo = "OTRO";

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
    }
}
