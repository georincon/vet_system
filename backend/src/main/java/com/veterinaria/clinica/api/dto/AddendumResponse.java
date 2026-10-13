package com.veterinaria.clinica.api.dto;

import java.time.Instant;

import com.veterinaria.clinica.domain.ConsultaAddendum;
import com.veterinaria.seguridad.api.dto.VeterinarioResponse;

public record AddendumResponse(Long id, String tipo, String texto, VeterinarioResponse autor, Instant creadoEn) {

    public static AddendumResponse from(ConsultaAddendum a) {
        return new AddendumResponse(a.getId(), a.getTipo(), a.getTexto(), VeterinarioResponse.from(a.getAutor()),
                a.getCreadoEn());
    }
}
