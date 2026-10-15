package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.CatalogoApi;
import com.veterinaria.inventario.api.dto.ItemDtos.BodegaRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.BodegaResponse;
import com.veterinaria.inventario.api.dto.ItemDtos.CategoriaRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.CategoriaResponse;
import com.veterinaria.inventario.api.dto.ItemDtos.ItemRequest;
import com.veterinaria.inventario.api.dto.ItemDtos.ItemResponse;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/** Catalogo de items/servicios, categorias y bodegas (escritura solo ADMIN, controlado en la capa HTTP). */
@Service
public class ItemService implements CatalogoApi {

    private static final String BASE = """
            select i.*, c.nombre as categoria,
                   coalesce((select sum(l.cantidad_actual) from lote l where l.item_id = i.id and l.estado = 'DISPONIBLE'
                             and (l.fecha_vencimiento is null or l.fecha_vencimiento >= :hoy)), 0) as stock_vigente,
                   coalesce((select sum(l.cantidad_actual) from lote l where l.item_id = i.id), 0) as stock_total
            from item i left join categoria_item c on c.id = i.categoria_id
            """;

    private final JdbcClient jdbc;
    private final SedePrincipal sedePrincipal;

    public ItemService(JdbcClient jdbc, SedePrincipal sedePrincipal) {
        this.jdbc = jdbc;
        this.sedePrincipal = sedePrincipal;
    }

    // ------------------------------------------------------------------ items

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listar(String q, String tipo, Long categoriaId, Boolean controlaStock,
            Boolean esControlado, Boolean activo, Boolean bajoMinimo, Pageable pageable) {
        Filtro f = new Filtro().param("hoy", ZonaClinica.hoy());
        if (q != null && !q.isBlank()) {
            f.y("(t.nombre ilike :q or t.codigo ilike :q or t.codigo_barras = :qx or t.principio_activo ilike :q)",
                    "q", "%" + q.trim() + "%").param("qx", q.trim());
        }
        f.si(tipo, "t.tipo = :tipo", "tipo").si(categoriaId, "t.categoria_id = :cat", "cat")
                .si(controlaStock, "t.controla_stock = :cs", "cs").si(esControlado, "t.es_controlado = :ec", "ec")
                .si(activo, "t.activo = :activo", "activo");
        if (bajoMinimo != null) {
            f.y((bajoMinimo ? "" : "not ") + "(t.controla_stock and t.activo and t.stock_minimo > 0 and t.stock_vigente < t.stock_minimo)");
        }
        String filtrado = "select * from (" + BASE + ") t" + f.sql();
        long total = jdbc.sql("select count(*) from (" + filtrado + ") x").params(f.params()).query(Long.class).single();
        List<ItemResponse> items = jdbc.sql(filtrado + " order by t.nombre, t.id limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> aItem(rs)).list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public ItemResponse obtener(Long id) {
        return jdbc.sql(BASE + " where i.id = :id").param("hoy", ZonaClinica.hoy()).param("id", id)
                .query((rs, n) -> aItem(rs)).optional().orElseThrow(() -> new ResourceNotFoundException("Item", id));
    }

    @Transactional
    public ItemResponse crear(ItemRequest r) {
        String codigo = Textos.limpiar(r.codigo());
        if (jdbc.sql("select count(*) from item where lower(codigo) = lower(:c)").param("c", codigo)
                .query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe un item con el codigo " + codigo);
        }
        boolean controlaStock = Boolean.TRUE.equals(r.controlaStock());
        boolean controlado = Boolean.TRUE.equals(r.esControlado());
        validarCoherencia(r.tipo(), controlaStock, controlado);
        validarReferencias(r.categoriaId(), r.proveedorPreferidoId());
        Long id = jdbc.sql("""
                insert into item (codigo, nombre, tipo, categoria_id, precio_venta, costo, impuesto_pct, controla_stock,
                                  activo, stock_minimo, unidad_medida, presentacion, marca, principio_activo, concentracion,
                                  via_administracion, registro_ica, es_controlado, requiere_formula, codigo_barras,
                                  tiene_vencimiento, proveedor_preferido_id, requiere_veterinario, duracion_minutos,
                                  descripcion)
                values (:codigo, :nombre, :tipo, :cat, :precio, :costo, :iva, :cs, :activo, :min, :um, :pres, :marca, :pa,
                        :conc, :via, :ica, :ctrl, :formula, :barras, :venc, :prov, :vet, :dur, :desc)
                returning id
                """).param("codigo", codigo).param("nombre", Textos.limpiar(r.nombre())).param("tipo", r.tipo())
                .param("cat", r.categoriaId()).param("precio", nvl(r.precioVenta())).param("costo", nvl(r.costo()))
                .param("iva", nvl(r.impuestoPct())).param("cs", controlaStock)
                .param("activo", r.activo() == null || r.activo()).param("min", nvl(r.stockMinimo()))
                .param("um", Textos.limpiar(r.unidadMedida())).param("pres", Textos.limpiar(r.presentacion()))
                .param("marca", Textos.limpiar(r.marca())).param("pa", Textos.limpiar(r.principioActivo()))
                .param("conc", Textos.limpiar(r.concentracion())).param("via", Textos.limpiar(r.viaAdministracion()))
                .param("ica", Textos.limpiar(r.registroIca())).param("ctrl", controlado)
                .param("formula", Boolean.TRUE.equals(r.requiereFormula()) || controlado)
                .param("barras", Textos.limpiar(r.codigoBarras()))
                .param("venc", r.tieneVencimiento() == null || r.tieneVencimiento())
                .param("prov", r.proveedorPreferidoId()).param("vet", Boolean.TRUE.equals(r.requiereVeterinario()))
                .param("dur", r.duracionMinutos()).param("desc", Textos.limpiar(r.descripcion()))
                .query(Long.class).single();
        return obtener(id);
    }

    @Transactional
    public ItemResponse actualizar(Long id, ItemRequest r) {
        ItemResponse a = obtener(id);
        String codigo = r.codigo() == null ? a.codigo() : Textos.limpiar(r.codigo());
        if (codigo == null) {
            throw new BusinessException("El codigo no puede quedar vacio");
        }
        if (!codigo.equalsIgnoreCase(a.codigo()) && jdbc.sql("select count(*) from item where lower(codigo) = lower(:c) and id <> :id")
                .param("c", codigo).param("id", id).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe un item con el codigo " + codigo);
        }
        String nombre = r.nombre() == null ? a.nombre() : Textos.limpiar(r.nombre());
        if (nombre == null) {
            throw new BusinessException("El nombre no puede quedar vacio");
        }
        String tipo = r.tipo() != null ? r.tipo() : a.tipo();
        boolean controlaStock = r.controlaStock() != null ? r.controlaStock() : a.controlaStock();
        boolean controlado = r.esControlado() != null ? r.esControlado() : a.esControlado();
        validarCoherencia(tipo, controlaStock, controlado);
        Long categoriaId = r.categoriaId() != null ? r.categoriaId() : a.categoriaId();
        Long proveedor = r.proveedorPreferidoId() != null ? r.proveedorPreferidoId() : a.proveedorPreferidoId();
        validarReferencias(categoriaId, proveedor);
        jdbc.sql("""
                update item set codigo = :codigo, nombre = :nombre, tipo = :tipo, categoria_id = :cat,
                       precio_venta = :precio, costo = :costo, impuesto_pct = :iva, controla_stock = :cs, activo = :activo,
                       stock_minimo = :min, unidad_medida = :um, presentacion = :pres, marca = :marca,
                       principio_activo = :pa, concentracion = :conc, via_administracion = :via, registro_ica = :ica,
                       es_controlado = :ctrl, requiere_formula = :formula, codigo_barras = :barras,
                       tiene_vencimiento = :venc, proveedor_preferido_id = :prov, requiere_veterinario = :vet,
                       duracion_minutos = :dur, descripcion = :desc, actualizado_en = now()
                where id = :id
                """).param("id", id).param("codigo", codigo).param("nombre", nombre).param("tipo", tipo)
                .param("cat", categoriaId).param("precio", r.precioVenta() != null ? r.precioVenta() : a.precioVenta())
                .param("costo", r.costo() != null ? r.costo() : costoActual(id))
                .param("iva", r.impuestoPct() != null ? r.impuestoPct() : a.impuestoPct()).param("cs", controlaStock)
                .param("activo", r.activo() != null ? r.activo() : a.activo())
                .param("min", r.stockMinimo() != null ? r.stockMinimo() : a.stockMinimo())
                .param("um", Textos.actualizar(a.unidadMedida(), r.unidadMedida()))
                .param("pres", Textos.actualizar(a.presentacion(), r.presentacion()))
                .param("marca", Textos.actualizar(a.marca(), r.marca()))
                .param("pa", Textos.actualizar(a.principioActivo(), r.principioActivo()))
                .param("conc", Textos.actualizar(a.concentracion(), r.concentracion()))
                .param("via", Textos.actualizar(a.viaAdministracion(), r.viaAdministracion()))
                .param("ica", Textos.actualizar(a.registroIca(), r.registroIca())).param("ctrl", controlado)
                .param("formula", r.requiereFormula() != null ? r.requiereFormula() : (a.requiereFormula() || controlado))
                .param("barras", Textos.actualizar(a.codigoBarras(), r.codigoBarras()))
                .param("venc", r.tieneVencimiento() != null ? r.tieneVencimiento() : a.tieneVencimiento())
                .param("prov", proveedor)
                .param("vet", r.requiereVeterinario() != null ? r.requiereVeterinario() : a.requiereVeterinario())
                .param("dur", r.duracionMinutos() != null ? r.duracionMinutos() : a.duracionMinutos())
                .param("desc", Textos.actualizar(a.descripcion(), r.descripcion())).update();
        return obtener(id);
    }

    private BigDecimal costoActual(Long id) {
        return jdbc.sql("select costo from item where id = :id").param("id", id).query(BigDecimal.class).single();
    }

    private void validarCoherencia(String tipo, boolean controlaStock, boolean controlado) {
        boolean fisico = tipo.equals("MEDICAMENTO") || tipo.equals("PRODUCTO") || tipo.equals("ALIMENTO");
        if (controlaStock && !fisico) {
            throw new BusinessException("Solo los medicamentos, productos y alimentos controlan stock");
        }
        if (controlado && !controlaStock) {
            throw new BusinessException("Un medicamento controlado debe controlar stock (libro de controlados)");
        }
    }

    private void validarReferencias(Long categoriaId, Long proveedorId) {
        if (categoriaId != null && jdbc.sql("select count(*) from categoria_item where id = :id").param("id", categoriaId)
                .query(Long.class).single() == 0) {
            throw new BusinessException("La categoria " + categoriaId + " no existe");
        }
        if (proveedorId != null && jdbc.sql("select count(*) from proveedor where id = :id").param("id", proveedorId)
                .query(Long.class).single() == 0) {
            throw new BusinessException("El proveedor " + proveedorId + " no existe");
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private ItemResponse aItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal vigente = rs.getBigDecimal("stock_vigente");
        BigDecimal minimo = rs.getBigDecimal("stock_minimo");
        boolean controla = rs.getBoolean("controla_stock");
        boolean activo = rs.getBoolean("activo");
        return new ItemResponse(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"),
                (Long) rs.getObject("categoria_id"), rs.getString("categoria"), rs.getBigDecimal("precio_venta"),
                Visibilidad.veCostos() ? rs.getBigDecimal("costo") : null, rs.getBigDecimal("impuesto_pct"), controla,
                activo, minimo, controla ? vigente : null, controla ? rs.getBigDecimal("stock_total") : null,
                controla && activo && minimo.signum() > 0 && vigente.compareTo(minimo) < 0,
                rs.getString("unidad_medida"), rs.getString("presentacion"), rs.getString("marca"),
                rs.getString("principio_activo"), rs.getString("concentracion"), rs.getString("via_administracion"),
                rs.getString("registro_ica"), rs.getBoolean("es_controlado"), rs.getBoolean("requiere_formula"),
                rs.getString("codigo_barras"), rs.getBoolean("tiene_vencimiento"),
                (Long) rs.getObject("proveedor_preferido_id"), rs.getBoolean("requiere_veterinario"),
                (Integer) rs.getObject("duracion_minutos"), rs.getString("descripcion"));
    }

    // ------------------------------------------------------------------ categorias

    @Transactional(readOnly = true)
    public List<CategoriaResponse> categorias(String tipo, Boolean activo) {
        Filtro f = new Filtro().si(tipo, "tipo = :tipo", "tipo").si(activo, "activo = :activo", "activo");
        return jdbc.sql("select id, nombre, tipo, activo from categoria_item" + f.sql() + " order by nombre")
                .params(f.params()).query((rs, n) -> new CategoriaResponse(rs.getLong("id"), rs.getString("nombre"),
                        rs.getString("tipo"), rs.getBoolean("activo"))).list();
    }

    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest r) {
        String nombre = Textos.limpiar(r.nombre());
        if (jdbc.sql("select count(*) from categoria_item where lower(nombre) = lower(:n)").param("n", nombre)
                .query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una categoria llamada " + nombre);
        }
        Long id = jdbc.sql("insert into categoria_item (nombre, tipo, activo) values (:n, :t, :a) returning id")
                .param("n", nombre).param("t", r.tipo()).param("a", r.activo() == null || r.activo())
                .query(Long.class).single();
        return categoria(id);
    }

    @Transactional
    public CategoriaResponse actualizarCategoria(Long id, CategoriaRequest r) {
        CategoriaResponse a = categoria(id);
        String nombre = r.nombre() == null ? a.nombre() : Textos.limpiar(r.nombre());
        if (nombre == null) {
            throw new BusinessException("El nombre no puede quedar vacio");
        }
        if (jdbc.sql("select count(*) from categoria_item where lower(nombre) = lower(:n) and id <> :id")
                .param("n", nombre).param("id", id).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una categoria llamada " + nombre);
        }
        jdbc.sql("update categoria_item set nombre = :n, tipo = :t, activo = :a where id = :id").param("id", id)
                .param("n", nombre).param("t", r.tipo() != null ? r.tipo() : a.tipo())
                .param("a", r.activo() != null ? r.activo() : a.activo()).update();
        return categoria(id);
    }

    private CategoriaResponse categoria(Long id) {
        return jdbc.sql("select id, nombre, tipo, activo from categoria_item where id = :id").param("id", id)
                .query((rs, n) -> new CategoriaResponse(rs.getLong("id"), rs.getString("nombre"), rs.getString("tipo"),
                        rs.getBoolean("activo"))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
    }

    // ------------------------------------------------------------------ bodegas

    @Transactional(readOnly = true)
    public List<BodegaResponse> bodegas(Boolean activo) {
        Filtro f = new Filtro().si(activo, "activo = :activo", "activo");
        return jdbc.sql("select id, nombre, tipo, activo, sede_id from bodega" + f.sql() + " order by nombre")
                .params(f.params()).query((rs, n) -> aBodega(rs)).list();
    }

    @Transactional
    public BodegaResponse crearBodega(BodegaRequest r) {
        String nombre = Textos.limpiar(r.nombre());
        if (jdbc.sql("select count(*) from bodega where lower(nombre) = lower(:n)").param("n", nombre)
                .query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una bodega llamada " + nombre);
        }
        Long id = jdbc.sql("insert into bodega (nombre, tipo, activo, sede_id) values (:n, :t, :a, :s) returning id")
                .param("n", nombre).param("t", r.tipo() == null ? "PRINCIPAL" : r.tipo())
                .param("a", r.activo() == null || r.activo()).param("s", sedePrincipal.id()).query(Long.class).single();
        return bodega(id);
    }

    @Transactional
    public BodegaResponse actualizarBodega(Long id, BodegaRequest r) {
        BodegaResponse a = bodega(id);
        String nombre = r.nombre() == null ? a.nombre() : Textos.limpiar(r.nombre());
        if (nombre == null) {
            throw new BusinessException("El nombre no puede quedar vacio");
        }
        if (jdbc.sql("select count(*) from bodega where lower(nombre) = lower(:n) and id <> :id").param("n", nombre)
                .param("id", id).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una bodega llamada " + nombre);
        }
        boolean activo = r.activo() != null ? r.activo() : a.activo();
        if (!activo && jdbc.sql("select count(*) from lote where bodega_id = :id and cantidad_actual > 0").param("id", id)
                .query(Long.class).single() > 0) {
            throw new ConflictException("La bodega tiene existencias: trasladelas o ajustelas antes de desactivarla");
        }
        jdbc.sql("update bodega set nombre = :n, tipo = :t, activo = :a where id = :id").param("id", id)
                .param("n", nombre).param("t", r.tipo() != null ? r.tipo() : a.tipo()).param("a", activo).update();
        return bodega(id);
    }

    private BodegaResponse bodega(Long id) {
        return jdbc.sql("select id, nombre, tipo, activo, sede_id from bodega where id = :id").param("id", id)
                .query((rs, n) -> aBodega(rs)).optional().orElseThrow(() -> new ResourceNotFoundException("Bodega", id));
    }

    private static BodegaResponse aBodega(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BodegaResponse(rs.getLong("id"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getBoolean("activo"), (Long) rs.getObject("sede_id"));
    }

    // ------------------------------------------------------------------ CatalogoApi

    private static final String VENTA = "select id, codigo, nombre, tipo, precio_venta, impuesto_pct, controla_stock, es_controlado, activo from item";

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemVenta> item(Long id) {
        return jdbc.sql(VENTA + " where id = :id").param("id", id).query((rs, n) -> aVenta(rs)).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemVenta> itemPorCodigo(String codigo) {
        return jdbc.sql(VENTA + " where codigo = :c").param("c", codigo).query((rs, n) -> aVenta(rs)).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemVenta> itemPorNombreOCodigo(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql(VENTA + " where activo and (lower(nombre) = lower(:t) or lower(codigo) = lower(:t)) order by id limit 1")
                .param("t", texto.trim()).query((rs, n) -> aVenta(rs)).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal precioVigente(Long itemId, Long sedeId) {
        return jdbc.sql("""
                select coalesce((select precio_venta from item_precio_sede where item_id = :item and sede_id = :sede
                                  and vigente_desde <= :hoy order by vigente_desde desc limit 1),
                                (select precio_venta from item where id = :item))
                """).param("item", itemId).param("sede", sedeId).param("hoy", ZonaClinica.hoy())
                .query(BigDecimal.class).single();
    }

    private static ItemVenta aVenta(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ItemVenta(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getBigDecimal("precio_venta"), rs.getBigDecimal("impuesto_pct"), rs.getBoolean("controla_stock"),
                rs.getBoolean("es_controlado"), rs.getBoolean("activo"));
    }
}
