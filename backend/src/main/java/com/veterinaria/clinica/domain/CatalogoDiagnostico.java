package com.veterinaria.clinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Diagnostico frecuente del catalogo propio (sistema INTERNO; VENOM si se licencia). */
@Entity
@Table(name = "catalogo_diagnostico")
@Getter
@Setter
public class CatalogoDiagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String sistema;

    @Column(length = 60)
    private String categoria;

    @Column(nullable = false)
    private boolean activo = true;
}
