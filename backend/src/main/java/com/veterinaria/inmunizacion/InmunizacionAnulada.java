package com.veterinaria.inmunizacion;

/** Evento (API del modulo): una aplicacion fue anulada; sus recordatorios pendientes ya no aplican. */
public record InmunizacionAnulada(String tipo, Long id, Long pacienteId) {
}
