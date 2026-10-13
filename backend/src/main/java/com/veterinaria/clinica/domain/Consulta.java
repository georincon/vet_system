package com.veterinaria.clinica.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.veterinaria.citas.domain.Cita;
import com.veterinaria.seguridad.domain.Usuario;
import com.veterinaria.tutores.domain.Paciente;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Consulta clinica de un paciente, opcionalmente asociada a una cita. */
@Entity
@Table(name = "consulta")
@Getter
@Setter
public class Consulta {

    public static final String ESTADO_ABIERTA = "ABIERTA";
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo legible CON-YYYYMMDD-NNNN, asignado al crear. */
    @Column(nullable = false, length = 30, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Usuario veterinario;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Columna historica obligatoria: inicio del dia de la consulta. */
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @Column(name = "sede_id")
    private Long sedeId;

    /** ABIERTA (editable) o CERRADA (inmutable; solo addendums). */
    @Column(nullable = false, length = 10)
    private String estado = ESTADO_ABIERTA;

    @Column(name = "tipo_consulta", nullable = false, length = 20)
    private String tipoConsulta = "GENERAL";

    @Column(name = "plantilla_id")
    private Long plantillaId;

    @Column(length = 10)
    private String triaje;

    @Column(name = "proxima_revision")
    private LocalDate proximaRevision;

    private String motivo;

    @Column(columnDefinition = "text")
    private String anamnesis;

    @Column(columnDefinition = "text")
    private String subjetivo;

    @Column(columnDefinition = "text")
    private String objetivo;

    @Column(columnDefinition = "text")
    private String evaluacion;

    @Column(columnDefinition = "text")
    private String plan;

    @Column(columnDefinition = "text")
    private String pronostico;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Column(name = "cerrada_por")
    private Long cerradaPor;

    @Column(name = "firma_hash", length = 64)
    private String firmaHash;

    @Column(length = 2000)
    private String sintomatologia;

    @Column(length = 2000)
    private String diagnostico;

    @Column(length = 2000)
    private String tratamiento;

    @Column(length = 2000)
    private String observaciones;

    @Column(name = "peso_kg", precision = 5, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "temperatura_c", precision = 4, scale = 2)
    private BigDecimal temperaturaC;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @Column(name = "actualizado_el", nullable = false)
    private Instant actualizadoEl;

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden, id")
    @BatchSize(size = 50)
    private List<ConsultaMedicamento> medicamentos = new ArrayList<>();

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<ConsultaVacuna> vacunas = new ArrayList<>();

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<SignoVital> signosVitales = new ArrayList<>();

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden, id")
    @BatchSize(size = 50)
    private List<Diagnostico> diagnosticos = new ArrayList<>();

    @OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<Receta> recetas = new ArrayList<>();

    public boolean estaCerrada() {
        return ESTADO_CERRADA.equals(estado);
    }

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
