package com.veterinaria.clinica.domain;

import java.time.Instant;

import com.veterinaria.seguridad.domain.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Nota posterior al cierre de una consulta. Append-only: la BD bloquea UPDATE y DELETE. */
@Entity
@Table(name = "consulta_addendum")
@Getter
@Setter
public class ConsultaAddendum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    /** ACLARACION, CORRECCION, RESULTADO_TARDIO o SEGUIMIENTO. */
    @Column(nullable = false, length = 20)
    private String tipo = "ACLARACION";

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void prePersist() {
        creadoEn = Instant.now();
    }
}
