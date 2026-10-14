package com.veterinaria.inmunizacion.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Aplicacion de vacuna o antiparasitario; {@code (tipo, id)} es la clave (los ids de cada tabla son independientes). */
public record InmunizacionResponse(
        String tipo,
        Long id,
        Long pacienteId,
        Long productoId,
        String producto,
        String codigoProducto,
        String clasificacion,
        Integer dosisNumero,
        String dosisNombre,
        String dosis,
        LocalDate fechaAplicacion,
        LocalDate proximaFecha,
        Long loteId,
        String lote,
        LocalDate fechaVencimientoLote,
        Persona veterinario,
        String sitioAplicacion,
        String viaAdministracion,
        BigDecimal pesoKg,
        String laboratorio,
        String observaciones,
        String reaccionAdversa,
        boolean externa,
        Long consultaId,
        String estado,
        String motivoAnulacion,
        Instant anuladoEn) {

    public record Persona(Long id, String nombre) {
    }
}
