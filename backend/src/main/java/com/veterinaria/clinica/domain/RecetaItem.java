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

/** Linea de una receta. */
@Entity
@Table(name = "receta_item")
@Getter
@Setter
public class RecetaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    /** Item del inventario/servicios, si el medicamento esta catalogado. */
    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private String medicamento;

    /** Concentracion del principio activo (p. ej. "500 mg"). */
    @Column(length = 100)
    private String concentracion;

    /** Presentacion (p. ej. "Tabletas x 10"). */
    @Column(length = 100)
    private String presentacion;

    @Column(length = 100)
    private String dosis;

    @Column(length = 60)
    private String via;

    @Column(length = 100)
    private String frecuencia;

    @Column(length = 100)
    private String duracion;

    private Integer cantidad;

    @Column(columnDefinition = "text")
    private String indicaciones;

    private Integer orden;
}
