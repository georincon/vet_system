package com.veterinaria.citas.api.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.veterinaria.citas.domain.ListaEspera;
import com.veterinaria.seguridad.api.dto.VeterinarioResponse;
import com.veterinaria.tutores.api.dto.PacienteResumen;

public record ListaEsperaResponse(
        Long id,
        PacienteResumen paciente,
        TutorBasico tutor,
        TipoCitaResponse tipoCita,
        VeterinarioResponse veterinario,
        LocalDate fechaDeseadaDesde,
        LocalDate fechaDeseadaHasta,
        String franjaPreferida,
        int prioridad,
        String estado,
        Long citaAsignadaId,
        String notas,
        Instant creadoEn) {

    public record TutorBasico(Long id, String nombre, String telefono) {
    }

    public static ListaEsperaResponse from(ListaEspera l) {
        var t = l.getTutor();
        String nombre = t.getNombres() + (t.getApellidos() == null ? "" : " " + t.getApellidos());
        return new ListaEsperaResponse(l.getId(), PacienteResumen.from(l.getPaciente()),
                new TutorBasico(t.getId(), nombre, t.getTelefono()), TipoCitaResponse.from(l.getTipoCita()),
                l.getVeterinario() == null ? null : VeterinarioResponse.from(l.getVeterinario()),
                l.getFechaDeseadaDesde(), l.getFechaDeseadaHasta(), l.getFranjaPreferida(), l.getPrioridad(),
                l.getEstado(), l.getCitaAsignadaId(), l.getNotas(), l.getCreadoEn());
    }
}
