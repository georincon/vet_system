package com.veterinaria.tutores.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.PacienteTutor;
import com.veterinaria.tutores.domain.Sexo;

public record PacienteResponse(
        Long id,
        String codigo,
        String nombre,
        EspecieResponse especie,
        RazaResponse raza,
        Sexo sexo,
        String estadoReproductivo,
        String color,
        LocalDate fechaNacimiento,
        String microchip,
        BigDecimal pesoActual,
        boolean esterilizado,
        boolean fallecido,
        String alertas,
        String notas,
        boolean activo,
        List<TutorResumen> tutores) {

    public static PacienteResponse from(Paciente p) {
        // El tutor principal va primero.
        List<TutorResumen> tutores = p.getTutores().stream()
                .sorted(Comparator.comparing(PacienteTutor::isEsPrincipal).reversed()
                        .thenComparing(v -> v.getTutor().getId()))
                .map(TutorResumen::from)
                .toList();
        return new PacienteResponse(p.getId(), p.getCodigo(), p.getNombre(), EspecieResponse.from(p.getEspecie()),
                p.getRaza() == null ? null : RazaResponse.from(p.getRaza()), p.getSexo(),
                p.getEstadoReproductivo(), p.getColor(), p.getFechaNacimiento(), p.getMicrochip(),
                p.getPesoActual(), p.isEsterilizado(), p.isFallecido(), p.getAlertas(), p.getNotas(),
                p.isActivo(), tutores);
    }
}
