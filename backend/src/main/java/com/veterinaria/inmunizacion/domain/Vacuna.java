package com.veterinaria.inmunizacion.domain;

import java.math.BigDecimal;

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

/** Vacuna del catalogo (V1 + V16). */
@Entity
@Table(name = "vacuna")
@Getter
@Setter
public class Vacuna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @Column(name = "periodo_eficacia_dias", nullable = false)
    private int periodoEficaciaDias;

    @Column(nullable = false, length = 12)
    private String clasificacion = "NO_NUCLEO";

    @Column(name = "enfermedades_objetivo")
    private String enfermedadesObjetivo;

    @Column(length = 120)
    private String fabricante;

    @Column(name = "via_administracion", length = 20)
    private String viaAdministracion;

    @Column(name = "dosis_ml", precision = 4, scale = 2)
    private BigDecimal dosisMl;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "obligatoria_ley", nullable = false)
    private boolean obligatoriaLey;

    @Column(nullable = false)
    private boolean activo = true;
}
