package com.veterinaria.shared.clinico;

import java.time.LocalDate;
import java.time.Period;

/**
 * Datos minimos de un paciente (y de su tutor principal) que necesitan los documentos clinicos y las reglas de
 * laboratorio (especie, sexo y edad). Lectura via SQL: no acopla los modulos con el modelo JPA de tutores.
 */
public record PacienteBasico(
        Long id,
        String codigo,
        String nombre,
        Long especieId,
        String especie,
        String raza,
        String sexo,
        LocalDate fechaNacimiento,
        String microchip,
        Long tutorId,
        String tutorNombre,
        String tutorDocumento,
        String tutorTelefono,
        String tutorEmail) {

    /** Edad en meses completos a la fecha dada; {@code null} si no se conoce el nacimiento. */
    public Integer edadMeses(LocalDate fecha) {
        if (fechaNacimiento == null || fecha.isBefore(fechaNacimiento)) {
            return null;
        }
        Period p = Period.between(fechaNacimiento, fecha);
        return p.getYears() * 12 + p.getMonths();
    }

    /** Edad legible para encabezados de documentos (p. ej. "3 a 2 m"). */
    public String edadTexto(LocalDate fecha) {
        Integer meses = edadMeses(fecha);
        if (meses == null) {
            return "-";
        }
        return meses >= 12 ? (meses / 12) + " a " + (meses % 12) + " m" : meses + " m";
    }
}
