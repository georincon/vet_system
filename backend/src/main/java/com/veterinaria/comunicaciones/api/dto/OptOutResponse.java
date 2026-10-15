package com.veterinaria.comunicaciones.api.dto;

import java.time.Instant;

public record OptOutResponse(Long id, Long tutorId, String canal, String categoria, Instant desde, String motivo) {
}
