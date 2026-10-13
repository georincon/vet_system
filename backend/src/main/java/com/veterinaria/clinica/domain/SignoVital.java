package com.veterinaria.clinica.domain;

import java.math.BigDecimal;
import java.time.Instant;

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

/** Toma de signos vitales dentro de una consulta (puede haber varias). */
@Entity
@Table(name = "signo_vital")
@Getter
@Setter
public class SignoVital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatura;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;

    @Column(name = "peso_kg", precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "condicion_corporal", length = 30)
    private String condicionCorporal;

    @Column(name = "puntaje_condicion_corporal")
    private Short puntajeCondicionCorporal;

    @Column(length = 60)
    private String mucosas;

    @Column(name = "tiempo_llenado_capilar", precision = 3, scale = 1)
    private BigDecimal tiempoLlenadoCapilar;

    @Column(name = "escala_dolor")
    private Short escalaDolor;

    @Column(name = "hidratacion_pct", precision = 4, scale = 1)
    private BigDecimal hidratacionPct;

    @Column(name = "presion_sistolica")
    private Integer presionSistolica;

    @Column(precision = 4, scale = 1)
    private BigDecimal spo2;

    @Column(name = "glucosa_mg_dl", precision = 6, scale = 1)
    private BigDecimal glucosaMgDl;

    @Column(columnDefinition = "text")
    private String notas;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @PrePersist
    void prePersist() {
        registradoEn = Instant.now();
    }
}
