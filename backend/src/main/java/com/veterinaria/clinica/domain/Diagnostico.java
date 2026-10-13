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

/** Diagnostico de una consulta: del catalogo o de texto libre. */
@Entity
@Table(name = "diagnostico")
@Getter
@Setter
public class Diagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogo_id")
    private CatalogoDiagnostico catalogo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "codigo_cie", length = 20)
    private String codigoCie;

    @Column(name = "codigo_venom", length = 30)
    private String codigoVenom;

    /** PRESUNTIVO, DIFERENCIAL o DEFINITIVO. */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private int orden = 1;

    @Column(length = 10)
    private String lateralidad;

    @Column(nullable = false)
    private boolean resuelto;
}
