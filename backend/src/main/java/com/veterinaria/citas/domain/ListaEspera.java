package com.veterinaria.citas.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.Tutor;

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

/** Paciente que espera un cupo en la agenda. */
@Entity
@Table(name = "lista_espera")
@Getter
@Setter
public class ListaEspera {

    public static final java.util.Set<String> ESTADOS = java.util.Set.of("ACTIVA", "ASIGNADA", "CANCELADA", "VENCIDA");
    public static final java.util.Set<String> FRANJAS = java.util.Set.of("MANANA", "TARDE", "CUALQUIERA");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sede_id")
    private Long sedeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_cita_id")
    private TipoCita tipoCita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinario_id")
    private Usuario veterinario;

    @Column(name = "fecha_deseada_desde")
    private LocalDate fechaDeseadaDesde;

    @Column(name = "fecha_deseada_hasta")
    private LocalDate fechaDeseadaHasta;

    @Column(name = "franja_preferida", length = 10)
    private String franjaPreferida;

    @Column(nullable = false)
    private short prioridad = 3;

    @Column(nullable = false, length = 12)
    private String estado = "ACTIVA";

    @Column(name = "cita_asignada_id")
    private Long citaAsignadaId;

    @Column(length = 500)
    private String notas;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
    }
}
