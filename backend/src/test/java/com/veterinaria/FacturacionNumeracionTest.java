package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** Numeracion por resoluciones: consecutivos sin saltos, series separadas, rango y vigencia. */
class FacturacionNumeracionTest extends Fase4TestSupport {

    private long consecutivo(String numero) {
        return Long.parseLong(numero.substring(numero.indexOf('-') + 1));
    }

    private String venta(Cajero caja, long servicio, String metodo, int monto) throws Exception {
        return json(POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(servicio, 1)),
                "pagos", lista(pago(metodo, monto)))).andExpect(status().isCreated()));
    }

    @Test
    void resolucionesInternasSembradasYConsecutivosSinSaltos() throws Exception {
        GET("/api/facturacion/resoluciones?tipo=POS", admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroResolucion=='INTERNA-POS')].prefijo").value("POS"));
        GET("/api/facturacion/resoluciones?tipo=NOTA_CREDITO", admin())
                .andExpect(jsonPath("$[?(@.numeroResolucion=='INTERNA-NC')].prefijo").value("NC"));
        GET("/api/facturacion/resoluciones?tipo=FACTURA", admin())
                .andExpect(jsonPath("$[?(@.numeroResolucion=='INTERNA-FACTURA')].prefijo").value("FI"));
        Cajero caja = cajero(0);
        long s = servicio("Servicio numeracion", 10000, 0);
        long a = consecutivo(JsonPath.read(venta(caja, s, "EFECTIVO", 10000), "$.numero"));
        long b = consecutivo(JsonPath.read(venta(caja, s, "TARJETA", 10000), "$.numero"));
        assertEquals(a + 1, b);
        // una venta que falla (stock 409) revierte tambien el consecutivo: no hay huecos
        long item = item("Sin stock numeracion", 5000);
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(item, 1)), "pagos", lista(pago("EFECTIVO", 5000))))
                .andExpect(status().isConflict());
        long c = consecutivo(JsonPath.read(venta(caja, s, "EFECTIVO", 10000), "$.numero"));
        assertEquals(b + 1, c);
        // la resolucion refleja lo emitido
        GET("/api/facturacion/resoluciones?tipo=POS", admin()).andExpect(jsonPath("$[0].consecutivoActual").value(c))
                .andExpect(jsonPath("$[0].siguienteNumero").value("POS-" + String.format("%06d", c + 1)));
    }

    @Test
    void notasCreditoTienenSuPropiaSerie() throws Exception {
        Cajero caja = cajero(0);
        long s = servicio("Servicio NC serie", 10000, 0);
        long f1 = ((Number) JsonPath.read(venta(caja, s, "EFECTIVO", 10000), "$.id")).longValue();
        long f2 = ((Number) JsonPath.read(venta(caja, s, "EFECTIVO", 10000), "$.id")).longValue();
        String n1 = JsonPath.read(json(POST("/api/facturacion/facturas/" + f1 + "/notas-credito", admin(), Map.of("motivo", "a"))
                .andExpect(status().isCreated())), "$.nota.numero");
        String n2 = JsonPath.read(json(POST("/api/facturacion/facturas/" + f2 + "/notas-credito", admin(), Map.of("motivo", "b"))
                .andExpect(status().isCreated())), "$.nota.numero");
        assertEquals("NC", n1.substring(0, 2));
        assertEquals(consecutivo(n1) + 1, consecutivo(n2));
    }

    @Test
    void resolucionConRangoVigenciaYAgotamiento() throws Exception {
        String admin = admin();
        long servicio = servicio("Servicio rango", 10000, 0);
        long tutorId = tutor();
        // se reemplaza la resolucion de FACTURA por una de rango 1..2 (la interna queda inactiva)
        long interna = ((Number) JsonPath.read(json(GET("/api/facturacion/resoluciones?tipo=FACTURA", admin)), "$[0].id")).longValue();
        PUT("/api/facturacion/resoluciones/" + interna, admin, Map.of("activo", false)).andExpect(jsonPath("$.activo").value(false));
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("resolucion")));
        // validaciones del alta
        Map<String, Object> base = Map.of("tipoDocumento", "FACTURA", "prefijo", "TX", "numeroResolucion", "18760000001",
                "rangoDesde", 1, "rangoHasta", 2, "ambiente", "PRODUCCION", "vigenteDesde", hoy().minusDays(10).toString(),
                "vigenteHasta", hoy().plusDays(300).toString());
        POST("/api/facturacion/resoluciones", admin, Map.of("tipoDocumento", "FACTURA", "prefijo", "BAD", "numeroResolucion", "1",
                "rangoDesde", 5, "rangoHasta", 2)).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/resoluciones", admin, Map.of("tipoDocumento", "FACTURA", "prefijo", "BAD")).andExpect(status().isBadRequest());
        long res = idDe(POST("/api/facturacion/resoluciones", admin, base).andExpect(status().isCreated())
                .andExpect(jsonPath("$.siguienteNumero").value("TX-000001")).andExpect(jsonPath("$.disponibles").value(2)));
        POST("/api/facturacion/resoluciones", admin, base).andExpect(status().isConflict());
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value("TX-000001"))
                .andExpect(jsonPath("$.resolucion.numeroResolucion").value("18760000001"))
                .andExpect(jsonPath("$.resolucion.ambiente").value("PRODUCCION"));
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(jsonPath("$.numero").value("TX-000002"));
        // rango agotado -> 409; ampliarlo (no por debajo de lo usado) reanuda la numeracion
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(status().isConflict());
        PUT("/api/facturacion/resoluciones/" + res, admin, Map.of("rangoHasta", 1)).andExpect(status().isUnprocessableEntity());
        PUT("/api/facturacion/resoluciones/" + res, admin, Map.of("rangoHasta", 5)).andExpect(jsonPath("$.disponibles").value(3));
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(jsonPath("$.numero").value("TX-000003"));
        // vigencia vencida -> sin resolucion utilizable
        PUT("/api/facturacion/resoluciones/" + res, admin, Map.of("vigenteHasta", hoy().minusDays(1).toString()))
                .andExpect(status().isOk());
        POST("/api/facturacion/facturas", admin, Map.of("tutorId", tutorId, "items", lista(linea(servicio, 1))))
                .andExpect(status().isConflict());
        // nada quedo a medias por los rechazos
        GET("/api/facturacion/facturas?tipo=FACTURA&q=TX-", admin).andExpect(jsonPath("$.totalElements").value(3));
    }
}
