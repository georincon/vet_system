package com.veterinaria.clinica.domain;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Alergia o reaccion adversa conocida de un paciente. */
@Entity
@Table(name = "alergia")
@Getter
@Setter
public class Alergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    private String descripcion;

    /** BAJA, MEDIA o ALTA. */
    @Column(nullable = false, length = 10)
    private String severidad = "BAJA";

    /** MEDICAMENTO, ALIMENTO, AMBIENTAL, VACUNA u OTRA. */
    @Column(nullable = false, length = 15)
    private String tipo = "OTRA";

    private String reaccion;

    @Column(name = "registrada_en")
    private LocalDate registradaEn;

    @Column(nullable = false)
    private boolean activo = true;
}
