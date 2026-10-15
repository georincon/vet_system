/**
 * Archivos clinicos: almacenamiento seguro en disco (nombre aleatorio, validacion por magic bytes, hash SHA-256,
 * miniaturas) sobre la tabla {@code adjunto}, con baja logica y trazabilidad de lecturas.
 *
 * <p>Modulo cerrado: los demas modulos solo usan {@link com.veterinaria.archivos.ArchivosApi} y
 * {@link com.veterinaria.archivos.TipoArchivo}. No depende de ningun modulo de negocio.
 */
@ApplicationModule(displayName = "Archivos")
package com.veterinaria.archivos;

import org.springframework.modulith.ApplicationModule;
