package com.veterinaria.citas.api.dto;

import java.time.LocalDateTime;

import com.veterinaria.citas.domain.BloqueoAgenda;
import com.veterinaria.seguridad.api.dto.VeterinarioResponse;
import com.veterinaria.shared.time.ZonaClinica;

public record BloqueoResponse(
        Long id,
        VeterinarioResponse veterinario,
        SalaResponse sala,
        LocalDateTime inicio,
        LocalDateTime fin,
        String tipo,
        String motivo) {

    public static BloqueoResponse from(BloqueoAgenda b) {
        return new BloqueoResponse(b.getId(),
                b.getVeterinario() == null ? null : VeterinarioResponse.from(b.getVeterinario()),
                SalaResponse.from(b.getSala()), ZonaClinica.local(b.getInicio()), ZonaClinica.local(b.getFin()),
                b.getTipo(), b.getMotivo());
    }
}
