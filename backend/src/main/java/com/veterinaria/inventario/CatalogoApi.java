package com.veterinaria.inventario;

import java.math.BigDecimal;
import java.util.Optional;

/** Consulta del catalogo de items/servicios para cotizar y facturar (precio vigente e IVA). */
public interface CatalogoApi {

    record ItemVenta(Long id, String codigo, String nombre, String tipo, BigDecimal precioVenta,
            BigDecimal impuestoPct, boolean controlaStock, boolean esControlado, boolean activo) {
    }

    Optional<ItemVenta> item(Long id);

    Optional<ItemVenta> itemPorCodigo(String codigo);

    /** Item activo cuyo codigo o nombre coincide (sin distinguir mayusculas), p. ej. una vacuna escrita en la consulta. */
    Optional<ItemVenta> itemPorNombreOCodigo(String texto);

    /** Precio de la sede si hay uno vigente en {@code item_precio_sede}; si no, el precio base del item. */
    BigDecimal precioVigente(Long itemId, Long sedeId);
}
