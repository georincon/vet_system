package com.veterinaria.clinica.domain;

import java.math.BigDecimal;
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

/** Historial de peso de un paciente. */
@Entity
@Table(name = "peso_registro")
@Getter
@Setter
public class PesoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(name = "peso_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "registrado_por")
    private Long registradoPor;
}
