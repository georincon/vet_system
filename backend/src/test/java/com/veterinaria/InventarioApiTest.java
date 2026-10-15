package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class InventarioApiTest extends Fase4TestSupport {

    @Test
    void catalogoItemsCategoriasYBodegas() throws Exception {
        String admin = admin();
        long id = item("Amoxicilina test", 4500, 19, false, 10);
        GET("/api/inventario/items/" + id, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioVenta").value(4500.0)).andExpect(jsonPath("$.impuestoPct").value(19.0))
                .andExpect(jsonPath("$.stockMinimo").value(10.0)).andExpect(jsonPath("$.bajoMinimo").value(true))
                .andExpect(jsonPath("$.costo").exists());
        // edicion parcial: solo precio; el resto se conserva
        PUT("/api/inventario/items/" + id, admin, Map.of("precioVenta", 5000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioVenta").value(5000.0)).andExpect(jsonPath("$.impuestoPct").value(19.0));
        // codigo repetido -> 409; servicio con stock o controlado sin stock -> 422
        String codigo = JsonPath.read(json(GET("/api/inventario/items/" + id, admin)), "$.codigo");
        POST("/api/inventario/items", admin, Map.of("codigo", codigo, "nombre", "x", "tipo", "PRODUCTO"))
                .andExpect(status().isConflict());
        POST("/api/inventario/items", admin, Map.of("codigo", unico("SRV"), "nombre", "x", "tipo", "SERVICIO",
                "controlaStock", true)).andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/items", admin, Map.of("codigo", unico("CTL"), "nombre", "x", "tipo", "MEDICAMENTO",
                "esControlado", true)).andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/items", admin, Map.of("codigo", unico("BAD"), "nombre", "x", "tipo", "RARO"))
                .andExpect(status().isBadRequest());
        GET("/api/inventario/items?q=Amoxicilina test&tipo=MEDICAMENTO", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        GET("/api/inventario/items?esControlado=true&controlaStock=true&size=100", admin)
                .andExpect(jsonPath("$.items[?(@.codigo=='MED-KETA')]", hasSize(1)));
        // categorias y bodegas
        String nombre = unico("Cat");
        long cat = idDe(POST("/api/inventario/categorias", admin, Map.of("nombre", nombre, "tipo", "PRODUCTOS"))
                .andExpect(status().isCreated()));
        POST("/api/inventario/categorias", admin, Map.of("nombre", nombre, "tipo", "PRODUCTOS")).andExpect(status().isConflict());
        PUT("/api/inventario/items/" + id, admin, Map.of("categoriaId", cat)).andExpect(jsonPath("$.categoria").value(nombre));
        long bod = idDe(POST("/api/inventario/bodegas", admin, Map.of("nombre", unico("Bodega"), "tipo", "FARMACIA"))
                .andExpect(status().isCreated()));
        PUT("/api/inventario/bodegas/" + bod, admin, Map.of("activo", false)).andExpect(jsonPath("$.activo").value(false));
        // baja del item
        PUT("/api/inventario/items/" + id, admin, Map.of("activo", false)).andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void ordenDeCompraRecepcionParcialCreaLotesYEntradas() throws Exception {
        String admin = admin();
        long item = item("Producto compra", 9000);
        long prov = idDe(POST("/api/inventario/proveedores", admin, Map.of("numeroDocumento", unico("900"),
                "razonSocial", "Distribuidora Test SAS", "plazoPagoDias", 30)).andExpect(status().isCreated()));
        POST("/api/inventario/proveedores", admin, Map.of("numeroDocumento", "dup", "razonSocial", "A"))
                .andExpect(status().isCreated());
        POST("/api/inventario/proveedores", admin, Map.of("numeroDocumento", "dup", "razonSocial", "B"))
                .andExpect(status().isConflict());
        String orden = json(POST("/api/inventario/ordenes-compra", admin, Map.of("proveedorId", prov, "items", lista(
                Map.of("itemId", item, "cantidad", 100, "costoUnitario", 5000, "impuestoPct", 19))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.subtotal").value(500000.0)).andExpect(jsonPath("$.impuestos").value(95000.0))
                .andExpect(jsonPath("$.total").value(595000.0)));
        long ordenId = ((Number) JsonPath.read(orden, "$.id")).longValue();
        long renglon = ((Number) JsonPath.read(orden, "$.items[0].id")).longValue();
        // no se recibe una orden en BORRADOR
        POST("/api/inventario/ordenes-compra/" + ordenId + "/recepciones", admin, Map.of("items", lista(Map.of(
                "ordenCompraItemId", renglon, "cantidad", 10, "numeroLote", "L1", "fechaVencimiento",
                hoy().plusDays(200).toString())))).andExpect(status().isConflict());
        POST("/api/inventario/ordenes-compra/" + ordenId + "/enviar", admin, Map.of()).andExpect(jsonPath("$.estado").value("ENVIADA"));
        // recepcion parcial 60: crea lote, entrada y estado PARCIAL
        String rec = json(POST("/api/inventario/ordenes-compra/" + ordenId + "/recepciones", admin, Map.of(
                "numeroFacturaProveedor", "FP-" + ordenId, "items", lista(Map.of("ordenCompraItemId", renglon,
                        "cantidad", 60, "numeroLote", "LC-1", "fechaVencimiento", hoy().plusDays(200).toString()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ordenEstado").value("PARCIAL"))
                .andExpect(jsonPath("$.total").value(357000.0)).andExpect(jsonPath("$.items[0].numeroLote").value("LC-1")));
        long lote = ((Number) JsonPath.read(rec, "$.items[0].loteId")).longValue();
        assertEquals(0, cantidadLote(lote).compareTo(BigDecimal.valueOf(60)));
        GET("/api/inventario/lotes/" + lote, admin).andExpect(jsonPath("$.costoUnitario").value(5000.0))
                .andExpect(jsonPath("$.proveedorId").value(prov));
        GET("/api/inventario/items/" + item, admin).andExpect(jsonPath("$.costo").value(5000.0));
        GET("/api/inventario/movimientos?loteId=" + lote, admin).andExpect(jsonPath("$.items[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.items[0].delta").value(60.0)).andExpect(jsonPath("$.items[0].saldoLote").value(60.0))
                .andExpect(jsonPath("$.items[0].costoUnitario").value(5000.0))
                .andExpect(jsonPath("$.items[0].recepcionItemId").isNumber());
        // excede lo pendiente (40) y factura de proveedor repetida
        POST("/api/inventario/recepciones", admin, Map.of("ordenCompraId", ordenId, "items", lista(Map.of(
                "ordenCompraItemId", renglon, "cantidad", 41, "numeroLote", "LC-2", "fechaVencimiento",
                hoy().plusDays(100).toString())))).andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/recepciones", admin, Map.of("ordenCompraId", ordenId, "numeroFacturaProveedor", "FP-" + ordenId,
                "items", lista(Map.of("ordenCompraItemId", renglon, "cantidad", 1, "numeroLote", "LC-2",
                        "fechaVencimiento", hoy().plusDays(100).toString())))).andExpect(status().isConflict());
        // resto: RECIBIDA; lote vencido en la recepcion -> 422
        POST("/api/inventario/recepciones", admin, Map.of("ordenCompraId", ordenId, "items", lista(Map.of(
                "ordenCompraItemId", renglon, "cantidad", 40, "numeroLote", "LC-X", "fechaVencimiento",
                hoy().minusDays(1).toString())))).andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/recepciones", admin, Map.of("ordenCompraId", ordenId, "items", lista(Map.of(
                "ordenCompraItemId", renglon, "cantidad", 40, "numeroLote", "LC-2", "fechaVencimiento",
                hoy().plusDays(100).toString())))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordenEstado").value("RECIBIDA"));
        assertEquals(0, stockItem(item).compareTo(BigDecimal.valueOf(100)));
        GET("/api/inventario/ordenes-compra/" + ordenId, admin).andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.items[0].pendiente").value(0.0));
        POST("/api/inventario/ordenes-compra/" + ordenId + "/cancelar", admin, Map.of()).andExpect(status().isConflict());
        // recepcion directa sin orden; un lote existente se incrementa
        POST("/api/inventario/recepciones", admin, Map.of("proveedorId", prov, "items", lista(Map.of("itemId", item,
                "cantidad", 5, "numeroLote", "LC-2", "costoUnitario", 5200, "fechaVencimiento",
                hoy().plusDays(100).toString())))).andExpect(status().isCreated());
        assertEquals(0, stockItem(item).compareTo(BigDecimal.valueOf(105)));
        GET("/api/inventario/recepciones?ordenCompraId=" + ordenId, admin).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void salidaFefoConsumeElLoteQueVenceAntesYNuncaDejaStockNegativo() throws Exception {
        String admin = admin();
        long item = item("Producto FEFO", 1000);
        long tarde = cargar(item, "TARDE", 10, hoy().plusDays(300));
        long pronto = cargar(item, "PRONTO", 5, hoy().plusDays(20));
        long medio = cargar(item, "MEDIO", 5, hoy().plusDays(100));
        // 8 unidades: 5 de PRONTO y 3 de MEDIO; TARDE intacto
        Usuario aux = usuario("AUXILIAR");
        POST("/api/inventario/movimientos/uso", aux.bearer(), Map.of("itemId", item, "cantidad", 8, "observaciones", "curacion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lotes", hasSize(2)))
                .andExpect(jsonPath("$.lotes[0].numeroLote").value("PRONTO")).andExpect(jsonPath("$.lotes[0].cantidad").value(5.0))
                .andExpect(jsonPath("$.lotes[1].numeroLote").value("MEDIO")).andExpect(jsonPath("$.lotes[1].cantidad").value(3.0))
                .andExpect(jsonPath("$.stockRestante").value(12.0));
        assertEquals(0, cantidadLote(pronto).compareTo(BigDecimal.ZERO));
        assertEquals(0, cantidadLote(medio).compareTo(BigDecimal.valueOf(2)));
        assertEquals(0, cantidadLote(tarde).compareTo(BigDecimal.TEN));
        GET("/api/inventario/lotes/" + pronto, admin).andExpect(jsonPath("$.estado").value("AGOTADO"));
        // kardex con usuario, saldo y signo
        GET("/api/inventario/movimientos?itemId=" + item + "&tipo=SALIDA", aux.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].delta").value(-3.0))
                .andExpect(jsonPath("$.items[0].usuario.id").value(aux.id()))
                .andExpect(jsonPath("$.items[0].costoUnitario").doesNotExist());
        // stock nunca negativo: pedir 13 con 12 disponibles -> 409 y no cambia nada (transaccion completa)
        POST("/api/inventario/movimientos/uso", admin, Map.of("itemId", item, "cantidad", 13)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Stock insuficiente")));
        assertEquals(0, stockItem(item).compareTo(BigDecimal.valueOf(12)));
        // lote concreto sin existencias suficientes tambien 409
        POST("/api/inventario/movimientos/uso", admin, Map.of("loteId", medio, "cantidad", 3)).andExpect(status().isConflict());
        assertEquals(0, cantidadLote(medio).compareTo(BigDecimal.valueOf(2)));
        // salida con motivo (merma) exige motivo valido
        POST("/api/inventario/movimientos/salida", admin, Map.of("itemId", item, "cantidad", 1, "motivo", "MERMA",
                "observaciones", "se rompio")).andExpect(status().isOk());
        POST("/api/inventario/movimientos/salida", admin, Map.of("itemId", item, "cantidad", 1)).andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/movimientos/salida", admin, Map.of("itemId", item, "cantidad", 1, "motivo", "REGALO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loteVencidoNoSeUsaNiSeCuentaComoStockVigente() throws Exception {
        String admin = admin();
        long item = item("Producto vencido", 1000, 0, false, 5);
        long vencido = loteVencido(item, "OLD", 50);
        // solo hay stock vencido: FEFO lo ignora -> 409 mencionando los lotes vencidos
        POST("/api/inventario/movimientos/uso", admin, Map.of("itemId", item, "cantidad", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("vencidos")));
        // uso directo del lote vencido -> 422; solo se admite su salida por desecho
        POST("/api/inventario/movimientos/uso", admin, Map.of("loteId", vencido, "cantidad", 1)).andExpect(status().isUnprocessableEntity());
        GET("/api/inventario/lotes?itemId=" + item + "&soloVigentes=true", admin).andExpect(jsonPath("$", hasSize(0)));
        GET("/api/inventario/lotes?itemId=" + item, admin).andExpect(jsonPath("$[0].vencido").value(true))
                .andExpect(jsonPath("$[0].vigente").value(false));
        GET("/api/inventario/items/" + item, admin).andExpect(jsonPath("$.stockActual").value(0.0))
                .andExpect(jsonPath("$.stockTotal").value(50.0)).andExpect(jsonPath("$.bajoMinimo").value(true));
        // un lote vigente si se usa, el vencido sigue intacto aunque venza antes
        long vigente = cargar(item, "NEW", 10, hoy().plusDays(30));
        POST("/api/inventario/movimientos/uso", admin, Map.of("itemId", item, "cantidad", 4)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lotes[0].numeroLote").value("NEW"));
        assertEquals(0, cantidadLote(vencido).compareTo(BigDecimal.valueOf(50)));
        assertEquals(0, cantidadLote(vigente).compareTo(BigDecimal.valueOf(6)));
        // baja del lote vencido: movimiento VENCIMIENTO y estado VENCIDO; no se reactiva
        POST("/api/inventario/lotes/" + vencido + "/retirar", admin, Map.of("motivo", "Vencido en estanteria"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VENCIDO"))
                .andExpect(jsonPath("$.cantidadActual").value(0.0));
        GET("/api/inventario/movimientos?loteId=" + vencido + "&tipo=VENCIMIENTO", admin)
                .andExpect(jsonPath("$.items[0].delta").value(-50.0));
        POST("/api/inventario/lotes/" + vencido + "/estado", admin, Map.of("estado", "DISPONIBLE")).andExpect(status().isConflict());
        // cuarentena bloquea el uso
        POST("/api/inventario/lotes/" + vigente + "/estado", admin, Map.of("estado", "CUARENTENA")).andExpect(status().isOk());
        POST("/api/inventario/movimientos/uso", admin, Map.of("itemId", item, "cantidad", 1)).andExpect(status().isConflict());
        POST("/api/inventario/lotes/" + vigente + "/estado", admin, Map.of("estado", "DISPONIBLE")).andExpect(status().isOk());
        POST("/api/inventario/movimientos/uso", admin, Map.of("itemId", item, "cantidad", 1)).andExpect(status().isOk());
    }

    @Test
    void ajustePorConteoFisicoConMotivoYSinNegativos() throws Exception {
        String admin = admin();
        long item = item("Producto ajuste", 1000);
        long lote = cargar(item, "AJ-1", 20, hoy().plusDays(90));
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "cantidadReal", 17, "motivo", "Conteo fisico"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.anterior").value(20.0)).andExpect(jsonPath("$.actual").value(17.0))
                .andExpect(jsonPath("$.delta").value(-3.0));
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "delta", 2, "motivo", "Hallazgo"))
                .andExpect(jsonPath("$.actual").value(19.0));
        GET("/api/inventario/movimientos?loteId=" + lote + "&tipo=AJUSTE", admin).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].delta").value(2.0)).andExpect(jsonPath("$.items[1].delta").value(-3.0));
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "delta", -20, "motivo", "x")).andExpect(status().isConflict());
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "cantidadReal", 19, "motivo", "igual"))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "cantidadReal", 5, "delta", 1, "motivo", "x"))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "cantidadReal", 5)).andExpect(status().isBadRequest());
        assertEquals(0, cantidadLote(lote).compareTo(BigDecimal.valueOf(19)));
    }

    @Test
    void alertasBajoMinimoPorVencerYVencidos() throws Exception {
        String admin = admin();
        long bajo = item("Item bajo minimo", 1000, 0, false, 10);
        cargar(bajo, "B1", 4, hoy().plusDays(400));
        long proximo = item("Item por vencer", 1000, 0, false, 0);
        long loteProximo = cargar(proximo, "P1", 3, hoy().plusDays(10));
        long lejano = cargar(proximo, "P2", 3, hoy().plusDays(200));
        long expirado = item("Item expirado", 1000);
        long loteExpirado = loteVencido(expirado, "E1", 7);
        String res = json(GET("/api/inventario/alertas?dias=30", admin).andExpect(status().isOk()));
        assertTrue(((List<?>) JsonPath.read(res, "$.bajoMinimo[?(@.itemId==" + bajo + ")]")).size() == 1);
        assertEquals(0, num(res, "$.bajoMinimo[?(@.itemId==" + bajo + ")].faltante").compareTo(BigDecimal.valueOf(6)));
        assertEquals(1, ((List<?>) JsonPath.read(res, "$.porVencer[?(@.loteId==" + loteProximo + ")]")).size());
        assertEquals(0, ((List<?>) JsonPath.read(res, "$.porVencer[?(@.loteId==" + lejano + ")]")).size());
        assertEquals(1, ((List<?>) JsonPath.read(res, "$.vencidos[?(@.loteId==" + loteExpirado + ")]")).size());
        // con 365 dias el lote lejano tambien entra; el vencido nunca esta en porVencer
        String largo = json(GET("/api/inventario/alertas?dias=365", admin));
        assertEquals(1, ((List<?>) JsonPath.read(largo, "$.porVencer[?(@.loteId==" + lejano + ")]")).size());
        assertEquals(0, ((List<?>) JsonPath.read(largo, "$.porVencer[?(@.loteId==" + loteExpirado + ")]")).size());
        GET("/api/inventario/alertas?dias=-1", admin).andExpect(status().isUnprocessableEntity());
        // dashboard (campos nuevos, sin romper los anteriores)
        GET("/api/dashboard/resumen", usuario("VETERINARIO").bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBajo").isNumber()).andExpect(jsonPath("$.lotesPorVencer").isNumber())
                .andExpect(jsonPath("$.ventasHoy").doesNotExist()).andExpect(jsonPath("$.citasHoy").isNumber());
    }

    @Test
    void selectorDeLotesPorVacunaYDescuentoDesdeInmunizacion() throws Exception {
        String admin = admin();
        // sembrado V28: item VAC-CAN-RAB vinculado por convencion de codigo
        long vacuna = vacunaId("CAN-RAB");
        long itemVacuna = jdbc.sql("select id from item where codigo = 'VAC-CAN-RAB'").query(Long.class).single();
        long vigente = cargar(itemVacuna, unico("RAB"), 5, hoy().plusDays(180));
        long proximo = cargar(itemVacuna, unico("RAB"), 5, hoy().plusDays(30));
        loteVencido(itemVacuna, unico("RABOLD"), 3);
        long otro = item("Otro producto", 1000);
        cargar(otro, unico("X"), 5, hoy().plusDays(10));
        // soloVigentes=true: sin el vencido, en orden FEFO, solo del item de la vacuna
        GET("/api/inventario/lotes?vacunaId=" + vacuna + "&soloVigentes=true", usuario("AUXILIAR").bearer())
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(proximo)).andExpect(jsonPath("$[1].id").value(vigente))
                .andExpect(jsonPath("$[0].itemCodigo").value("VAC-CAN-RAB")).andExpect(jsonPath("$[0].diasParaVencer").value(30))
                .andExpect(jsonPath("$[0].costoUnitario").doesNotExist());
        GET("/api/inventario/lotes?vacunaId=" + vacuna, admin).andExpect(jsonPath("$", hasSize(3)));
        GET("/api/inventario/lotes?vacunaId=" + vacunaId("CAN-LYME") + "&soloVigentes=true", admin).andExpect(jsonPath("$", hasSize(0)));
        // aplicar la vacuna con ese lote descuenta via inventario (kardex + libro de estado) y anular devuelve
        Caso caso = caso(true, hoy().minusYears(2));
        long insumo = idDe(POST("/api/pacientes/" + caso.pacienteId() + "/inmunizaciones", admin,
                aplicacionVacuna(vacuna, hoy(), Map.of("loteId", proximo))).andExpect(status().isCreated()));
        assertEquals(0, cantidadLote(proximo).compareTo(BigDecimal.valueOf(4)));
        GET("/api/inventario/movimientos?loteId=" + proximo + "&referencia=INMUNIZACION", admin)
                .andExpect(jsonPath("$.items[0].tipo").value("SALIDA")).andExpect(jsonPath("$.items[0].pacienteId").value(caso.pacienteId()));
        POST("/api/pacientes/" + caso.pacienteId() + "/inmunizaciones/" + insumo + "/anular", admin, Map.of("motivo", "error"))
                .andExpect(status().isOk());
        assertEquals(0, cantidadLote(proximo).compareTo(BigDecimal.valueOf(5)));
        // ahora el catalogo enlaza explicitamente la vacuna a otro item: el selector lo sigue
        long otroItem = item("Rabia especial", 30000);
        long loteOtro = cargar(otroItem, unico("RE"), 2, hoy().plusDays(60));
        PUT("/api/inmunizacion/vacunas/" + vacuna, admin, Map.of("itemId", otroItem)).andExpect(status().isOk());
        GET("/api/inventario/lotes?vacunaId=" + vacuna + "&soloVigentes=true", admin).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(loteOtro));
        // y la validacion de inmunizacion rechaza ahora un lote de otro producto
        POST("/api/pacientes/" + caso.pacienteId() + "/inmunizaciones", admin, aplicacionVacuna(vacuna, hoy(),
                Map.of("loteId", proximo))).andExpect(status().isUnprocessableEntity());
        PUT("/api/inmunizacion/vacunas/" + vacuna, admin, Map.of("itemId", itemVacuna)).andExpect(status().isOk());
    }

    @Test
    void librosDeControladosRegistranEntradasYSalidasConPaciente() throws Exception {
        String admin = admin();
        long ketamina = item("Controlado test", 12000, 0, true, 0);
        long lote = cargar(ketamina, "KET-1", 10, hoy().plusDays(200));
        long paciente = paciente();
        // salida sin paciente -> 422 (un controlado exige trazabilidad por paciente)
        Usuario vet = usuario("VETERINARIO");
        POST("/api/inventario/movimientos/uso", vet.bearer(), Map.of("itemId", ketamina, "cantidad", 2))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message", containsString("controlado")));
        POST("/api/inventario/movimientos/uso", vet.bearer(), Map.of("itemId", ketamina, "cantidad", 2, "pacienteId", paciente))
                .andExpect(status().isOk());
        // merma de controlado queda como DESECHO (no exige paciente)
        POST("/api/inventario/movimientos/salida", admin, Map.of("itemId", ketamina, "cantidad", 1, "motivo", "DESECHO"))
                .andExpect(status().isOk());
        POST("/api/inventario/movimientos/ajuste", admin, Map.of("loteId", lote, "delta", -1, "motivo", "Conteo")).andExpect(status().isOk());
        String libro = json(GET("/api/inventario/controlados?itemId=" + ketamina + "&size=50", vet.bearer())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4)));
        // orden descendente: AJUSTE, DESECHO, SALIDA, ENTRADA con saldo corriente 6, 7, 8, 10
        assertEquals("AJUSTE", JsonPath.read(libro, "$.items[0].tipo"));
        assertEquals("DESECHO", JsonPath.read(libro, "$.items[1].tipo"));
        assertEquals("SALIDA", JsonPath.read(libro, "$.items[2].tipo"));
        assertEquals(paciente, ((Number) JsonPath.read(libro, "$.items[2].pacienteId")).longValue());
        assertEquals("ENTRADA", JsonPath.read(libro, "$.items[3].tipo"));
        assertEquals(0, num(libro, "$.items[0].saldo").compareTo(BigDecimal.valueOf(6)));
        assertEquals(0, num(libro, "$.items[3].saldo").compareTo(BigDecimal.TEN));
        // el libro es append-only (trigger)
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.sql("update registro_controlado set cantidad = 99").update());
        // lectura del libro: ADMIN y VETERINARIO; no AUXILIAR ni RECEPCION
        GET("/api/inventario/controlados", usuario("AUXILIAR").bearer()).andExpect(status().isForbidden());
        GET("/api/inventario/controlados", usuario("RECEPCION").bearer()).andExpect(status().isForbidden());
    }

    @Test
    void matrizDeRolesDeInventario() throws Exception {
        long item = item("Item roles", 1000);
        long lote = cargar(item, unico("R"), 10, hoy().plusDays(60));
        Usuario recepcion = usuario("RECEPCION");
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        for (Usuario u : List.of(recepcion, vet, aux)) {
            GET("/api/inventario/items", u.bearer()).andExpect(status().isOk());
            GET("/api/inventario/lotes?itemId=" + item, u.bearer()).andExpect(status().isOk());
            GET("/api/inventario/movimientos", u.bearer()).andExpect(status().isOk());
            GET("/api/inventario/alertas", u.bearer()).andExpect(status().isOk());
            GET("/api/inventario/bodegas", u.bearer()).andExpect(status().isOk());
            // escritura, compras y proveedores: solo ADMIN
            POST("/api/inventario/items", u.bearer(), Map.of("codigo", unico("NO"), "nombre", "x", "tipo", "PRODUCTO"))
                    .andExpect(status().isForbidden());
            PUT("/api/inventario/items/" + item, u.bearer(), Map.of("precioVenta", 1)).andExpect(status().isForbidden());
            POST("/api/inventario/lotes", u.bearer(), Map.of("itemId", item)).andExpect(status().isForbidden());
            POST("/api/inventario/movimientos/ajuste", u.bearer(), Map.of("loteId", lote, "delta", 1, "motivo", "x"))
                    .andExpect(status().isForbidden());
            POST("/api/inventario/movimientos/salida", u.bearer(), Map.of("itemId", item, "cantidad", 1, "motivo", "MERMA"))
                    .andExpect(status().isForbidden());
            GET("/api/inventario/proveedores", u.bearer()).andExpect(status().isForbidden());
            GET("/api/inventario/ordenes-compra", u.bearer()).andExpect(status().isForbidden());
            GET("/api/inventario/recepciones", u.bearer()).andExpect(status().isForbidden());
        }
        // uso clinico: VET y AUX si; RECEPCION no
        POST("/api/inventario/movimientos/uso", vet.bearer(), Map.of("itemId", item, "cantidad", 1)).andExpect(status().isOk());
        POST("/api/inventario/movimientos/uso", aux.bearer(), Map.of("itemId", item, "cantidad", 1)).andExpect(status().isOk());
        POST("/api/inventario/movimientos/uso", recepcion.bearer(), Map.of("itemId", item, "cantidad", 1)).andExpect(status().isForbidden());
        // costos ocultos a quien no es ADMIN
        GET("/api/inventario/items/" + item, recepcion.bearer()).andExpect(jsonPath("$.costo").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/inventario/items"))
                .andExpect(status().isUnauthorized());
    }
}
