package com.veterinaria.clinica.domain;

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

/** Medicamento prescrito durante una consulta. */
@Entity
@Table(name = "consulta_medicamento")
@Getter
@Setter
public class ConsultaMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 120)
    private String dosis;

    @Column(length = 120)
    private String frecuencia;

    @Column(length = 120)
    private String duracion;

    @Column(length = 50)
    private String via;

    private String observaciones;

    private Integer orden;
}
