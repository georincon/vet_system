package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.api.dto.CompraDtos.OrdenItemRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.OrdenItemResponse;
import com.veterinaria.inventario.api.dto.CompraDtos.OrdenRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.OrdenResponse;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionItemRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionItemResponse;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.RecepcionResponse;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Ordenes de compra (BORRADOR -> ENVIADA -> PARCIAL -> RECIBIDA | CANCELADA) y recepciones: cada renglon recibido crea
 * (o suma a) un lote con su movimiento ENTRADA, costo y trazabilidad a la recepcion. Solo ADMIN.
 */
@Service
public class CompraService {

    private final JdbcClient jdbc;
    private final LoteService lotes;
    private final SedePrincipal sedePrincipal;

    public CompraService(JdbcClient jdbc, LoteService lotes, SedePrincipal sedePrincipal) {
        this.jdbc = jdbc;
        this.lotes = lotes;
        this.sedePrincipal = sedePrincipal;
    }

    // ------------------------------------------------------------------ ordenes

    @Transactional(readOnly = true)
    public PageResponse<OrdenResponse> ordenes(Long proveedorId, String estado, Pageable pageable) {
        Filtro f = new Filtro().si(proveedorId, "o.proveedor_id = :prov", "prov").si(estado, "o.estado = :estado", "estado");
        long total = jdbc.sql("select count(*) from orden_compra o" + f.sql()).params(f.params()).query(Long.class).single();
        List<Long> ids = jdbc.sql("select o.id from orden_compra o" + f.sql() + " order by o.id desc limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query(Long.class).list();
        List<OrdenResponse> items = ids.stream().map(this::orden).toList();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public OrdenResponse orden(Long id) {
        record Cab(Long id, String codigo, Long proveedorId, String proveedor, Long bodegaId, String bodega, LocalDate fecha,
                LocalDate esperada, String estado, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total, String notas) {
        }
        Cab c = jdbc.sql("""
                select o.*, p.razon_social, b.nombre as bodega from orden_compra o join proveedor p on p.id = o.proveedor_id
                left join bodega b on b.id = o.bodega_destino_id where o.id = :id
                """).param("id", id).query((rs, n) -> new Cab(rs.getLong("id"), rs.getString("codigo"),
                        rs.getLong("proveedor_id"), rs.getString("razon_social"), (Long) rs.getObject("bodega_destino_id"),
                        rs.getString("bodega"), rs.getDate("fecha").toLocalDate(),
                        rs.getDate("fecha_esperada") == null ? null : rs.getDate("fecha_esperada").toLocalDate(),
                        rs.getString("estado"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("impuestos"),
                        rs.getBigDecimal("total"), rs.getString("notas"))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));
        List<OrdenItemResponse> items = jdbc.sql("""
                select oi.*, i.codigo, i.nombre from orden_compra_item oi join item i on i.id = oi.item_id
                where oi.orden_compra_id = :id order by oi.id
                """).param("id", id).query((rs, n) -> new OrdenItemResponse(rs.getLong("id"), rs.getLong("item_id"),
                        rs.getString("codigo"), rs.getString("nombre"), rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("cantidad_recibida"),
                        rs.getBigDecimal("cantidad").subtract(rs.getBigDecimal("cantidad_recibida")),
                        rs.getBigDecimal("costo_unitario"), rs.getBigDecimal("impuesto_pct"))).list();
        return new OrdenResponse(c.id(), c.codigo(), c.proveedorId(), c.proveedor(), c.bodegaId(), c.bodega(), c.fecha(),
                c.esperada(), c.estado(), c.subtotal(), c.impuestos(), c.total(), c.notas(), items);
    }

    @Transactional
    public OrdenResponse crearOrden(OrdenRequest r) {
        validarProveedor(r.proveedorId());
        if (r.bodegaDestinoId() != null) {
            validarBodega(r.bodegaDestinoId());
        }
        Long id = jdbc.sql("""
                insert into orden_compra (codigo, proveedor_id, sede_id, bodega_destino_id, fecha_esperada, notas, creado_por)
                values ('OC-' || lpad(nextval('seq_orden_compra')::text, 6, '0'), :prov, :sede, :bod, :esp, :notas, :uid)
                returning id
                """).param("prov", r.proveedorId()).param("sede", sedePrincipal.id()).param("bod", r.bodegaDestinoId())
                .param("esp", r.fechaEsperada()).param("notas", Textos.limpiar(r.notas()))
                .param("uid", UsuarioActual.id().orElse(null)).query(Long.class).single();
        guardarItems(id, r.items());
        return orden(id);
    }

    @Transactional
    public OrdenResponse actualizarOrden(Long id, OrdenRequest r) {
        OrdenResponse a = orden(id);
        if (!"BORRADOR".equals(a.estado())) {
            throw new ConflictException("Solo se edita una orden en BORRADOR (estado actual: " + a.estado() + ")");
        }
        Long prov = r.proveedorId() != null ? r.proveedorId() : a.proveedorId();
        validarProveedor(prov);
        Long bod = r.bodegaDestinoId() != null ? r.bodegaDestinoId() : a.bodegaDestinoId();
        if (bod != null) {
            validarBodega(bod);
        }
        jdbc.sql("update orden_compra set proveedor_id = :p, bodega_destino_id = :b, fecha_esperada = :e, notas = :n, actualizado_en = now() where id = :id")
                .param("id", id).param("p", prov).param("b", bod)
                .param("e", r.fechaEsperada() != null ? r.fechaEsperada() : a.fechaEsperada())
                .param("n", Textos.actualizar(a.notas(), r.notas())).update();
        if (r.items() != null) {
            jdbc.sql("delete from orden_compra_item where orden_compra_id = :id").param("id", id).update();
            guardarItems(id, r.items());
        }
        return orden(id);
    }

    @Transactional
    public OrdenResponse enviar(Long id) {
        OrdenResponse a = orden(id);
        if (!"BORRADOR".equals(a.estado())) {
            throw new ConflictException("La orden esta " + a.estado() + "; solo se envia una en BORRADOR");
        }
        if (a.items().isEmpty()) {
            throw new BusinessException("La orden no tiene items");
        }
        jdbc.sql("update orden_compra set estado = 'ENVIADA', actualizado_en = now() where id = :id").param("id", id).update();
        return orden(id);
    }

    @Transactional
    public OrdenResponse cancelar(Long id) {
        OrdenResponse a = orden(id);
        if (!List.of("BORRADOR", "ENVIADA").contains(a.estado())) {
            throw new ConflictException("No se puede cancelar una orden " + a.estado());
        }
        jdbc.sql("update orden_compra set estado = 'CANCELADA', actualizado_en = now() where id = :id").param("id", id).update();
        return orden(id);
    }

    private void guardarItems(Long ordenId, List<OrdenItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("La orden necesita al menos un item");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        List<Long> vistos = new ArrayList<>();
        for (OrdenItemRequest it : items) {
            if (vistos.contains(it.itemId())) {
                throw new BusinessException("El item " + it.itemId() + " esta repetido en la orden");
            }
            vistos.add(it.itemId());
            boolean controla = jdbc.sql("select controla_stock from item where id = :id and activo").param("id", it.itemId())
                    .query(Boolean.class).optional().orElseThrow(() -> new BusinessException("El item " + it.itemId()
                            + " no existe o esta inactivo"));
            if (!controla) {
                throw new BusinessException("El item " + it.itemId() + " no controla stock: no se compra por inventario");
            }
            BigDecimal pct = it.impuestoPct() == null ? BigDecimal.ZERO : it.impuestoPct();
            jdbc.sql("insert into orden_compra_item (orden_compra_id, item_id, cantidad, costo_unitario, impuesto_pct) values (:o, :i, :c, :cu, :p)")
                    .param("o", ordenId).param("i", it.itemId()).param("c", it.cantidad()).param("cu", it.costoUnitario())
                    .param("p", pct).update();
            BigDecimal base = dinero(it.cantidad().multiply(it.costoUnitario()));
            subtotal = subtotal.add(base);
            impuestos = impuestos.add(dinero(base.multiply(pct).divide(BigDecimal.valueOf(100))));
        }
        jdbc.sql("update orden_compra set subtotal = :s, impuestos = :i, total = :t where id = :id").param("s", subtotal)
                .param("i", impuestos).param("t", subtotal.add(impuestos)).param("id", ordenId).update();
    }

    // ------------------------------------------------------------------ recepciones

    @Transactional(readOnly = true)
    public PageResponse<RecepcionResponse> recepciones(Long proveedorId, Long ordenCompraId, Pageable pageable) {
        Filtro f = new Filtro().si(proveedorId, "r.proveedor_id = :prov", "prov").si(ordenCompraId, "r.orden_compra_id = :oc", "oc");
        long total = jdbc.sql("select count(*) from recepcion_compra r" + f.sql()).params(f.params()).query(Long.class).single();
        List<Long> ids = jdbc.sql("select r.id from recepcion_compra r" + f.sql() + " order by r.id desc limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query(Long.class).list();
        return PageResponse.from(new PageImpl<>(ids.stream().map(this::recepcion).toList(), pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public RecepcionResponse recepcion(Long id) {
        RecepcionResponse base = jdbc.sql("""
                select r.*, p.razon_social, b.nombre as bodega, o.estado as orden_estado
                from recepcion_compra r join proveedor p on p.id = r.proveedor_id join bodega b on b.id = r.bodega_id
                left join orden_compra o on o.id = r.orden_compra_id where r.id = :id
                """).param("id", id).query((rs, n) -> new RecepcionResponse(rs.getLong("id"),
                        (Long) rs.getObject("orden_compra_id"), rs.getString("orden_estado"), rs.getLong("proveedor_id"),
                        rs.getString("razon_social"), rs.getLong("bodega_id"), rs.getString("bodega"),
                        rs.getString("numero_factura_proveedor"), rs.getDate("fecha").toLocalDate(),
                        rs.getBigDecimal("total"),
                        rs.getDate("fecha_vencimiento_pago") == null ? null : rs.getDate("fecha_vencimiento_pago").toLocalDate(),
                        rs.getString("estado_pago"), rs.getTimestamp("creado_en").toInstant(), rs.getString("notas"), null))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Recepcion", id));
        List<RecepcionItemResponse> items = jdbc.sql("""
                select ri.*, i.codigo, i.nombre, l.numero_lote, l.fecha_vencimiento
                from recepcion_compra_item ri join item i on i.id = ri.item_id left join lote l on l.id = ri.lote_id
                where ri.recepcion_id = :id order by ri.id
                """).param("id", id).query((rs, n) -> new RecepcionItemResponse(rs.getLong("id"), rs.getLong("item_id"),
                        rs.getString("codigo"), rs.getString("nombre"), (Long) rs.getObject("lote_id"),
                        rs.getString("numero_lote"),
                        rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate(),
                        rs.getBigDecimal("cantidad"), Visibilidad.veCostos() ? rs.getBigDecimal("costo_unitario") : null))
                .list();
        return new RecepcionResponse(base.id(), base.ordenCompraId(), base.ordenEstado(), base.proveedorId(),
                base.proveedor(), base.bodegaId(), base.bodega(), base.numeroFacturaProveedor(), base.fecha(), base.total(),
                base.fechaVencimientoPago(), base.estadoPago(), base.creadoEn(), base.notas(), items);
    }

    /** Registra la recepcion, crea lotes y entradas y avanza el estado de la orden. */
    @Transactional
    public RecepcionResponse recibir(RecepcionRequest r) {
        record OrdenRow(Long proveedorId, Long bodegaId, String estado) {
        }
        OrdenRow orden = null;
        if (r.ordenCompraId() != null) {
            orden = jdbc.sql("select proveedor_id, bodega_destino_id, estado from orden_compra where id = :id for update")
                    .param("id", r.ordenCompraId()).query((rs, n) -> new OrdenRow(rs.getLong(1), (Long) rs.getObject(2),
                            rs.getString(3))).optional()
                    .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", r.ordenCompraId()));
            if (!List.of("ENVIADA", "PARCIAL").contains(orden.estado())) {
                throw new ConflictException("La orden esta " + orden.estado() + ": solo se recibe una orden ENVIADA o PARCIAL");
            }
            if (r.proveedorId() != null && !r.proveedorId().equals(orden.proveedorId())) {
                throw new BusinessException("El proveedor no coincide con el de la orden");
            }
        }
        Long proveedorId = orden != null ? orden.proveedorId() : r.proveedorId();
        if (proveedorId == null) {
            throw new BusinessException("Indique ordenCompraId o proveedorId");
        }
        validarProveedor(proveedorId);
        Long bodegaId = r.bodegaId() != null ? r.bodegaId() : (orden != null ? orden.bodegaId() : null);
        if (bodegaId == null) {
            bodegaId = jdbc.sql("select id from bodega where activo order by (tipo = 'PRINCIPAL') desc, id limit 1")
                    .query(Long.class).optional().orElseThrow(() -> new BusinessException("No hay bodegas activas"));
        }
        validarBodega(bodegaId);
        String factura = Textos.limpiar(r.numeroFacturaProveedor());
        if (factura != null && jdbc.sql("select count(*) from recepcion_compra where proveedor_id = :p and numero_factura_proveedor = :f")
                .param("p", proveedorId).param("f", factura).query(Long.class).single() > 0) {
            throw new ConflictException("La factura " + factura + " de este proveedor ya fue recibida");
        }
        LocalDate fecha = r.fecha() != null ? r.fecha() : ZonaClinica.hoy();
        Long recepcionId = jdbc.sql("""
                insert into recepcion_compra (orden_compra_id, proveedor_id, bodega_id, numero_factura_proveedor, fecha,
                                              fecha_vencimiento_pago, recibido_por, notas)
                values (:oc, :p, :b, :f, :fecha, :fvp, :uid, :notas) returning id
                """).param("oc", r.ordenCompraId()).param("p", proveedorId).param("b", bodegaId).param("f", factura)
                .param("fecha", fecha).param("fvp", r.fechaVencimientoPago()).param("uid", UsuarioActual.id().orElse(null))
                .param("notas", Textos.limpiar(r.notas())).query(Long.class).single();
        BigDecimal total = BigDecimal.ZERO;
        Instant ahora = Instant.now();
        for (RecepcionItemRequest it : r.items()) {
            Long itemId = it.itemId();
            BigDecimal costo = it.costoUnitario();
            BigDecimal pct = it.impuestoPct() == null ? BigDecimal.ZERO : it.impuestoPct();
            if (it.ordenCompraItemId() != null) {
                if (orden == null) {
                    throw new BusinessException("ordenCompraItemId solo aplica a recepciones de una orden");
                }
                record Oci(Long itemId, BigDecimal cantidad, BigDecimal recibida, BigDecimal costo, BigDecimal pct) {
                }
                Oci oci = jdbc.sql("select item_id, cantidad, cantidad_recibida, costo_unitario, impuesto_pct from orden_compra_item where id = :id and orden_compra_id = :oc for update")
                        .param("id", it.ordenCompraItemId()).param("oc", r.ordenCompraId())
                        .query((rs, n) -> new Oci(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                                rs.getBigDecimal(5))).optional()
                        .orElseThrow(() -> new BusinessException("El renglon " + it.ordenCompraItemId() + " no pertenece a la orden"));
                if (itemId != null && !itemId.equals(oci.itemId())) {
                    throw new BusinessException("El item no corresponde al renglon de la orden");
                }
                itemId = oci.itemId();
                BigDecimal pendiente = oci.cantidad().subtract(oci.recibida());
                if (it.cantidad().compareTo(pendiente) > 0) {
                    throw new BusinessException("Se recibe mas de lo pendiente del renglon " + it.ordenCompraItemId()
                            + " (pendiente " + pendiente.stripTrailingZeros().toPlainString() + ")");
                }
                if (costo == null) {
                    costo = oci.costo();
                }
                if (it.impuestoPct() == null) {
                    pct = oci.pct();
                }
                jdbc.sql("update orden_compra_item set cantidad_recibida = cantidad_recibida + :c where id = :id")
                        .param("c", it.cantidad()).param("id", it.ordenCompraItemId()).update();
            } else if (orden != null) {
                throw new BusinessException("Cada renglon de una recepcion de orden debe indicar ordenCompraItemId");
            }
            if (itemId == null) {
                throw new BusinessException("Cada renglon necesita itemId (o ordenCompraItemId)");
            }
            if (costo == null) {
                throw new BusinessException("El costo unitario es obligatorio en recepciones sin orden");
            }
            Long recepcionItemId = jdbc.sql("""
                    insert into recepcion_compra_item (recepcion_id, orden_compra_item_id, item_id, cantidad, costo_unitario)
                    values (:r, :oci, :i, :c, :cu) returning id
                    """).param("r", recepcionId).param("oci", it.ordenCompraItemId()).param("i", itemId)
                    .param("c", it.cantidad()).param("cu", costo).query(Long.class).single();
            Long loteId = lotes.crearLote(itemId, bodegaId, Textos.limpiar(it.numeroLote()), it.fechaVencimiento(),
                    it.cantidad(), costo, proveedorId, "Recepcion de compra" + (factura == null ? "" : " factura " + factura),
                    "RECEPCION:" + recepcionId, recepcionItemId, false);
            jdbc.sql("update recepcion_compra_item set lote_id = :l where id = :id").param("l", loteId)
                    .param("id", recepcionItemId).update();
            jdbc.sql("update item set costo = :c, actualizado_en = now() where id = :id").param("c", costo).param("id", itemId).update();
            jdbc.sql("""
                    insert into item_proveedor (item_id, proveedor_id, ultimo_costo, ultima_compra) values (:i, :p, :c, :f)
                    on conflict (item_id, proveedor_id) do update set ultimo_costo = excluded.ultimo_costo,
                                                                      ultima_compra = excluded.ultima_compra
                    """).param("i", itemId).param("p", proveedorId).param("c", costo).param("f", fecha).update();
            BigDecimal base = dinero(it.cantidad().multiply(costo));
            total = total.add(base).add(dinero(base.multiply(pct).divide(BigDecimal.valueOf(100))));
        }
        jdbc.sql("update recepcion_compra set total = :t where id = :id").param("t", total).param("id", recepcionId).update();
        if (orden != null) {
            boolean completa = jdbc.sql("select count(*) from orden_compra_item where orden_compra_id = :id and cantidad_recibida < cantidad")
                    .param("id", r.ordenCompraId()).query(Long.class).single() == 0;
            jdbc.sql("update orden_compra set estado = :e, actualizado_en = now() where id = :id")
                    .param("e", completa ? "RECIBIDA" : "PARCIAL").param("id", r.ordenCompraId()).update();
        }
        return recepcion(recepcionId);
    }

    // ------------------------------------------------------------------ utilidades

    private void validarProveedor(Long id) {
        if (jdbc.sql("select count(*) from proveedor where id = :id and activo").param("id", id).query(Long.class)
                .single() == 0) {
            throw new BusinessException("El proveedor " + id + " no existe o esta inactivo");
        }
    }

    private void validarBodega(Long id) {
        if (jdbc.sql("select count(*) from bodega where id = :id and activo").param("id", id).query(Long.class)
                .single() == 0) {
            throw new BusinessException("La bodega " + id + " no existe o esta inactiva");
        }
    }

    private static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
