package com.veterinaria.citas.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Contenido del calendario para el rango pedido: citas (con su estado) y bloqueos. */
public record AgendaResponse(
        LocalDate desde,
        LocalDate hasta,
        List<CitaResponse> citas,
        List<BloqueoResponse> bloqueos) {
}
