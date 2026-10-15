package com.veterinaria.inmunizacion.application;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inmunizacion.internal.AntiparasitarioRepository;
import com.veterinaria.inmunizacion.internal.VacunaRepository;
import com.veterinaria.inventario.ProductoClinicoResolver;

/**
 * Vinculo vacuna/antiparasitario con item de inventario. Orden: {@code item_id} explicito del catalogo y, para
 * vacunas sin vinculo, el item sembrado por convencion {@code VAC-<codigo>} (V28). Lo usan el selector de lotes
 * ({@code GET /api/inventario/lotes?vacunaId=}) y los cargos automaticos de facturacion.
 */
@Component
class ProductoClinicoResolverImpl implements ProductoClinicoResolver {

    private final VacunaRepository vacunas;
    private final AntiparasitarioRepository antiparasitarios;
    private final JdbcClient jdbc;

    ProductoClinicoResolverImpl(VacunaRepository vacunas, AntiparasitarioRepository antiparasitarios, JdbcClient jdbc) {
        this.vacunas = vacunas;
        this.antiparasitarios = antiparasitarios;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> itemIdDeVacuna(Long vacunaId) {
        return vacunas.findById(vacunaId).flatMap(v -> v.getItemId() != null ? Optional.of(v.getItemId())
                : porCodigo("VAC-" + v.getCodigo()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> itemIdDeAntiparasitario(Long antiparasitarioId) {
        return antiparasitarios.findById(antiparasitarioId).map(a -> a.getItemId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> itemIdDeVacunaPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String buscado = nombre.trim();
        return vacunas.findFirstByNombreIgnoreCase(buscado)
                .or(() -> vacunas.findByCodigo(buscado.toUpperCase(java.util.Locale.ROOT)))
                .flatMap(v -> itemIdDeVacuna(v.getId()));
    }

    private Optional<Long> porCodigo(String codigo) {
        return jdbc.sql("select id from item where codigo = :c").param("c", codigo).query(Long.class).optional();
    }
}
