package com.veterinaria.citas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espacio fisico reservable (consultorio, quirofano, etc.). */
@Entity
@Table(name = "sala")
@Getter
@Setter
public class Sala {

    public static final java.util.Set<String> TIPOS = java.util.Set.of("CONSULTORIO", "QUIROFANO",
            "HOSPITALIZACION", "PELUQUERIA", "IMAGENES", "LABORATORIO", "GUARDERIA", "ESPERA");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sede_id", nullable = false)
    private Long sedeId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tipo = "CONSULTORIO";

    @Column(nullable = false)
    private int capacidad = 1;

    @Column(nullable = false)
    private boolean activo = true;
}
