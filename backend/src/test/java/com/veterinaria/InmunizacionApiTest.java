package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.veterinaria.clinica.ConsultaCerrada;
import com.veterinaria.inmunizacion.application.InmunizacionService;

class InmunizacionApiTest extends Fase3TestSupport {

    @Autowired
    InmunizacionService inmunizacionService;

    private String url(long paciente) {
        return "/api/pacientes/" + paciente + "/inmunizaciones";
    }

    @Test
    void proximaFechaSegunEsquemaDosisYEdad() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long dhpp = vacunaId("CAN-DHPP");
        // cachorro de 70 dias: dosis 1 -> dosis 2 a +21 dias (la edad minima de 9 semanas ya se cumple)
        Caso a = caso(true, hoy().minusDays(70));
        POST(url(a.pacienteId()), vet.bearer(), aplicacionVacuna(dhpp, hoy(), Map.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dosisNumero").value(1))
                .andExpect(jsonPath("$.dosisNombre").value("Dosis 1"))
                .andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(21).toString()))
                .andExpect(jsonPath("$.veterinario.id").value(vet.id()))
                .andExpect(jsonPath("$.estado").value("APLICADA"));
        // la dosis siguiente se numera sola y usa el intervalo de la dosis 3 (21 dias)
        POST(url(a.pacienteId()), vet.bearer(), aplicacionVacuna(dhpp, hoy(), Map.of()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dosisNumero").value(2))
                .andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(21).toString()));
        // dosis 4 -> refuerzo a 12 meses (365); dosis 5 (refuerzo) -> repite cada 3 anios
        POST(url(a.pacienteId()), vet.bearer(), aplicacionVacuna(dhpp, hoy(), Map.of("dosisNumero", 4)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(365).toString()));
        POST(url(a.pacienteId()), vet.bearer(), aplicacionVacuna(dhpp, hoy(), Map.of("dosisNumero", 5)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(1095).toString()));

        // la edad minima manda: nacido hace 28 dias, la dosis 2 no puede ir antes de las 9 semanas (35 dias mas)
        Caso b = caso(true, hoy().minusDays(28));
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(dhpp, hoy(), Map.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximaFecha").value(hoy().minusDays(28).plusWeeks(9).toString()));

        // rabia: refuerzo anual; proxima manual si el veterinario la indica
        long rab = vacunaId("CAN-RAB");
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(365).toString()));
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(),
                Map.of("proximaFecha", hoy().plusDays(10).toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(10).toString()));
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(),
                Map.of("proximaFecha", hoy().minusDays(1).toString()))).andExpect(status().isUnprocessableEntity());
        // futura o sin producto
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy().plusDays(1), Map.of()))
                .andExpect(status().isUnprocessableEntity());
        POST(url(b.pacienteId()), vet.bearer(), Map.of("tipo", "VACUNA")).andExpect(status().isUnprocessableEntity());
        POST(url(b.pacienteId()), vet.bearer(), Map.of("vacunaId", rab)).andExpect(status().isBadRequest());
        // especie incompatible (vacuna felina en un perro)
        POST(url(b.pacienteId()), vet.bearer(), aplicacionVacuna(vacunaId("FEL-RAB"), hoy(), Map.of()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void antiparasitarioCalculaProximaPorIntervaloYRegistraLote() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Caso c = caso(true, hoy().minusDays(400));
        long ap = antiparasitarioId("ANT-PZQ");
        POST(url(c.pacienteId()), vet.bearer(), Map.of("tipo", "ANTIPARASITARIO", "antiparasitarioId", ap,
                "fechaAplicacion", hoy().toString(), "dosis", "1 tableta", "lote", "ANT-77",
                "fechaVencimientoLote", hoy().plusDays(100).toString(), "pesoKg", 12.5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ANTIPARASITARIO"))
                .andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(90).toString()))
                .andExpect(jsonPath("$.lote").value("ANT-77"))
                .andExpect(jsonPath("$.clasificacion").value("INTERNA"));
        // lote vencido en texto libre
        POST(url(c.pacienteId()), vet.bearer(), Map.of("tipo", "ANTIPARASITARIO", "antiparasitarioId", ap,
                "lote", "VIEJO", "fechaVencimientoLote", hoy().minusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity());
        GET(url(c.pacienteId()) + "?tipo=ANTIPARASITARIO", vet.bearer()).andExpect(jsonPath("$", hasSize(1)));
        GET(url(c.pacienteId()) + "?tipo=VACUNA", vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void loteVencidoDa422StockAgotadoDa409YSeDescuentaYDevuelve() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Caso c = caso(true, null);
        long rab = vacunaId("CAN-RAB");

        long vencido = lote("VENC-1", 5, hoy().minusDays(1));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of("loteId", vencido)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("vencido")));
        org.junit.jupiter.api.Assertions.assertEquals(0, stock(vencido).compareTo(java.math.BigDecimal.valueOf(5)));

        long agotado = lote("AGOT-1", 0, hoy().plusDays(90));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of("loteId", agotado)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("Stock")));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of("loteId", 99999999)))
                .andExpect(status().isNotFound());

        long bueno = lote("OK-1", 1, hoy().plusDays(90));
        long id = idDe(POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of("loteId", bueno)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lote").value("OK-1"))
                .andExpect(jsonPath("$.loteId").value(bueno))
                .andExpect(jsonPath("$.fechaVencimientoLote").value(hoy().plusDays(90).toString())));
        Assertions.assertEquals(0, stock(bueno).signum());
        // sin stock: el siguiente intento es 409 (no queda negativo)
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of("loteId", bueno)))
                .andExpect(status().isConflict());
        Assertions.assertEquals(0, stock(bueno).signum());
        Assertions.assertEquals(1L, jdbc.sql("select count(*) from movimiento_stock where lote_id = :l and tipo = 'SALIDA'")
                .param("l", bueno).query(Long.class).single());

        // anular devuelve la dosis al lote y deja el registro
        POST(url(c.pacienteId()) + "/" + id + "/anular", vet.bearer(), Map.of("motivo", "Se aplico otra vacuna"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.motivoAnulacion").value("Se aplico otra vacuna"));
        Assertions.assertEquals(0, stock(bueno).compareTo(java.math.BigDecimal.ONE));
        POST(url(c.pacienteId()) + "/" + id + "/anular", vet.bearer(), Map.of("motivo", "otra vez"))
                .andExpect(status().isConflict());
        GET(url(c.pacienteId()), vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
        GET(url(c.pacienteId()) + "?incluirAnuladas=true", vet.bearer()).andExpect(jsonPath("$", hasSize(1)));
        POST(url(c.pacienteId()) + "/" + id + "/anular", vet.bearer(), Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void rolesRegistroAnulacionYConsulta() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        Caso c = caso(true, null);
        long rab = vacunaId("CAN-RAB");
        // auxiliar registra solo indicando el veterinario supervisor
        POST(url(c.pacienteId()), aux.bearer(), aplicacionVacuna(rab, hoy(), Map.of()))
                .andExpect(status().isUnprocessableEntity());
        POST(url(c.pacienteId()), aux.bearer(), aplicacionVacuna(rab, hoy(), Map.of("veterinarioId", aux.id())))
                .andExpect(status().isUnprocessableEntity());
        long id = idDe(POST(url(c.pacienteId()), aux.bearer(), aplicacionVacuna(rab, hoy(), Map.of("veterinarioId", vet.id())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.veterinario.id").value(vet.id())));
        // auxiliar no anula; recepcion no accede a datos clinicos
        POST(url(c.pacienteId()) + "/" + id + "/anular", aux.bearer(), Map.of("motivo", "x")).andExpect(status().isForbidden());
        POST(url(c.pacienteId()), recepcion.bearer(), aplicacionVacuna(rab, hoy(), Map.of())).andExpect(status().isForbidden());
        GET(url(c.pacienteId()), recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/pacientes/" + c.pacienteId() + "/esquema-sugerido", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/pacientes/" + c.pacienteId() + "/carnet.pdf", recepcion.bearer()).andExpect(status().isForbidden());
        GET(url(c.pacienteId()), aux.bearer()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        // admin registra y anula
        long adminId = idDe(POST(url(c.pacienteId()), admin(), aplicacionVacuna(rab, hoy(), Map.of()))
                .andExpect(status().isCreated()));
        POST(url(c.pacienteId()) + "/" + adminId + "/anular", admin(), Map.of("motivo", "Error de digitacion"))
                .andExpect(status().isOk());
        // sin token
        mockMvc.perform(get(url(c.pacienteId()))).andExpect(status().isUnauthorized());
        GET("/api/pacientes/999999/inmunizaciones", vet.bearer()).andExpect(status().isNotFound());
        // la auditoria por trigger registra quien aplico
        Assertions.assertTrue(jdbc.sql("select count(*) from auditoria where entidad = 'inmunizacion' and entidad_id = :id and usuario_id = :u")
                .param("id", id).param("u", aux.id()).query(Long.class).single() > 0);
    }

    @Test
    void eventoDeCierreGeneraInmunizacionSinDuplicarEnLaHistoria() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Caso c = caso(true, hoy().minusDays(300));
        String hace2 = hoy().minusDays(2).toString();
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", c.pacienteId(),
                "veterinarioId", vet.id(), "fecha", hace2, "motivo", "Vacunacion",
                "vacunas", List.of(
                        Map.of("nombre", "Antirrabica canina", "lote", "LR-9", "fechaAplicacion", hace2),
                        Map.of("nombre", "Vacuna rara de prueba", "lote", "LX-1", "fechaAplicacion", hace2,
                                "proximaFecha", hoy().plusDays(40).toString()))))
                .andExpect(status().isCreated()));
        // abierta: aun no hay inmunizaciones, la historia muestra los renglones de la consulta
        GET(url(c.pacienteId()), vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
        GET("/api/pacientes/" + c.pacienteId() + "/historia?tipo=VACUNA", vet.bearer())
                .andExpect(jsonPath("$.totalElements").value(2));

        POST("/api/consultas/" + consulta + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());

        String lista = json(GET(url(c.pacienteId()), vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))));
        Assertions.assertEquals("LR-9", primerValor(lista, "$[?(@.codigoProducto=='CAN-RAB')].lote"));
        Assertions.assertEquals(hoy().minusDays(2).plusDays(365).toString(),
                primerValor(lista, "$[?(@.codigoProducto=='CAN-RAB')].proximaFecha"));
        Assertions.assertEquals(hoy().plusDays(40).toString(),
                primerValor(lista, "$[?(@.producto=='Vacuna rara de prueba')].proximaFecha"));
        // la vacuna fuera de catalogo se incorporo (NO_NUCLEO) y la consulta cerrada no cambio
        Assertions.assertEquals("NO_NUCLEO", primerValor(lista, "$[?(@.producto=='Vacuna rara de prueba')].clasificacion"));
        GET("/api/consultas/" + consulta, vet.bearer()).andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.vacunas", hasSize(2)));

        // historia: cada vacuna una sola vez (no duplicada por consulta_vacuna + inmunizacion)
        GET("/api/pacientes/" + c.pacienteId() + "/historia?tipo=VACUNA", vet.bearer())
                .andExpect(jsonPath("$.totalElements").value(2));

        // idempotente: reentregar el mismo evento no crea nada
        List<Long> cvIds = jdbc.sql("select id from consulta_vacuna where consulta_id = :c order by id")
                .param("c", consulta).query(Long.class).list();
        ConsultaCerrada evento = new ConsultaCerrada(consulta, c.pacienteId(), vet.id(), hoy().minusDays(2),
                cvIds.stream().map(i -> new ConsultaCerrada.VacunaAplicada(i, "Antirrabica canina", "LR-9",
                        hoy().minusDays(2), null)).toList());
        Assertions.assertEquals(0, inmunizacionService.registrarDesdeConsulta(evento));
        GET(url(c.pacienteId()), vet.bearer()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void pendientesVencidasYPorVencerConTutorYDashboardReal() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario recepcion = usuario("RECEPCION");
        long antes = ((Number) JsonPath.read(json(GET("/api/dashboard/resumen", vet.bearer())), "$.vacunasPorVencer")).longValue();
        Caso vencida = caso(true, null);
        Caso porVencer = caso(false, null);
        long rab = vacunaId("CAN-RAB");
        POST(url(vencida.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy().minusDays(20),
                Map.of("proximaFecha", hoy().minusDays(3).toString()))).andExpect(status().isCreated());
        POST(url(porVencer.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy().minusDays(20),
                Map.of("proximaFecha", hoy().plusDays(10).toString()))).andExpect(status().isCreated());
        POST(url(porVencer.pacienteId()), vet.bearer(), Map.of("tipo", "ANTIPARASITARIO",
                "antiparasitarioId", antiparasitarioId("ANT-ISOX"), "fechaAplicacion", hoy().minusDays(25).toString()))
                .andExpect(status().isCreated());

        String todas = json(GET("/api/inmunizacion/pendientes?size=200", recepcion.bearer()).andExpect(status().isOk()));
        Assertions.assertEquals("VENCIDA", primerValor(todas, "$.items[?(@.pacienteId==" + vencida.pacienteId() + ")].estado"));
        Assertions.assertEquals("-3", primerValor(todas, "$.items[?(@.pacienteId==" + vencida.pacienteId() + ")].diasRestantes"));
        Assertions.assertEquals(vencida.email(), primerValor(todas, "$.items[?(@.pacienteId==" + vencida.pacienteId() + ")].tutor.email"));
        Assertions.assertEquals("true", primerValor(todas, "$.items[?(@.pacienteId==" + vencida.pacienteId() + ")].tutor.aceptaEmail"));
        Assertions.assertEquals("false", primerValor(todas, "$.items[?(@.pacienteId==" + porVencer.pacienteId() + " && @.tipo=='VACUNA')].tutor.aceptaEmail"));
        // la antiparasitaria (isoxazolina, 30 dias desde hace 25) vence en 5 dias
        Assertions.assertEquals("5", primerValor(todas, "$.items[?(@.pacienteId==" + porVencer.pacienteId() + " && @.tipo=='ANTIPARASITARIO')].diasRestantes"));

        GET("/api/inmunizacion/pendientes?estado=VENCIDA&size=200", vet.bearer())
                .andExpect(jsonPath("$.items[?(@.pacienteId==" + porVencer.pacienteId() + ")]", hasSize(0)));
        GET("/api/inmunizacion/pendientes?tipo=ANTIPARASITARIO&size=200", vet.bearer())
                .andExpect(jsonPath("$.items[?(@.tipo=='VACUNA')]", hasSize(0)));
        GET("/api/inmunizacion/pendientes?desde=" + hoy() + "&hasta=" + hoy().plusDays(7) + "&size=200", vet.bearer())
                .andExpect(jsonPath("$.items[?(@.pacienteId==" + porVencer.pacienteId() + " && @.tipo=='VACUNA')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.pacienteId==" + porVencer.pacienteId() + " && @.tipo=='ANTIPARASITARIO')]", hasSize(1)));
        GET("/api/inmunizacion/pendientes?size=1", vet.bearer()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.first").value(true));
        GET("/api/inmunizacion/pendientes?estado=XX", vet.bearer()).andExpect(status().isUnprocessableEntity());
        GET("/api/inmunizacion/pendientes?tipo=OTRO", vet.bearer()).andExpect(status().isBadRequest());

        // dashboard: 2 nuevas por vencer (vacuna +10 y antiparasitaria +5); la vencida no cuenta
        long despues = ((Number) JsonPath.read(json(GET("/api/dashboard/resumen", vet.bearer())), "$.vacunasPorVencer")).longValue();
        Assertions.assertEquals(antes + 2, despues);

        // una dosis posterior reemplaza a la anterior: la vencida deja de estar pendiente (la nueva vence en +365)
        POST(url(vencida.pacienteId()), vet.bearer(), aplicacionVacuna(rab, hoy(), Map.of())).andExpect(status().isCreated());
        GET("/api/inmunizacion/pendientes?size=200", vet.bearer())
                .andExpect(jsonPath("$.items[?(@.pacienteId==" + vencida.pacienteId() + ")]", hasSize(0)));
        // si se anula esa ultima, reaparece la anterior como vencida
        GET("/api/pacientes/" + vencida.pacienteId() + "/inmunizaciones", vet.bearer()).andExpect(jsonPath("$", hasSize(2)));
        long ultima = primerId(json(GET(url(vencida.pacienteId()), vet.bearer())), "$[?(@.fechaAplicacion=='" + hoy() + "')].id");
        POST(url(vencida.pacienteId()) + "/" + ultima + "/anular", vet.bearer(), Map.of("motivo", "duplicada"))
                .andExpect(status().isOk());
        GET("/api/inmunizacion/pendientes?size=200", vet.bearer())
                .andExpect(jsonPath("$.items[?(@.pacienteId==" + vencida.pacienteId() + ")].estado").value("VENCIDA"));
    }

    @Test
    void esquemaSugeridoSegunEdadYAplicadas() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Caso cachorro = caso(true, hoy().minusWeeks(2));
        String s = json(GET("/api/pacientes/" + cachorro.pacienteId() + "/esquema-sugerido", vet.bearer())
                .andExpect(status().isOk()).andExpect(jsonPath("$.especie").value("Perro"))
                .andExpect(jsonPath("$.edadSemanas").value(2)));
        // a las 2 semanas la polivalente (6 sem) aun no toca: FUTURA/PROXIMA con fecha = nacimiento + 6 semanas
        Assertions.assertEquals(hoy().minusWeeks(2).plusWeeks(6).toString(),
                primerValor(s, "$.items[?(@.esquema=='Cachorro: polivalente nucleo')].fechaSugerida"));
        Assertions.assertEquals("PROXIMA", primerValor(s, "$.items[?(@.esquema=='Cachorro: polivalente nucleo')].estado"));
        // solo esquemas de perro
        List<String> vacunas = JsonPath.read(s, "$.items[*].vacuna");
        Assertions.assertTrue(vacunas.stream().noneMatch(v -> v.toLowerCase().contains("felina")));

        // adulto sin aplicar: toca ahora; tras aplicar rabia, queda al dia y la serie sigue en la dosis 2
        Caso adulto = caso(true, hoy().minusYears(3));
        String a1 = json(GET("/api/pacientes/" + adulto.pacienteId() + "/esquema-sugerido", vet.bearer()));
        Assertions.assertEquals("TOCA_AHORA", primerValor(a1, "$.items[?(@.esquema=='Perro: antirrabica')].estado"));
        Assertions.assertEquals("Primovacunacion", primerValor(a1, "$.items[?(@.esquema=='Perro: antirrabica')].proximaDosisNombre"));
        POST(url(adulto.pacienteId()), vet.bearer(), aplicacionVacuna(vacunaId("CAN-RAB"), hoy(), Map.of()))
                .andExpect(status().isCreated());
        String a2 = json(GET("/api/pacientes/" + adulto.pacienteId() + "/esquema-sugerido", vet.bearer()));
        Assertions.assertEquals("AL_DIA", primerValor(a2, "$.items[?(@.esquema=='Perro: antirrabica')].estado"));
        Assertions.assertEquals("Refuerzo anual", primerValor(a2, "$.items[?(@.esquema=='Perro: antirrabica')].proximaDosisNombre"));
        Assertions.assertEquals("1", primerValor(a2, "$.items[?(@.esquema=='Perro: antirrabica')].dosisAplicadas"));
        Assertions.assertEquals(hoy().plusDays(365).toString(),
                primerValor(a2, "$.items[?(@.esquema=='Perro: antirrabica')].fechaSugerida"));
    }

    @Test
    void carnetPdfEsValidoYContieneLosDatos() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Caso c = caso(true, hoy().minusYears(1));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(vacunaId("CAN-RAB"), hoy(),
                Map.of("lote", "LOTE-CARNET-7", "fechaVencimientoLote", hoy().plusDays(200).toString()))).andExpect(status().isCreated());
        POST(url(c.pacienteId()), vet.bearer(), Map.of("tipo", "ANTIPARASITARIO",
                "antiparasitarioId", antiparasitarioId("ANT-SELA"), "fechaAplicacion", hoy().toString()))
                .andExpect(status().isCreated());

        byte[] pdf = GET("/api/pacientes/" + c.pacienteId() + "/carnet.pdf", vet.bearer()).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        Assertions.assertEquals("%PDF-", new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        Assertions.assertTrue(new String(pdf, Math.max(0, pdf.length - 20), Math.min(20, pdf.length),
                java.nio.charset.StandardCharsets.US_ASCII).contains("%%EOF"));
        PdfReader reader = new PdfReader(pdf);
        Assertions.assertTrue(reader.getNumberOfPages() >= 1);
        String texto = new PdfTextExtractor(reader).getTextFromPage(1);
        Assertions.assertTrue(texto.contains("LOTE-CARNET-7"), texto);
        Assertions.assertTrue(texto.contains("Fase3-"), texto);
        Assertions.assertTrue(texto.contains("Firma"), texto);
        Assertions.assertTrue(texto.contains("Selamectina"), texto);
        Assertions.assertEquals(1L, jdbc.sql("select count(*) from certificado_vacunacion where paciente_id = :p and hash_documento is not null")
                .param("p", c.pacienteId()).query(Long.class).single());
        GET("/api/pacientes/999999/carnet.pdf", vet.bearer()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pacientes/" + c.pacienteId() + "/carnet.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogosCrudSoloAdminYEsquemaConDosis() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario recepcion = usuario("RECEPCION");
        // lectura para todos los autenticados
        GET("/api/inmunizacion/vacunas", recepcion.bearer()).andExpect(status().isOk());
        GET("/api/inmunizacion/vacunas?especieId=" + primerId(json(GET("/api/catalogos/especies", admin())),
                "$[?(@.nombre=='Gato')].id"), vet.bearer()).andExpect(jsonPath("$[?(@.codigo=='FEL-RAB')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.codigoProducto=='CAN-RAB')]", hasSize(0)));
        GET("/api/inmunizacion/antiparasitarios", vet.bearer()).andExpect(jsonPath("$[?(@.codigo=='ANT-IVER')].tipo").value("DIROFILARIA"));
        GET("/api/inmunizacion/esquemas", vet.bearer()).andExpect(status().isOk());

        Map<String, Object> nueva = Map.of("codigo", "TST-VAC", "nombre", "Vacuna de prueba", "periodoEficaciaDias", 180,
                "clasificacion", "NO_NUCLEO");
        POST("/api/inmunizacion/vacunas", vet.bearer(), nueva).andExpect(status().isForbidden());
        long id = idDe(POST("/api/inmunizacion/vacunas", admin(), nueva).andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true)));
        POST("/api/inmunizacion/vacunas", admin(), nueva).andExpect(status().isConflict());
        POST("/api/inmunizacion/vacunas", admin(), Map.of("codigo", "X")).andExpect(status().isBadRequest());
        PUT("/api/inmunizacion/vacunas/" + id, admin(), Map.of("nombre", "Renombrada", "periodoEficaciaDias", 200))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Renombrada"))
                .andExpect(jsonPath("$.codigo").value("TST-VAC")).andExpect(jsonPath("$.periodoEficaciaDias").value(200));
        PUT("/api/inmunizacion/vacunas/" + id, admin(), Map.of("clasificacion", "OTRA")).andExpect(status().isBadRequest());
        PUT("/api/inmunizacion/vacunas/" + id, recepcion.bearer(), Map.of("nombre", "x")).andExpect(status().isForbidden());
        PUT("/api/inmunizacion/vacunas/999999", admin(), Map.of("nombre", "x")).andExpect(status().isNotFound());

        long ap = idDe(POST("/api/inmunizacion/antiparasitarios", admin(), Map.of("codigo", "TST-AP", "nombre", "AP prueba",
                "tipo", "EXTERNA", "intervaloDias", 45)).andExpect(status().isCreated()));
        PUT("/api/inmunizacion/antiparasitarios/" + ap, admin(), Map.of("activo", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        Caso c = caso(true, null);
        POST(url(c.pacienteId()), vet.bearer(), Map.of("tipo", "ANTIPARASITARIO", "antiparasitarioId", ap))
                .andExpect(status().isUnprocessableEntity());

        // esquema nuevo con 2 dosis; sin proxima por esquema usa el calendario
        long esq = idDe(POST("/api/inmunizacion/esquemas", admin(), Map.of("codigo", "ESQ-TST", "nombre", "Esquema de prueba",
                "vacunaId", id, "dosis", List.of(
                        Map.of("orden", 1, "nombreDosis", "Primera"),
                        Map.of("orden", 2, "nombreDosis", "Segunda", "intervaloDiasPrevia", 15, "repetirCadaDias", 90))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dosis", hasSize(2))));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(id, hoy(), Map.of())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(15).toString()));
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(id, hoy(), Map.of())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximaFecha").value(hoy().plusDays(90).toString()));
        // editar el calendario por orden (las dosis ya usadas no se pueden eliminar: 409)
        PUT("/api/inmunizacion/esquemas/" + esq, admin(), Map.of("dosis", List.of(
                Map.of("orden", 1, "nombreDosis", "Primera (ed)"),
                Map.of("orden", 2, "nombreDosis", "Segunda (ed)", "intervaloDiasPrevia", 20, "repetirCadaDias", 60))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dosis[1].intervaloDiasPrevia").value(20));
        PUT("/api/inmunizacion/esquemas/" + esq, admin(), Map.of("dosis", List.of(Map.of("orden", 1, "nombreDosis", "Unica"))))
                .andExpect(status().isConflict());
        // serie de una sola dosis sin repeticion: queda completa (sin proxima fecha)
        long vac2 = idDe(POST("/api/inmunizacion/vacunas", admin(), Map.of("codigo", "TST-VAC2", "nombre", "Unica dosis",
                "periodoEficaciaDias", 30)).andExpect(status().isCreated()));
        POST("/api/inmunizacion/esquemas", admin(), Map.of("codigo", "ESQ-TST2", "nombre", "Una dosis", "vacunaId", vac2,
                "dosis", List.of(Map.of("orden", 1, "nombreDosis", "Unica")))).andExpect(status().isCreated());
        POST(url(c.pacienteId()), vet.bearer(), aplicacionVacuna(vac2, hoy(), Map.of())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximaFecha").doesNotExist());
        POST("/api/inmunizacion/esquemas", admin(), Map.of("codigo", "ESQ-TST", "nombre", "dup", "vacunaId", id))
                .andExpect(status().isConflict());
    }
}
