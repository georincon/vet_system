/**
 * Comunicaciones: plantillas con variables, reglas de recordatorio, cola de recordatorios sin duplicados,
 * bitacora de envios y opt-out. Canal de salida abstracto ({@code CanalEnvio}); hoy EMAIL (SMTP o simulado).
 * Lee pendientes de {@code inmunizacion}; escucha sus eventos para cancelar recordatorios obsoletos.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.veterinaria.comunicaciones;

import org.springframework.modulith.ApplicationModule;
