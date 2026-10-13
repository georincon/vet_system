package com.veterinaria.tutores.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Clave compuesta de la relacion paciente-tutor (se rellena via {@code @MapsId}). */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class PacienteTutorId implements Serializable {

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "tutor_id")
    private Long tutorId;
}
