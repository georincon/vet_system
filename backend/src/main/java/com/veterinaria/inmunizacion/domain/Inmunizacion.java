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

/** Vacuna aplicada a un paciente (V1 + V16 + V25). Nunca se borra: se anula con motivo. */
@Entity
@Table(name = "inmunizacion")
@Getter
@Setter
public class Inmunizacion {

    public static final String APLICADA = "APLICADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacuna_id", nullable = false)
    private Vacuna vacuna;

    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(name = "consulta_vacuna_id")
    private Long consultaVacunaId;

    @Column(name = "fecha_aplicacion", nullable = false)
    private LocalDate fechaAplicacion;

    @Column(name = "fecha_proxima_dosis")
    private LocalDate fechaProximaDosis;

    @Column(length = 60)
    private String lote;

    @Column(name = "lote_id")
    private Long loteId;

    @Column(name = "fecha_vencimiento_lote")
    private LocalDate fechaVencimientoLote;

    @Column(length = 120)
    private String laboratorio;

    @Column(name = "veterinario_id")
    private Long veterinarioId;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(name = "dosis_numero")
    private Integer dosisNumero;

    @Column(name = "esquema_dosis_id")
    private Long esquemaDosisId;

    @Column(name = "sitio_aplicacion", length = 40)
    private String sitioAplicacion;

    @Column(name = "via_administracion", length = 20)
    private String viaAdministracion;

    @Column(name = "peso_kg", precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(nullable = false, length = 12)
    private String estado = APLICADA;

    @Column(name = "reaccion_adversa", columnDefinition = "text")
    private String reaccionAdversa;

    @Column(nullable = false)
    private boolean externa;

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
