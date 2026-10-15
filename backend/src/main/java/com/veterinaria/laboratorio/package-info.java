/**
 * Laboratorio, imagenologia y patologia (orden -> muestra -> resultado -> validacion, como en salud humana):
 * catalogo de examenes con paneles, parametros (LOINC/UCUM) y rangos por especie/sexo/edad; ordenes con bandeja y
 * bitacora; captura con bandera automatica y snapshot del rango; validacion e inmutabilidad con correccion enlazada;
 * importacion HL7 v2 / CSV / JSON (analizadores y LIS con API key); tendencias; informes estructurados de imagen y
 * patologia; e informes PDF.
 *
 * <p>Modulo cerrado. Depende de {@code archivos} (solo {@code ArchivosApi}) y de {@code shared}; lee paciente,
 * consulta y usuarios por SQL. El resto del sistema lo ve solo a traves de las tablas (linea de tiempo de clinica).
 */
@ApplicationModule(displayName = "Laboratorio")
package com.veterinaria.laboratorio;

import org.springframework.modulith.ApplicationModule;
