/**
 * Infraestructura transversal (seguridad HTTP, errores, paginacion).
 * Modulo abierto: todos los demas modulos pueden usar sus tipos.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.veterinaria.shared;

import org.springframework.modulith.ApplicationModule;
