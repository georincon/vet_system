package com.veterinaria.inventario;

import java.util.Optional;

/**
 * Puerto que implementa {@code inmunizacion}: traduce vacuna/antiparasitario del catalogo clinico al item de
 * inventario (por {@code item_id} o por convencion de codigo {@code VAC-<codigo>}). Asi inventario y facturacion
 * no dependen de ese modulo (sin ciclos).
 */
public interface ProductoClinicoResolver {

    Optional<Long> itemIdDeVacuna(Long vacunaId);

    Optional<Long> itemIdDeAntiparasitario(Long antiparasitarioId);

    /** Vacuna cuyo nombre o codigo coincide con el texto libre escrito en una consulta. */
    Optional<Long> itemIdDeVacunaPorNombre(String nombre);
}
