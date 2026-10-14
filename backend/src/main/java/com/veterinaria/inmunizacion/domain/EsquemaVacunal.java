package com.veterinaria.inmunizacion.domain;

import java.util.ArrayList;
import java.util.List;

import com.veterinaria.tutores.domain.Especie;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Esquema vacunal de una vacuna para una especie, con su calendario de dosis. */
@Entity
@Table(name = "esquema_vacunal")
@Getter
@Setter
public class EsquemaVacunal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacuna_id", nullable = false)
    private Vacuna vacuna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id")
    private Especie especie;

    @Column(length = 120)
    private String fuente;

    @Column(columnDefinition = "text")
    private String notas;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "esquema", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<EsquemaVacunalDosis> dosis = new ArrayList<>();
}
