package com.veterinaria.clinica.domain;

import com.veterinaria.tutores.domain.Especie;

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

/** Plantilla de texto SOAP (lectura) para precargar una consulta. */
@Entity
@Table(name = "plantilla_soap")
@Getter
@Setter
public class PlantillaSoap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Null = aplica a cualquier especie. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @Column(name = "tipo_consulta", length = 20)
    private String tipoConsulta;

    @Column(columnDefinition = "text")
    private String subjetivo;

    @Column(columnDefinition = "text")
    private String objetivo;

    @Column(columnDefinition = "text")
    private String evaluacion;

    @Column(columnDefinition = "text")
    private String plan;

    @Column(nullable = false)
    private boolean activo = true;
}
