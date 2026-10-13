package com.veterinaria.tutores.domain;

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

@Entity
@Table(name = "raza")
@Getter
@Setter
public class Raza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especie_id", nullable = false)
    private Especie especie;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "grupo_fci")
    private Short grupoFci;

    @Column(name = "numero_fci")
    private Integer numeroFci;

    private String origen;

    private String tamano;

    @Column(name = "reconocimiento_fci")
    private String reconocimientoFci;

    @Column(name = "potencialmente_peligrosa", nullable = false)
    private boolean potencialmentePeligrosa;
}
