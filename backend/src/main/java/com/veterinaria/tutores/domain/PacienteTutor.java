package com.veterinaria.tutores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vinculo M:N entre paciente y tutor; exactamente uno es principal cuando hay tutores. */
@Entity
@Table(name = "paciente_tutor")
@Getter
@Setter
@NoArgsConstructor
public class PacienteTutor {

    @EmbeddedId
    private PacienteTutorId id = new PacienteTutorId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("pacienteId")
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tutorId")
    @JoinColumn(name = "tutor_id", nullable = false)
    private Tutor tutor;

    @Column(name = "es_principal", nullable = false)
    private boolean esPrincipal;

    @Column(length = 60)
    private String parentesco;

    public PacienteTutor(Paciente paciente, Tutor tutor, boolean esPrincipal) {
        this.paciente = paciente;
        this.tutor = tutor;
        this.esPrincipal = esPrincipal;
    }
}
