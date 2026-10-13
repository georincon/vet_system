package com.veterinaria.clinica.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Receta emitida en una consulta, con sus items (medicamentos). */
@Entity
@Table(name = "receta")
@Getter
@Setter
public class Receta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Usuario veterinario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "indicaciones_generales", columnDefinition = "text")
    private String indicacionesGenerales;

    /** ACTIVA, CUMPLIDA o CANCELADA. */
    @Column(nullable = false, length = 10)
    private String estado = "ACTIVA";

    @Column(name = "valida_hasta")
    private LocalDate validaHasta;

    @Column(name = "es_controlada", nullable = false)
    private boolean esControlada;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden, id")
    @BatchSize(size = 50)
    private List<RecetaItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
    }
}
