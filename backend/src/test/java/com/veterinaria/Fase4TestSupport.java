package com.veterinaria;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;

/** Utilidades de Fase 4: items, lotes por API, cajas/turnos aislados por prueba y lectura de montos. */
abstract class Fase4TestSupport extends Fase3TestSupport {

    private static final AtomicInteger SEQ4 = new AtomicInteger(100);

    protected String unico(String prefijo) {
        return prefijo + "-" + SEQ4.incrementAndGet();
    }

    /** Item de inventario nuevo (medicamento con control de stock) creado por la API. */
    protected long item(String nombre, int precio, int iva, boolean controlado, int minimo) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("codigo", unico("T4"), "nombre", nombre + " " + SEQ4.get(),
                "tipo", "MEDICAMENTO", "precioVenta", precio, "impuestoPct", iva, "controlaStock", true,
                "stockMinimo", minimo));
        if (controlado) {
            body.put("esControlado", true);
        }
        return idDe(POST("/api/inventario/items", admin(), body).andExpect(status().isCreated()));
    }

    protected long item(String nombre, int precio) throws Exception {
        return item(nombre, precio, 0, false, 0);
    }

    /** Servicio (sin stock). */
    protected long servicio(String nombre, int precio, int iva) throws Exception {
        return idDe(POST("/api/inventario/items", admin(), Map.of("codigo", unico("S4"), "nombre", nombre + " " + SEQ4.get(),
                "tipo", "SERVICIO", "precioVenta", precio, "impuestoPct", iva)).andExpect(status().isCreated()));
    }

    protected long bodega() throws Exception {
        return primerId(json(GET("/api/inventario/bodegas", admin())), "$[?(@.nombre=='Bodega principal')].id");
    }

    /** Lote por la API de carga inicial. */
    protected long cargar(long itemId, String numero, int cantidad, LocalDate vence) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("itemId", itemId, "bodegaId", bodega(), "numeroLote", numero,
                "cantidad", cantidad, "costoUnitario", 1000));
        if (vence != null) {
            body.put("fechaVencimiento", vence.toString());
        }
        return idDe(POST("/api/inventario/lotes", admin(), body).andExpect(status().isCreated()));
    }

    /** Lote ya vencido (la API no admite ingresarlos): se inserta directo para probar que FEFO lo ignora. */
    protected long loteVencido(long itemId, String numero, int cantidad) throws Exception {
        return jdbc.sql("""
                insert into lote (item_id, bodega_id, numero_lote, fecha_vencimiento, cantidad_inicial, cantidad_actual)
                values (:i, :b, :n, :v, :q, :q) returning id
                """).param("i", itemId).param("b", bodega()).param("n", numero).param("v", hoy().minusDays(5))
                .param("q", BigDecimal.valueOf(cantidad)).query(Long.class).single();
    }

    protected BigDecimal stockItem(long itemId) {
        return jdbc.sql("select coalesce(sum(cantidad_actual), 0) from lote where item_id = :i").param("i", itemId)
                .query(BigDecimal.class).single();
    }

    protected BigDecimal cantidadLote(long loteId) {
        return stock(loteId);
    }

    /** Usuario RECEPCION con una caja propia y un turno abierto (aisla las pruebas: una caja tiene un solo turno abierto). */
    protected Cajero cajero(int apertura) throws Exception {
        return cajero("RECEPCION", apertura);
    }

    protected Cajero cajero(String rol, int apertura) throws Exception {
        Usuario u = usuario(rol);
        long caja = jdbc.sql("insert into caja (sede_id, nombre) values ((select id from sede where es_principal), :n) returning id")
                .param("n", unico("Caja")).query(Long.class).single();
        long turno = idDe(POST("/api/facturacion/caja/turnos/abrir", u.bearer(),
                Map.of("cajaId", caja, "montoApertura", apertura)).andExpect(status().isCreated()));
        return new Cajero(u, caja, turno);
    }

    protected record Cajero(Usuario usuario, long cajaId, long turnoId) {
        String bearer() {
            return usuario.bearer();
        }
    }

    protected long tutor() throws Exception {
        return jdbc.sql("select tutor_id from paciente_tutor where paciente_id = :p").param("p", paciente())
                .query(Long.class).single();
    }

    protected static BigDecimal num(String json, String path) {
        Object v = JsonPath.read(json, path);
        if (v instanceof List<?> l) {
            v = l.get(0);
        }
        return new BigDecimal(String.valueOf(v));
    }

    protected static Map<String, Object> linea(long itemId, double cantidad) {
        return Map.of("itemId", itemId, "cantidad", cantidad);
    }

    protected static Map<String, Object> pago(String metodo, int monto) {
        return Map.of("metodo", metodo, "monto", monto);
    }

    protected static List<Object> lista(Object... xs) {
        return List.of(xs);
    }
}
