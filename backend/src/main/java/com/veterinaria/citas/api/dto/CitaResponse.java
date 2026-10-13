package com.veterinaria.citas.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.veterinaria.citas.domain.Cita;
import com.veterinaria.seguridad.api.dto.UsuarioResponse;
import com.veterinaria.tutores.api.dto.PacienteResumen;

public record CitaResponse(
        Long id,
        String codigo,
        PacienteResumen paciente,
        UsuarioResponse veterinario,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        EstadoCitaResponse estadoCita,
        TipoCitaResponse tipoCita,
        SalaResponse sala,
        String motivo,
        String notas,
        Instant horaLlegada,
        Instant atencionInicio,
        boolean activo) {

    public static CitaResponse from(Cita c) {
        return new CitaResponse(c.getId(), c.getCodigo(), PacienteResumen.from(c.getPaciente()),
                UsuarioResponse.from(c.getVeterinario()), c.getFecha(), c.getHoraInicio(), c.getHoraFin(),
                EstadoCitaResponse.from(c.getEstadoCita()), TipoCitaResponse.from(c.getTipoCita()),
                SalaResponse.from(c.getSala()), c.getMotivo(), c.getNotas(), c.getHoraLlegada(),
                c.getAtencionInicio(), c.isActivo());
    }
}
