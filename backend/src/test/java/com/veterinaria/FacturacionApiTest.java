package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.veterinaria.clinica.ConsultaCerrada;
import com.veterinaria.facturacion.application.CargoConsultaService;

class FacturacionApiTest extends Fase4TestSupport {

    @Autowired
    CargoConsultaService cargos;

    @Autowired
    ApplicationEventPublisher eventos;

    @Autowired
    TransactionTemplate tx;

    /** Primer valor de un JsonPath con filtro o 0 si no hay coincidencias. */
    private static BigDecimal opt(String json, String path) {
        List<?> l = JsonPath.read(json, path);
        return l.isEmpty() ? BigDecimal.ZERO : new BigDecimal(String.valueOf(l.get(0)));
    }

    private long contarFacturas() {
        return jdbc.sql("select count(*) from factura").query(Long.class).single();
    }

    @Test
    void posConPagosMixtosCuadraConElTotalYDescuentaFefo() throws Exception {
        Cajero caja = cajero(0);
        long a = item("Antibiotico POS", 10000, 19, false, 0);
        long s = servicio("Servicio POS", 50000, 0);
        long l1 = cargar(a, "POS-1", 3, hoy().plusDays(10));
        long l2 = cargar(a, "POS-2", 10, hoy().plusDays(100));
        // 5 x 10.000 con 5.000 de descuento + IVA 19% = 53.550; servicio 50.000 -> total 103.550
        Map<String, Object> lineaA = Map.of("itemId", a, "cantidad", 5, "descuento", 5000);
        List<Object> items = lista(lineaA, linea(s, 1));
        // pagos que no cuadran -> 422 y no queda nada (ni stock descontado ni numero consumido)
        long antes = contarFacturas();
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", items, "pagos", lista(pago("EFECTIVO", 100000))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message", containsString("cuadrar")));
        assertEquals(antes, contarFacturas());
        assertEquals(0, stockItem(a).compareTo(BigDecimal.valueOf(13)));
        String res = json(POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", items,
                "pagos", lista(pago("EFECTIVO", 50000), pago("TARJETA", 53550))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoDocumento").value("POS"))
                .andExpect(jsonPath("$.numero", startsWith("POS-"))).andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.tutor.nombre").value("Consumidor Final"))
                .andExpect(jsonPath("$.subtotal").value(100000.0)).andExpect(jsonPath("$.descuento").value(5000.0))
                .andExpect(jsonPath("$.impuestos").value(8550.0)).andExpect(jsonPath("$.total").value(103550.0))
                .andExpect(jsonPath("$.pagado").value(103550.0)).andExpect(jsonPath("$.saldo").value(0.0))
                .andExpect(jsonPath("$.pagos", hasSize(2))).andExpect(jsonPath("$.estadoDian").value("PENDIENTE"))
                .andExpect(jsonPath("$.items[0].base").value(45000.0)).andExpect(jsonPath("$.items[0].impuesto").value(8550.0))
                .andExpect(jsonPath("$.items[0].total").value(53550.0)).andExpect(jsonPath("$.items[0].loteId").value(l1)));
        // la suma de los renglones y de los pagos es el total de la factura
        assertEquals(0, num(res, "$.total").compareTo(num(res, "$.items[0].total").add(num(res, "$.items[1].total"))));
        assertEquals(0, num(res, "$.total").compareTo(num(res, "$.pagos[0].monto").add(num(res, "$.pagos[1].monto"))));
        // FEFO: 3 del lote que vence primero y 2 del siguiente; kardex referencia la factura y su renglon
        assertEquals(0, cantidadLote(l1).compareTo(BigDecimal.ZERO));
        assertEquals(0, cantidadLote(l2).compareTo(BigDecimal.valueOf(8)));
        GET("/api/inventario/movimientos?itemId=" + a + "&referencia=FACTURA", admin()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].facturaItemId").isNumber());
        // stock insuficiente -> 409 y la venta completa se revierte
        long antes2 = contarFacturas();
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(a, 9)),
                "pagos", lista(pago("EFECTIVO", 107100)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Stock insuficiente")));
        assertEquals(antes2, contarFacturas());
        assertEquals(0, stockItem(a).compareTo(BigDecimal.valueOf(8)));
        // RECEPCION no puede cambiar precios ni usar renglones libres; el descuento mayor al renglon es 422
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(Map.of("itemId", s, "cantidad", 1,
                "precioUnitario", 1)), "pagos", lista(pago("EFECTIVO", 1)))).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(Map.of("descripcion", "Libre", "cantidad", 1,
                "precioUnitario", 1000)), "pagos", lista(pago("EFECTIVO", 1000)))).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(Map.of("itemId", s, "cantidad", 1,
                "descuento", 60000)), "pagos", lista(pago("EFECTIVO", 1)))).andExpect(status().isUnprocessableEntity());
        // ADMIN si puede un renglon libre (sin stock)
        POST("/api/facturacion/pos/ventas", cajero("ADMIN", 0).bearer(), Map.of("items", lista(Map.of("descripcion", "Libre admin",
                "cantidad", 2, "precioUnitario", 1500)), "pagos", lista(pago("NEQUI", 3000)))).andExpect(status().isCreated());
        // sin turno abierto no hay pagos
        Usuario sinTurno = usuario("RECEPCION");
        POST("/api/facturacion/pos/ventas", sinTurno.bearer(), Map.of("items", lista(linea(s, 1)),
                "pagos", lista(pago("EFECTIVO", 50000)))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("turno de caja")));
        // la venta a un tutor concreto con paciente queda a su nombre
        long tutorId = tutor();
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("tutorId", tutorId, "items", lista(linea(s, 1)),
                "pagos", lista(pago("TRANSFERENCIA", 50000)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutor.id").value(tutorId));
    }

    @Test
    void facturaDeContadoSaldoPagosParcialesEInmutabilidad() throws Exception {
        Cajero caja = cajero(0);
        long s = servicio("Cirugia menor", 100000, 19);
        long tutorId = tutor();
        String f = json(POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutorId, "items", lista(linea(s, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.total").value(119000.0)).andExpect(jsonPath("$.saldo").value(119000.0))
                .andExpect(jsonPath("$.numero", startsWith("FI-"))).andExpect(jsonPath("$.condicionPago").value("CONTADO")));
        long id = ((Number) JsonPath.read(f, "$.id")).longValue();
        // pagos mixtos: abono, exceso (422), saldo final; pagar de mas o una factura saldada no se permite
        POST("/api/facturacion/facturas/" + id + "/pagos", caja.bearer(), pago("EFECTIVO", 19000)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PARCIAL")).andExpect(jsonPath("$.saldo").value(100000.0))
                .andExpect(jsonPath("$.pagado").value(19000.0));
        POST("/api/facturacion/facturas/" + id + "/pagos", caja.bearer(), pago("EFECTIVO", 100001)).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/facturas/" + id + "/pagos", caja.bearer(), Map.of("metodo", "BITCOIN", "monto", 1))
                .andExpect(status().isBadRequest());
        POST("/api/facturacion/facturas/" + id + "/pagos", caja.bearer(), Map.of("metodo", "TRANSFERENCIA", "monto", 100000,
                "referencia", "TRX-1")).andExpect(jsonPath("$.estado").value("PAGADA")).andExpect(jsonPath("$.saldo").value(0.0))
                .andExpect(jsonPath("$.pagos", hasSize(2)));
        POST("/api/facturacion/facturas/" + id + "/pagos", caja.bearer(), pago("EFECTIVO", 1)).andExpect(status().isConflict());
        // contado con pagos que no cuadran -> 422; credito admite abono parcial
        POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutorId, "items", lista(linea(s, 1)),
                "pagos", lista(pago("EFECTIVO", 1000)))).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutorId, "condicionPago", "CREDITO",
                "items", lista(linea(s, 1)), "pagos", lista(pago("EFECTIVO", 19000)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PARCIAL")).andExpect(jsonPath("$.saldo").value(100000.0))
                .andExpect(jsonPath("$.fechaVencimiento").isString());
        // la factura emitida es inmutable: ni siquiera por SQL se cambian montos (trigger de V19)
        assertThrows(Exception.class, () -> jdbc.sql("update factura set total = 1 where id = :id").param("id", id).update());
        GET("/api/facturacion/facturas/" + id, caja.bearer()).andExpect(jsonPath("$.total").value(119000.0));
        GET("/api/facturacion/facturas?tutorId=" + tutorId + "&conSaldo=true", caja.bearer()).andExpect(jsonPath("$.totalElements").value(1));
        GET("/api/facturacion/facturas?q=FI-&tipo=FACTURA", caja.bearer()).andExpect(status().isOk());
        // DIAN: PENDIENTE con adaptador simulado; reintento solo ADMIN
        GET("/api/facturacion/facturas/" + id, caja.bearer()).andExpect(jsonPath("$.estadoDian").value("PENDIENTE"))
                .andExpect(jsonPath("$.cufe").doesNotExist());
        POST("/api/facturacion/facturas/" + id + "/dian/reintentar", caja.bearer(), Map.of()).andExpect(status().isForbidden());
        POST("/api/facturacion/facturas/" + id + "/dian/reintentar", admin(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoDian").value("PENDIENTE"));
    }

    @Test
    void notaCreditoSoloAdminYConValidaciones() throws Exception {
        Cajero caja = cajero(0);
        long a = item("Producto NC", 10000, 19, false, 0);
        cargar(a, "NC-1", 10, hoy().plusDays(90));
        String f = json(POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutor(), "items", lista(linea(a, 4)),
                "pagos", lista(pago("EFECTIVO", 47600)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGADA")));
        long id = ((Number) JsonPath.read(f, "$.id")).longValue();
        long renglon = ((Number) JsonPath.read(f, "$.items[0].id")).longValue();
        // RECEPCION no acredita; el motivo es obligatorio; no se acredita mas de lo facturado ni renglones ajenos
        POST("/api/facturacion/facturas/" + id + "/notas-credito", caja.bearer(), Map.of("motivo", "error")).andExpect(status().isForbidden());
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin(), Map.of()).andExpect(status().isBadRequest());
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin(), Map.of("motivo", "x", "items",
                lista(Map.of("facturaItemId", renglon, "cantidad", 5)))).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin(), Map.of("motivo", "x", "items",
                lista(Map.of("facturaItemId", 999999, "cantidad", 1)))).andExpect(status().isUnprocessableEntity());
        // devolucion en efectivo exige turno abierto del que acredita
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin(), Map.of("motivo", "x", "items",
                lista(Map.of("facturaItemId", renglon, "cantidad", 1)), "devolucion", Map.of("metodo", "EFECTIVO")))
                .andExpect(status().isUnprocessableEntity());
        // el rechazo revirtio todo: sin notas y con la factura intacta
        GET("/api/facturacion/facturas/" + id, caja.bearer()).andExpect(jsonPath("$.notasCredito", hasSize(0)))
                .andExpect(jsonPath("$.estado").value("PAGADA")).andExpect(jsonPath("$.items[0].cantidadAcreditada").value(0.0));
    }

    @Test
    void notaCreditoParcialTotalYDevolucionEnCaja() throws Exception {
        Cajero admin = cajero("ADMIN", 0);
        long a = item("Producto NC2", 10000, 19, false, 0);
        long lote = cargar(a, "NC-2", 10, hoy().plusDays(90));
        String f = json(POST("/api/facturacion/facturas", admin.bearer(), Map.of("tutorId", tutor(), "items", lista(linea(a, 4)),
                "pagos", lista(pago("EFECTIVO", 47600)))).andExpect(status().isCreated()));
        long id = ((Number) JsonPath.read(f, "$.id")).longValue();
        long renglon = ((Number) JsonPath.read(f, "$.items[0].id")).longValue();
        String numeroFactura = JsonPath.read(f, "$.numero");
        // nota parcial 1/4: reintegra 1 al lote y devuelve 11.900 en efectivo; la factura sigue PAGADA y con el mismo total
        String nc = json(POST("/api/facturacion/facturas/" + id + "/notas-credito", admin.bearer(), Map.of("motivo", "Devolucion de 1",
                "items", lista(Map.of("facturaItemId", renglon, "cantidad", 1)), "reintegrarStock", true,
                "devolucion", Map.of("metodo", "EFECTIVO"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota.tipoDocumento").value("NOTA_CREDITO")).andExpect(jsonPath("$.nota.numero", startsWith("NC-")))
                .andExpect(jsonPath("$.nota.total").value(11900.0)).andExpect(jsonPath("$.nota.facturaOrigenId").value(id))
                .andExpect(jsonPath("$.nota.numeroOrigen").value(numeroFactura))
                .andExpect(jsonPath("$.nota.estadoDian").value("PENDIENTE"))
                .andExpect(jsonPath("$.montoADevolver").value(11900.0)).andExpect(jsonPath("$.devolucionRegistradaEnCaja").value(true))
                .andExpect(jsonPath("$.stockReintegrado").value(1.0))
                .andExpect(jsonPath("$.factura.estado").value("PAGADA")).andExpect(jsonPath("$.factura.total").value(47600.0))
                .andExpect(jsonPath("$.factura.items[0].cantidadAcreditada").value(1.0))
                .andExpect(jsonPath("$.factura.notasCredito", hasSize(1))));
        assertEquals(0, cantidadLote(lote).compareTo(BigDecimal.valueOf(7)));
        GET("/api/facturacion/caja/turnos/" + admin.turnoId(), admin.bearer()).andExpect(jsonPath("$.resumen.egresos").value(11900.0))
                .andExpect(jsonPath("$.resumen.efectivoEsperado").value(35700.0));
        // nota por el resto (anulacion): la factura queda ANULADA pero sus montos no cambian
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin.bearer(), Map.of("motivo", "Anulacion"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nota.total").value(35700.0))
                .andExpect(jsonPath("$.factura.estado").value("ANULADA")).andExpect(jsonPath("$.factura.total").value(47600.0))
                .andExpect(jsonPath("$.factura.subtotal").value(40000.0)).andExpect(jsonPath("$.factura.saldo").value(0.0))
                .andExpect(jsonPath("$.factura.motivoAnulacion").value("Anulacion"))
                .andExpect(jsonPath("$.montoADevolver").value(35700.0)).andExpect(jsonPath("$.devolucionRegistradaEnCaja").value(false))
                .andExpect(jsonPath("$.stockReintegrado").value(0.0));
        assertEquals(0, cantidadLote(lote).compareTo(BigDecimal.valueOf(7)));
        // una factura ANULADA no admite mas notas ni pagos
        POST("/api/facturacion/facturas/" + id + "/notas-credito", admin.bearer(), Map.of("motivo", "otra")).andExpect(status().isConflict());
        POST("/api/facturacion/facturas/" + id + "/pagos", admin.bearer(), pago("EFECTIVO", 1)).andExpect(status().isConflict());
        // la suma de lo acreditado en las notas es el total de la factura
        GET("/api/facturacion/facturas/" + id, admin.bearer()).andExpect(jsonPath("$.notasCredito", hasSize(2)));
        assertEquals(0, jdbc.sql("select coalesce(sum(total), 0) from factura where factura_origen_id = :id and tipo_documento = 'NOTA_CREDITO'")
                .param("id", id).query(BigDecimal.class).single().compareTo(BigDecimal.valueOf(47600)));
        // factura a credito sin pagos: la nota total la anula y no hay nada que devolver
        String credito = json(POST("/api/facturacion/facturas", admin.bearer(), Map.of("tutorId", tutor(), "condicionPago", "CREDITO",
                "items", lista(linea(servicio("Servicio credito", 20000, 0), 1)))).andExpect(status().isCreated()));
        long cid = ((Number) JsonPath.read(credito, "$.id")).longValue();
        POST("/api/facturacion/facturas/" + cid + "/notas-credito", admin.bearer(), Map.of("motivo", "Cliente desiste"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montoADevolver").value(0.0))
                .andExpect(jsonPath("$.factura.estado").value("ANULADA")).andExpect(jsonPath("$.factura.saldo").value(0.0));
        // la nota credito no se paga ni se acredita
        long nota = ((Number) JsonPath.read(nc, "$.nota.id")).longValue();
        POST("/api/facturacion/facturas/" + nota + "/pagos", admin.bearer(), pago("EFECTIVO", 1)).andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/facturas/" + nota + "/notas-credito", admin.bearer(), Map.of("motivo", "x")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cierreDeCajaReportaLaDiferenciaContraElEfectivoContado() throws Exception {
        Cajero caja = cajero(100000);
        long s = servicio("Servicio caja", 50000, 0);
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(s, 1)), "pagos", lista(pago("EFECTIVO", 50000))))
                .andExpect(status().isCreated());
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(s, 1)), "pagos", lista(pago("TARJETA", 50000))))
                .andExpect(status().isCreated());
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/movimientos", caja.bearer(), Map.of("tipo", "RETIRO", "monto", 20000,
                "concepto", "Consignacion bancaria")).andExpect(status().isCreated());
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/movimientos", caja.bearer(), Map.of("tipo", "EGRESO", "monto", 999999,
                "concepto", "Mucho")).andExpect(status().isUnprocessableEntity());
        // esperado = 100.000 + 50.000 efectivo - 20.000 retiro = 130.000; la tarjeta no es efectivo
        GET("/api/facturacion/caja/turnos/actual", caja.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.efectivoEsperado").value(130000.0)).andExpect(jsonPath("$.resumen.totalCobrado").value(100000.0))
                .andExpect(jsonPath("$.resumen.ingresosPorMetodo", hasSize(2))).andExpect(jsonPath("$.movimientos", hasSize(1)));
        // otro usuario no cierra el turno; ADMIN si
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/cerrar", usuario("RECEPCION").bearer(),
                Map.of("efectivoContado", 130000)).andExpect(status().isForbidden());
        // diferencia -10.000 sin explicacion -> 422; con observaciones se reporta
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/cerrar", caja.bearer(), Map.of("efectivoContado", 120000))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/cerrar", caja.bearer(), Map.of("efectivoContado", 120000,
                "observaciones", "Faltante: vueltos mal dados")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO")).andExpect(jsonPath("$.efectivoEsperado").value(130000.0))
                .andExpect(jsonPath("$.efectivoContado").value(120000.0)).andExpect(jsonPath("$.diferencia").value(-10000.0))
                .andExpect(jsonPath("$.cerradoEn").isString());
        assertEquals(0, jdbc.sql("select diferencia from caja_turno where id = :id").param("id", caja.turnoId())
                .query(BigDecimal.class).single().compareTo(BigDecimal.valueOf(-10000)));
        // cerrado: no se cierra otra vez, no admite movimientos ni pagos
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/cerrar", caja.bearer(), Map.of("efectivoContado", 1, "observaciones", "x"))
                .andExpect(status().isConflict());
        POST("/api/facturacion/caja/turnos/" + caja.turnoId() + "/movimientos", caja.bearer(), Map.of("tipo", "INGRESO", "monto", 1,
                "concepto", "x")).andExpect(status().isConflict());
        POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(s, 1)), "pagos", lista(pago("EFECTIVO", 50000))))
                .andExpect(status().isUnprocessableEntity());
        GET("/api/facturacion/caja/turnos/actual", caja.bearer()).andExpect(status().isNotFound());
        // el mismo usuario puede abrir otro turno y cerrarlo exacto sin observaciones (diferencia 0)
        long turno2 = idDe(POST("/api/facturacion/caja/turnos/abrir", caja.bearer(), Map.of("cajaId", caja.cajaId(), "montoApertura", 5000))
                .andExpect(status().isCreated()));
        POST("/api/facturacion/caja/turnos/abrir", caja.bearer(), Map.of("cajaId", caja.cajaId(), "montoApertura", 1)).andExpect(status().isConflict());
        POST("/api/facturacion/caja/turnos/" + turno2 + "/cerrar", caja.bearer(), Map.of("efectivoContado", 5000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.diferencia").value(0.0));
        GET("/api/facturacion/caja/turnos?cajaId=" + caja.cajaId() + "&estado=CERRADO", caja.bearer()).andExpect(jsonPath("$.totalElements").value(2));
        // dos turnos abiertos en la misma caja no se permiten
        Cajero otro = cajero(0);
        POST("/api/facturacion/caja/turnos/abrir", usuario("RECEPCION").bearer(), Map.of("cajaId", otro.cajaId(), "montoApertura", 0))
                .andExpect(status().isConflict());
        GET("/api/facturacion/caja", caja.bearer()).andExpect(status().isOk());
    }

    @Test
    void carteraAbonosPorTutorYEdadesDeMora() throws Exception {
        Cajero caja = cajero(0);
        long tutorId = tutor();
        long s1 = servicio("Hospitalizacion", 100000, 0);
        long s2 = servicio("Control", 50000, 0);
        long f1 = idDe(POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutorId, "condicionPago", "CREDITO",
                "items", lista(linea(s1, 1)))).andExpect(status().isCreated()));
        long f2 = idDe(POST("/api/facturacion/facturas", caja.bearer(), Map.of("tutorId", tutorId, "condicionPago", "CREDITO",
                "items", lista(linea(s2, 1)))).andExpect(status().isCreated()));
        GET("/api/facturacion/cartera/tutores/" + tutorId, caja.bearer()).andExpect(jsonPath("$.saldo").value(150000.0))
                .andExpect(jsonPath("$.facturas", hasSize(2))).andExpect(jsonPath("$.saldoVencido").value(0.0));
        // abono mixto de 120.000: salda f1 (mas antigua) y deja 30.000 en f2
        POST("/api/facturacion/cartera/tutores/" + tutorId + "/abonos", caja.bearer(), Map.of("pagos",
                lista(pago("EFECTIVO", 70000), pago("TRANSFERENCIA", 50000)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(120000.0)).andExpect(jsonPath("$.saldoTutor").value(30000.0))
                .andExpect(jsonPath("$.aplicado", hasSize(3)));
        GET("/api/facturacion/facturas/" + f1, caja.bearer()).andExpect(jsonPath("$.estado").value("PAGADA")).andExpect(jsonPath("$.saldo").value(0.0));
        GET("/api/facturacion/facturas/" + f2, caja.bearer()).andExpect(jsonPath("$.estado").value("PARCIAL")).andExpect(jsonPath("$.saldo").value(30000.0));
        POST("/api/facturacion/cartera/tutores/" + tutorId + "/abonos", caja.bearer(), Map.of("pagos", lista(pago("EFECTIVO", 30001))))
                .andExpect(status().isUnprocessableEntity());
        // mora: se vence hace 40 dias (solo cambia la fecha, no los montos)
        jdbc.sql("update factura set fecha_vencimiento = current_date - 40 where id = :id").param("id", f2).update();
        GET("/api/facturacion/cartera?soloVencidas=true", caja.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.tutorId==" + tutorId + ")].saldoVencido").value(30000.0))
                .andExpect(jsonPath("$.items[?(@.tutorId==" + tutorId + ")].diasMoraMax").value(40));
        String edades = json(GET("/api/facturacion/reportes/cartera", caja.bearer()).andExpect(status().isOk()));
        assertTrue(num(edades, "$.edades.dias31a60").compareTo(BigDecimal.valueOf(30000)) >= 0);
        // saldando lo pendiente el tutor sale de la cartera
        POST("/api/facturacion/cartera/tutores/" + tutorId + "/abonos", caja.bearer(), Map.of("pagos", lista(pago("EFECTIVO", 30000))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.saldoTutor").value(0.0));
        GET("/api/facturacion/cartera?soloVencidas=true", caja.bearer())
                .andExpect(jsonPath("$.items[?(@.tutorId==" + tutorId + ")]", hasSize(0)));
        POST("/api/facturacion/cartera/tutores/" + tutorId + "/abonos", caja.bearer(), Map.of("pagos", lista(pago("EFECTIVO", 1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cierreDeConsultaGeneraUnPresupuestoSinDuplicarCargosYFacturaDescuentaStockUnaVez() throws Exception {
        Cajero caja = cajero(0);
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        long tutorId = jdbc.sql("select tutor_id from paciente_tutor where paciente_id = :p").param("p", paciente).query(Long.class).single();
        // inventario: un medicamento recetado y el lote de la vacuna (item sembrado VAC-CAN-RAB)
        long medicamento = item("Medicamento receta", 8000, 0, false, 0);
        long loteMed = cargar(medicamento, "RX-1", 10, hoy().plusDays(120));
        long itemRabia = jdbc.sql("select id from item where codigo = 'VAC-CAN-RAB'").query(Long.class).single();
        long loteRabia = cargar(itemRabia, unico("RABIA"), 5, hoy().plusDays(200));
        long procedimiento = servicio("Curacion de herida", 30000, 0);
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString(), "motivo", "Herida y vacuna", "vacunas", lista(Map.of("nombre", "Antirrabica canina"))))
                .andExpect(status().isCreated()));
        POST("/api/consultas/" + consulta + "/recetas", vet.bearer(), Map.of("items", lista(Map.of("itemId", medicamento,
                "medicamento", "Medicamento receta", "cantidad", 2)))).andExpect(status().isCreated());
        jdbc.sql("insert into consulta_procedimiento (consulta_id, item_id, descripcion, cantidad) values (:c, :i, 'Curacion', 1)")
                .param("c", consulta).param("i", procedimiento).update();
        // la vacuna ya descuenta su lote desde inmunizacion (con la consulta): facturar no debe descontar de nuevo
        POST("/api/pacientes/" + paciente + "/inmunizaciones", admin(), aplicacionVacuna(vacunaId("CAN-RAB"), hoy(),
                Map.of("loteId", loteRabia, "consultaId", consulta))).andExpect(status().isCreated());
        assertEquals(0, cantidadLote(loteRabia).compareTo(BigDecimal.valueOf(4)));
        POST("/api/consultas/" + consulta + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());
        // presupuesto BORRADOR de la consulta: servicio + procedimiento + receta + vacuna + acto de vacunacion
        String lista = json(GET("/api/facturacion/presupuestos?consultaId=" + consulta, admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)));
        long presupuesto = ((Number) JsonPath.read(lista, "$.items[0].id")).longValue();
        String detalle = json(GET("/api/facturacion/presupuestos/" + presupuesto, admin()).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.origen").value("CONSULTA")).andExpect(jsonPath("$.tutor.id").value(tutorId))
                .andExpect(jsonPath("$.items", hasSize(5))));
        BigDecimal total = num(detalle, "$.total");
        // 70.000 consulta + 30.000 curacion + 16.000 receta + 65.000... (rabia 35.000) + 15.000 acto
        assertEquals(0, total.compareTo(BigDecimal.valueOf(70000 + 30000 + 16000 + 35000 + 15000)));
        // idempotencia: reentregar el evento (por la via real, tras commit) y llamar al servicio no duplica nada
        eventos.publishEvent(new ConsultaCerrada(consulta, paciente, vet.id(), hoy(), List.of()));
        tx.executeWithoutResult(st -> eventos.publishEvent(new ConsultaCerrada(consulta, paciente, vet.id(), hoy(), List.of())));
        assertEquals(0, cargos.generar(consulta));
        GET("/api/facturacion/presupuestos?consultaId=" + consulta, admin()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].items", hasSize(5)));
        assertEquals(1L, jdbc.sql("select count(*) from presupuesto where consulta_id = :c and origen = 'CONSULTA'").param("c", consulta)
                .query(Long.class).single());
        // veterinario dueno ve su presupuesto; otro veterinario no (404) y no puede crear
        GET("/api/facturacion/presupuestos/" + presupuesto, vet.bearer()).andExpect(status().isOk());
        GET("/api/facturacion/presupuestos?consultaId=" + consulta, vet.bearer()).andExpect(jsonPath("$.totalElements").value(1));
        Usuario otroVet = usuario("VETERINARIO");
        GET("/api/facturacion/presupuestos/" + presupuesto, otroVet.bearer()).andExpect(status().isNotFound());
        GET("/api/facturacion/presupuestos", otroVet.bearer()).andExpect(jsonPath("$.totalElements").value(0));
        POST("/api/facturacion/presupuestos", vet.bearer(), Map.of("tutorId", tutorId, "items", lista(linea(procedimiento, 1))))
                .andExpect(status().isForbidden());
        // facturar el presupuesto: descuenta el medicamento (2) y NO la vacuna (ya descontada por inmunizacion)
        String fact = json(POST("/api/facturacion/presupuestos/" + presupuesto + "/facturar", caja.bearer(), Map.of("pagos",
                lista(pago("EFECTIVO", 100000), pago("TARJETA", 66000)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(166000.0)).andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.consultaId").value(consulta)).andExpect(jsonPath("$.presupuestoId").value(presupuesto)));
        assertEquals(0, cantidadLote(loteMed).compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, cantidadLote(loteRabia).compareTo(BigDecimal.valueOf(4)));
        assertEquals(0, num(fact, "$.items[?(@.itemId==" + itemRabia + ")].cantidadStockCubierta").compareTo(BigDecimal.ONE));
        GET("/api/facturacion/presupuestos/" + presupuesto, admin()).andExpect(jsonPath("$.estado").value("FACTURADO"))
                .andExpect(jsonPath("$.facturaId").value(((Number) JsonPath.read(fact, "$.id")).longValue()));
        // ya facturado: ni se factura de nuevo ni el evento le agrega cargos
        POST("/api/facturacion/presupuestos/" + presupuesto + "/facturar", caja.bearer(), Map.of()).andExpect(status().isConflict());
        assertEquals(0, cargos.generar(consulta));
        // anular la factura sin reintegrar deja el stock; con reintegro solo vuelve lo que desconto la factura (no el de la vacuna)
        String nc = json(POST("/api/facturacion/facturas/" + ((Number) JsonPath.read(fact, "$.id")).longValue() + "/notas-credito",
                admin(), Map.of("motivo", "Error de facturacion", "reintegrarStock", true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockReintegrado").value(2.0)));
        assertEquals(0, cantidadLote(loteMed).compareTo(BigDecimal.TEN));
        assertEquals(0, cantidadLote(loteRabia).compareTo(BigDecimal.valueOf(4)));
        assertTrue(nc.contains("ANULADA"));
    }

    @Test
    void presupuestoManualEstadosYFacturacion() throws Exception {
        Usuario recepcion = usuario("RECEPCION");
        long tutorId = tutor();
        long s = servicio("Profilaxis", 200000, 0);
        long p = idDe(POST("/api/facturacion/presupuestos", recepcion.bearer(), Map.of("tutorId", tutorId, "vigenteHasta",
                hoy().plusDays(15).toString(), "items", lista(Map.of("itemId", s, "cantidad", 1, "descuento", 20000))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.codigo", startsWith("PRE-")))
                .andExpect(jsonPath("$.total").value(180000.0)).andExpect(jsonPath("$.estado").value("BORRADOR")));
        PUT("/api/facturacion/presupuestos/" + p, recepcion.bearer(), Map.of("items", lista(linea(s, 2)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(400000.0));
        POST("/api/facturacion/presupuestos/" + p + "/estado", recepcion.bearer(), Map.of("estado", "ENVIADO")).andExpect(jsonPath("$.estado").value("ENVIADO"));
        POST("/api/facturacion/presupuestos/" + p + "/estado", recepcion.bearer(), Map.of("estado", "ACEPTADO")).andExpect(jsonPath("$.estado").value("ACEPTADO"));
        PUT("/api/facturacion/presupuestos/" + p, recepcion.bearer(), Map.of("notas", "x")).andExpect(status().isConflict());
        POST("/api/facturacion/presupuestos/" + p + "/estado", recepcion.bearer(), Map.of("estado", "RECHAZADO")).andExpect(status().isConflict());
        POST("/api/facturacion/presupuestos/" + p + "/estado", recepcion.bearer(), Map.of("estado", "FACTURADO")).andExpect(status().isBadRequest());
        POST("/api/facturacion/presupuestos", recepcion.bearer(), Map.of("tutorId", tutorId)).andExpect(status().isBadRequest());
        // factura a credito del presupuesto aceptado (sin turno: no hay pagos)
        POST("/api/facturacion/presupuestos/" + p + "/facturar", recepcion.bearer(), Map.of("condicionPago", "CREDITO")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(400000.0)).andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.condicionPago").value("CREDITO"));
        GET("/api/facturacion/presupuestos/" + p, recepcion.bearer()).andExpect(jsonPath("$.estado").value("FACTURADO"));
        // consumidor final no compra a credito
        Long cf = jdbc.sql("select id from tutor where numero_documento = '222222222222'").query(Long.class).single();
        POST("/api/facturacion/facturas", recepcion.bearer(), Map.of("tutorId", cf, "condicionPago", "CREDITO", "items", lista(linea(s, 1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reporteDeVentasPorDiaMetodoEItemYDashboard() throws Exception {
        Cajero caja = cajero(0);
        long a = item("Reporte item", 20000, 19, false, 0);
        cargar(a, "REP-1", 20, hoy().plusDays(50));
        String antes = json(GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy(), admin()).andExpect(status().isOk()));
        String dashAntes = json(GET("/api/dashboard/resumen", admin()));
        String venta = json(POST("/api/facturacion/pos/ventas", caja.bearer(), Map.of("items", lista(linea(a, 3)),
                "pagos", lista(pago("EFECTIVO", 40000), pago("NEQUI", 31400)))).andExpect(status().isCreated()));
        long facturaId = ((Number) JsonPath.read(venta, "$.id")).longValue();
        String despues = json(GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy(), admin()));
        // 3 x 20.000 + 19% = 71.400
        assertEquals(0, num(despues, "$.totales.facturado").subtract(num(antes, "$.totales.facturado")).compareTo(BigDecimal.valueOf(71400)));
        assertEquals(0, num(despues, "$.totales.neto").subtract(num(antes, "$.totales.neto")).compareTo(BigDecimal.valueOf(71400)));
        assertEquals(0, num(despues, "$.totales.impuestos").subtract(num(antes, "$.totales.impuestos")).compareTo(BigDecimal.valueOf(11400)));
        String dia = "$.porDia[?(@.fecha=='" + hoy() + "')].neto";
        assertEquals(0, opt(despues, dia).subtract(opt(antes, dia)).compareTo(BigDecimal.valueOf(71400)));
        String nequi = "$.porMetodoPago[?(@.metodo=='NEQUI')].monto";
        assertEquals(0, opt(despues, nequi).subtract(opt(antes, nequi)).compareTo(BigDecimal.valueOf(31400)));
        assertEquals(0, num(despues, "$.porItem[?(@.itemId==" + a + ")].cantidad").compareTo(BigDecimal.valueOf(3)));
        assertEquals(0, num(despues, "$.porItem[?(@.itemId==" + a + ")].total").compareTo(BigDecimal.valueOf(71400)));
        // una nota credito resta del neto
        POST("/api/facturacion/facturas/" + facturaId + "/notas-credito", admin(), Map.of("motivo", "Devolucion total"))
                .andExpect(status().isCreated());
        String conNota = json(GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy(), admin()));
        assertEquals(0, num(conNota, "$.totales.neto").compareTo(num(antes, "$.totales.neto")));
        assertEquals(0, num(conNota, "$.porItem[?(@.itemId==" + a + ")].total").compareTo(BigDecimal.ZERO));
        assertEquals(0, num(conNota, "$.totales.notasCredito").subtract(num(antes, "$.totales.notasCredito")).compareTo(BigDecimal.valueOf(71400)));
        // validaciones del rango
        GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy().minusDays(1), admin()).andExpect(status().isUnprocessableEntity());
        GET("/api/facturacion/reportes/ventas?desde=" + hoy().minusDays(400) + "&hasta=" + hoy(), admin()).andExpect(status().isUnprocessableEntity());
        GET("/api/facturacion/reportes/ventas?desde=" + hoy(), admin()).andExpect(status().isBadRequest());
        // dashboard: ventas visibles para ADMIN/RECEPCION; los demas campos intactos
        GET("/api/dashboard/resumen", admin()).andExpect(status().isOk()).andExpect(jsonPath("$.ventasHoy").isNumber())
                .andExpect(jsonPath("$.ventasMes").isNumber()).andExpect(jsonPath("$.citasHoy").isNumber());
        GET("/api/dashboard/resumen", caja.bearer()).andExpect(jsonPath("$.ventasHoy").isNumber());
        assertTrue(dashAntes.contains("ventasHoy"));
    }

    @Test
    void matrizDeRolesDeFacturacion() throws Exception {
        Usuario recepcion = usuario("RECEPCION");
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        // AUXILIAR: sin acceso a facturacion
        for (String ruta : List.of("/api/facturacion/facturas", "/api/facturacion/presupuestos", "/api/facturacion/caja",
                "/api/facturacion/cartera", "/api/facturacion/reportes/cartera", "/api/facturacion/resoluciones")) {
            GET(ruta, aux.bearer()).andExpect(status().isForbidden());
        }
        // VETERINARIO: solo lectura de presupuestos
        GET("/api/facturacion/presupuestos", vet.bearer()).andExpect(status().isOk());
        for (String ruta : List.of("/api/facturacion/facturas", "/api/facturacion/caja", "/api/facturacion/cartera",
                "/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy())) {
            GET(ruta, vet.bearer()).andExpect(status().isForbidden());
        }
        POST("/api/facturacion/pos/ventas", vet.bearer(), Map.of("items", lista(linea(1, 1)), "pagos", lista(pago("EFECTIVO", 1))))
                .andExpect(status().isForbidden());
        // RECEPCION: caja, facturas, cartera si; resoluciones, reporte de ventas y notas credito no
        for (String ruta : List.of("/api/facturacion/facturas", "/api/facturacion/presupuestos", "/api/facturacion/caja",
                "/api/facturacion/cartera", "/api/facturacion/reportes/cartera")) {
            GET(ruta, recepcion.bearer()).andExpect(status().isOk());
        }
        GET("/api/facturacion/resoluciones", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy(), recepcion.bearer()).andExpect(status().isForbidden());
        POST("/api/facturacion/facturas/1/notas-credito", recepcion.bearer(), Map.of("motivo", "x")).andExpect(status().isForbidden());
        // ADMIN todo
        GET("/api/facturacion/resoluciones", admin()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        GET("/api/facturacion/reportes/ventas?desde=" + hoy() + "&hasta=" + hoy(), admin()).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/facturacion/facturas"))
                .andExpect(status().isUnauthorized());
    }
}
