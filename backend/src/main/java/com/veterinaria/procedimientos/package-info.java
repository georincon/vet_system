/**
 * Procedimientos ambulatorios y cirugias: registro clinico (tecnica, anestesia, hallazgos, complicaciones, insumos,
 * resultado, cuidados y controles), monitoreo anestesico, consentimiento informado con archivo firmado opcional y
 * reporte quirurgico en PDF. Usa las tablas {@code cirugia}, {@code cirugia_monitoreo_anestesico}, {@code cirugia_insumo}
 * y {@code consentimiento}.
 *
 * <p>Modulo cerrado. Depende de {@code archivos} (solo {@code ArchivosApi}) y de {@code shared}.
 */
@ApplicationModule(displayName = "Procedimientos")
package com.veterinaria.procedimientos;

import org.springframework.modulith.ApplicationModule;
