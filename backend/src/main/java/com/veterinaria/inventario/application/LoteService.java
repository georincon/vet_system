package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.ProductoClinicoResolver;
import com.veterinaria.inventario.api.dto.StockDtos.LoteRequest;
import com.veterinaria.inventario.api.dto.StockDtos.LoteResponse;
import com.veterinaria.inventario.domain.TipoMovimiento;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.inventario.internal.StockRepository;
import com.veterinaria.inventario.internal.StockRepository.LoteRow;
import com.veterinaria.inventario.internal.StockRepository.Mov;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;

/** Lotes y vencimientos: listado (selector de vacunacion), carga inicial, cuarentena y retiro. */
@Service
public class LoteService {

    private static final int MAX_FILAS = 500;
    private static final String BASE = """
            select l.*, i.codigo as item_codigo, i.nombre as item_nombre, b.nombre as bodega
            from lote l join item i on i.id = l.item_id join bodega b on b.id = l.bodega_id
            """;

    private final JdbcClient jdbc;
    private final StockRepository stock;
    private final ObjectProvider<ProductoClinicoResolver> resolver;

    public LoteService(JdbcClient jdbc, StockRepository stock, ObjectProvider<ProductoClinicoResolver> resolver) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.resolver = resolver;
    }

    /**
     * Lotes por FEFO. {@code vacunaId}/{@code antiparasitarioId} se traducen al item de inventario mediante el puerto
     * {@link ProductoClinicoResolver} (si no hay item vinculado, la lista es vacia).
     */
    @Transactional(readOnly = true)
    public List<LoteResponse> listar(Long itemId, Long vacunaId, Long antiparasitarioId, Long bodegaId,
            boolean soloVigentes, Boolean conStock, String q) {
        Long item = itemId;
        if (vacunaId != null || antiparasitarioId != null) {
            ProductoClinicoResolver r = resolver.getIfAvailable();
            Long deProducto = r == null ? null
                    : (vacunaId != null ? r.itemIdDeVacuna(vacunaId) : r.itemIdDeAntiparasitario(antiparasitarioId))
                            .orElse(null);
            if (deProducto == null || item != null && !item.equals(deProducto)) {
                return List.of();
            }
            item = deProducto;
        }
        LocalDate hoy = ZonaClinica.hoy();
        Filtro f = new Filtro().param("hoy", hoy).si(item, "l.item_id = :item", "item")
                .si(bodegaId, "l.bodega_id = :bodega", "bodega");
        if (soloVigentes) {
            f.y("l.cantidad_actual > 0 and l.estado = 'DISPONIBLE' and (l.fecha_vencimiento is null or l.fecha_vencimiento >= :hoy)");
        }
        if (conStock != null) {
            f.y(conStock ? "l.cantidad_actual > 0" : "l.cantidad_actual = 0");
        }
        if (q != null && !q.isBlank()) {
            f.y("(l.numero_lote ilike :q or i.nombre ilike :q or i.codigo ilike :q)", "q", "%" + q.trim() + "%");
        }
        return jdbc.sql(BASE + f.sql() + " order by l.fecha_vencimiento asc nulls last, l.id limit " + MAX_FILAS)
                .params(f.params()).query((rs, n) -> aLote(rs, hoy)).list();
    }

    @Transactional(readOnly = true)
    public LoteResponse obtener(Long id) {
        LocalDate hoy = ZonaClinica.hoy();
        return jdbc.sql(BASE + " where l.id = :id").param("id", id).query((rs, n) -> aLote(rs, hoy)).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Lote", id));
    }

    /** Carga inicial: crea el lote y su movimiento ENTRADA. Para compras use las recepciones. */
    @Transactional
    public LoteResponse crear(LoteRequest r) {
        crearLote(r.itemId(), r.bodegaId(), Textos.limpiar(r.numeroLote()), r.fechaVencimiento(), r.cantidad(),
                r.costoUnitario(), r.proveedorId(), Textos.limpiar(r.motivo()) == null ? "Carga inicial de inventario"
                        : r.motivo().trim(), "CARGA_INICIAL", null, true);
        Long id = jdbc.sql("select id from lote where item_id = :i and bodega_id = :b and numero_lote = :n")
                .param("i", r.itemId()).param("b", r.bodegaId()).param("n", Textos.limpiar(r.numeroLote()))
                .query(Long.class).single();
        return obtener(id);
    }

    /**
     * Crea un lote nuevo o, si ya existe (item, bodega, numero) y {@code fusionar}, le suma la entrada.
     *
     * @return id del lote
     */
    @Transactional
    public Long crearLote(Long itemId, Long bodegaId, String numeroLote, LocalDate vencimiento, BigDecimal cantidad,
            BigDecimal costo, Long proveedorId, String motivo, String referencia, Long recepcionItemId, boolean estricto) {
        record ItemRow(boolean controlaStock, boolean tieneVencimiento, String nombre, boolean activo) {
        }
        ItemRow item = jdbc.sql("select controla_stock, tiene_vencimiento, nombre, activo from item where id = :id")
                .param("id", itemId).query((rs, n) -> new ItemRow(rs.getBoolean(1), rs.getBoolean(2), rs.getString(3),
                        rs.getBoolean(4))).optional().orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        if (!item.controlaStock()) {
            throw new BusinessException("El item " + item.nombre() + " no controla stock: no admite lotes");
        }
        if (jdbc.sql("select count(*) from bodega where id = :id and activo").param("id", bodegaId).query(Long.class)
                .single() == 0) {
            throw new BusinessException("La bodega " + bodegaId + " no existe o esta inactiva");
        }
        if (numeroLote == null) {
            throw new BusinessException("El numero de lote es obligatorio");
        }
        if (item.tieneVencimiento() && vencimiento == null) {
            throw new BusinessException("El item " + item.nombre() + " exige fecha de vencimiento del lote");
        }
        if (vencimiento != null && vencimiento.isBefore(ZonaClinica.hoy())) {
            throw new BusinessException("No se puede ingresar un lote ya vencido (" + vencimiento + ")");
        }
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new BusinessException("La cantidad debe ser mayor a cero");
        }
        List<Object[]> previos = jdbc.sql("select id, fecha_vencimiento, estado from lote where item_id = :i and bodega_id = :b and numero_lote = :n for update")
                .param("i", itemId).param("b", bodegaId).param("n", numeroLote)
                .query((rs, n) -> new Object[] {rs.getLong(1), rs.getDate(2), rs.getString(3)}).list();
        Long loteId;
        if (previos.isEmpty()) {
            loteId = jdbc.sql("""
                    insert into lote (item_id, bodega_id, numero_lote, fecha_vencimiento, cantidad_inicial, cantidad_actual,
                                      costo_unitario, proveedor_id)
                    values (:i, :b, :n, :v, 0, 0, :c, :p) returning id
                    """).param("i", itemId).param("b", bodegaId).param("n", numeroLote).param("v", vencimiento)
                    .param("c", costo).param("p", proveedorId).query(Long.class).single();
        } else {
            if (estricto) {
                throw new ConflictException("Ya existe el lote " + numeroLote + " de este item en la bodega");
            }
            loteId = (Long) previos.get(0)[0];
            String estadoPrevio = (String) previos.get(0)[2];
            if ("RETIRADO".equals(estadoPrevio) || "VENCIDO".equals(estadoPrevio) || "CUARENTENA".equals(estadoPrevio)) {
                throw new ConflictException("El lote " + numeroLote + " existe en estado " + estadoPrevio
                        + ": no admite nuevas entradas");
            }
            java.sql.Date existente = (java.sql.Date) previos.get(0)[1];
            if (vencimiento != null && existente != null && !existente.toLocalDate().equals(vencimiento)) {
                throw new BusinessException("El lote " + numeroLote + " ya existe con otro vencimiento ("
                        + existente.toLocalDate() + ")");
            }
        }
        jdbc.sql("update lote set cantidad_inicial = cantidad_inicial + :q, costo_unitario = coalesce(:c, costo_unitario) where id = :id")
                .param("q", cantidad).param("c", costo).param("id", loteId).update();
        stock.mover(new Mov(loteId, TipoMovimiento.ENTRADA, cantidad, motivo, referencia, UsuarioActual.id().orElse(null),
                null, null, null, recepcionItemId, costo, null, false));
        return loteId;
    }

    @Transactional
    public LoteResponse cambiarEstado(Long id, String estado) {
        LoteRow lote = stock.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Lote", id));
        if (lote.estado().equals("RETIRADO") || lote.estado().equals("VENCIDO")) {
            throw new ConflictException("El lote esta " + lote.estado() + " y no se puede reactivar");
        }
        String nuevo = estado;
        if (estado.equals("DISPONIBLE") && lote.cantidad().signum() == 0) {
            nuevo = "AGOTADO";
        }
        jdbc.sql("update lote set estado = :e where id = :id").param("e", nuevo).param("id", id).update();
        return obtener(id);
    }

    /** Da de baja las existencias de un lote (vencido, danado, retirado por el proveedor): movimiento VENCIMIENTO. */
    @Transactional
    public LoteResponse retirar(Long id, String motivo) {
        LoteRow lote = stock.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Lote", id));
        if (lote.estado().equals("RETIRADO") || lote.estado().equals("VENCIDO")) {
            throw new ConflictException("El lote ya fue dado de baja (" + lote.estado() + ")");
        }
        boolean vencido = lote.vencimiento() != null && lote.vencimiento().isBefore(ZonaClinica.hoy());
        jdbc.sql("update lote set estado = :e where id = :id").param("e", vencido ? "VENCIDO" : "RETIRADO")
                .param("id", id).update();
        if (lote.cantidad().signum() > 0) {
            stock.mover(new Mov(id, TipoMovimiento.VENCIMIENTO, lote.cantidad().negate(),
                    (vencido ? "Baja por vencimiento: " : "Baja de lote: ") + motivo.trim(), "BAJA_LOTE:" + id,
                    UsuarioActual.id().orElse(null), null, null, null, null, null, null, true));
        }
        return obtener(id);
    }

    private static LoteResponse aLote(java.sql.ResultSet rs, LocalDate hoy) throws java.sql.SQLException {
        java.sql.Date v = rs.getDate("fecha_vencimiento");
        LocalDate venc = v == null ? null : v.toLocalDate();
        BigDecimal actual = rs.getBigDecimal("cantidad_actual");
        String estado = rs.getString("estado");
        boolean vencido = venc != null && venc.isBefore(hoy);
        java.sql.Timestamp creado = rs.getTimestamp("creado_en");
        return new LoteResponse(rs.getLong("id"), rs.getLong("item_id"), rs.getString("item_codigo"),
                rs.getString("item_nombre"), rs.getLong("bodega_id"), rs.getString("bodega"), rs.getString("numero_lote"),
                venc, venc == null ? null : ChronoUnit.DAYS.between(hoy, venc), vencido, rs.getBigDecimal("cantidad_inicial"),
                actual, Visibilidad.veCostos() ? rs.getBigDecimal("costo_unitario") : null, estado,
                actual.signum() > 0 && "DISPONIBLE".equals(estado) && !vencido, (Long) rs.getObject("proveedor_id"),
                creado == null ? null : creado.toInstant());
    }
}
