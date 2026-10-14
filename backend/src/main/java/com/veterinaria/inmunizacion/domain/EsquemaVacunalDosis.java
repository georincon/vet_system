package com.veterinaria.inmunizacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Dosis de un esquema: la dosis N+1 se programa {@code intervaloDiasPrevia} tras la N (y no antes de la edad minima). */
@Entity
@Table(name = "esquema_vacunal_dosis")
@Getter
@Setter
public class EsquemaVacunalDosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "esquema_id", nullable = false)
    private EsquemaVacunal esquema;

    @Column(nullable = false)
    private int orden;

    @Column(name = "nombre_dosis", nullable = false, length = 60)
    private String nombreDosis;

    @Column(name = "edad_minima_semanas")
    private Integer edadMinimaSemanas;

    @Column(name = "intervalo_dias_previa")
    private Integer intervaloDiasPrevia;

    @Column(name = "es_refuerzo", nullable = false)
    private boolean esRefuerzo;

    @Column(name = "repetir_cada_dias")
    private Integer repetirCadaDias;
}
