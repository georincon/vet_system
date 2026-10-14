package com.veterinaria.inmunizacion.domain;

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

/** Antiparasitario del catalogo (V25); {@code intervaloDias} calcula la proxima aplicacion. */
@Entity
@Table(name = "antiparasitario")
@Getter
@Setter
public class Antiparasitario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    /** INTERNA, EXTERNA, DIROFILARIA o COMBINADA. */
    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(name = "principio_activo", length = 150)
    private String principioActivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @Column(name = "intervalo_dias", nullable = false)
    private int intervaloDias;

    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private boolean activo = true;
}
