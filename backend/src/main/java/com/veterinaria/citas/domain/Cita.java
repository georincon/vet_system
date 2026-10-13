package com.veterinaria.citas.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.tutores.domain.Paciente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cita de un paciente con un veterinario. Guarda la fecha/hora local (campos que expone la API)
 * y los instantes equivalentes que exige el esquema historico para la restriccion de unicidad.
 */
@Entity
@Table(name = "cita")
@Getter
@Setter
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo legible CIT-YYYYMMDD-NNNN, asignado al crear. */
    @Column(nullable = false, length = 20, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Usuario veterinario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private Instant fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private Instant fechaHoraFin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_cita_id", nullable = false)
    private EstadoCita estadoCita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_cita_id")
    private TipoCita tipoCita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_id")
    private Sala sala;

    /** Sede de la cita; por defecto la sede principal. */
    @Column(name = "sede_id")
    private Long sedeId;

    private String motivo;

    private String notas;

    /** Marca de llegada (check-in a sala de espera). */
    @Column(name = "hora_llegada")
    private Instant horaLlegada;

    /** Inicio real de la atencion (la consulta se inicio). */
    @Column(name = "atencion_inicio")
    private Instant atencionInicio;

    @Column(name = "confirmada_en")
    private Instant confirmadaEn;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "cancelada_por")
    private Long canceladaPor;

    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @Column(name = "actualizado_el", nullable = false)
    private Instant actualizadoEl;

    @PrePersist
    void prePersist() {
        creadoEl = Instant.now();
        actualizadoEl = creadoEl;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEl = Instant.now();
    }
}
