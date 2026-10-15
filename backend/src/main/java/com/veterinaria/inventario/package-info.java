/**
 * Inventario y farmacia: catalogo de items/servicios, bodegas, lotes con vencimiento, proveedores, compras y
 * recepciones, kardex, descuento FEFO, alertas y libro de medicamentos controlados.
 *
 * <p>Modulo cerrado: los demas modulos solo usan los tipos de este paquete raiz ({@link com.veterinaria.inventario.StockApi},
 * {@link com.veterinaria.inventario.CatalogoApi}, {@link com.veterinaria.inventario.ProductoClinicoResolver}).
 * No depende de ningun otro modulo de negocio: el vinculo vacuna/antiparasitario con item lo aporta
 * {@code inmunizacion} implementando {@code ProductoClinicoResolver}.
 */
@ApplicationModule(displayName = "Inventario")
package com.veterinaria.inventario;

import org.springframework.modulith.ApplicationModule;
