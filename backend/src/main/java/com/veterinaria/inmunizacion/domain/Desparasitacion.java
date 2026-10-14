package com.veterinaria.inmunizacion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Aplicacion de antiparasitario a un paciente (V16 + V25). Se anula, no se borra. */
@Entity
@Table(name = "desparasitacion")
@Getter
@Setter
public class Desparasitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "antiparasitario_id")
    private Antiparasitario antiparasitario;

    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private String producto;

    @Column(name = "principio_activo", length = 150)
    private String principioActivo;

    @Column(length = 80)
    private String dosis;

    @Column(name = "peso_kg", precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "fecha_aplicacion", nullable = false)
    private LocalDate fechaAplicacion;

    @Column(name = "proxima_fecha")
    private LocalDate proximaFecha;

    @Column(name = "lote_id")
    private Long loteId;

    @Column(length = 60)
    private String lote;

    @Column(name = "fecha_vencimiento_lote")
    private LocalDate fechaVencimientoLote;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(name = "veterinario_id")
    private Long veterinarioId;

    @Column(name = "sede_id")
    private Long sedeId;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(nullable = false, length = 12)
    private String estado = Inmunizacion.APLICADA;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Column(name = "anulado_por")
    private Long anuladoPor;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
    }
}
