package com.veterinaria.tutores.api.dto;

import java.time.LocalDate;

import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.Sexo;

/** Vista compacta de un paciente (listas de un tutor, citas, consultas). */
public record PacienteResumen(
        Long id,
        String codigo,
        String nombre,
        String especie,
        String raza,
        Sexo sexo,
        LocalDate fechaNacimiento,
        boolean activo) {

    public static PacienteResumen from(Paciente p) {
        return new PacienteResumen(p.getId(), p.getCodigo(), p.getNombre(), p.getEspecie().getNombre(),
                p.getRaza() == null ? null : p.getRaza().getNombre(), p.getSexo(), p.getFechaNacimiento(),
                p.isActivo());
    }
}
