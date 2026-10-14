/**
 * Inmunizacion: catalogos de vacunas y antiparasitarios, esquemas por especie/edad, aplicacion trazable al lote,
 * proxima dosis automatica, pendientes y carnet PDF. Depende de tutores, seguridad y del evento
 * {@code clinica.ConsultaCerrada}; ningun otro modulo de negocio lo conoce salvo dashboard y comunicaciones.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.veterinaria.inmunizacion;

import org.springframework.modulith.ApplicationModule;
