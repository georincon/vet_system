package com.veterinaria.tutores.api.dto;

import com.veterinaria.tutores.domain.PacienteTutor;
import com.veterinaria.tutores.domain.Tutor;

/** Tutor tal como aparece dentro de un paciente. */
public record TutorResumen(
        Long id,
        String nombres,
        String apellidos,
        String telefono,
        String email,
        boolean esPrincipal,
        String parentesco) {

    public static TutorResumen from(PacienteTutor vinculo) {
        Tutor t = vinculo.getTutor();
        return new TutorResumen(t.getId(), t.getNombres(), t.getApellidos(), t.getTelefono(), t.getEmail(),
                vinculo.isEsPrincipal(), vinculo.getParentesco());
    }
}
