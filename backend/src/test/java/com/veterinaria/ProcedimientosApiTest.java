package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProcedimientosApiTest extends EtapaATestSupport {

    @Test
    void procedimientoAmbulatorioSeRegistraConInsumosYConsulta() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Perro", "M", LocalDate.now().minusYears(3));
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString(), "motivo", "Herida en miembro")).andExpect(status().isCreated()));
        long itemCatalogo = jdbc.sql("select id from item where codigo = 'SRV-PRC-CURA'").query(Long.class).single();

        POST("/api/procedimientos", vet.bearer(), mapa("pacienteId", paciente, "consultaId", consulta, "itemId", itemCatalogo,
                "tecnica", "x")).andExpect(status().isBadRequest()); // propiedad desconocida
        String res = json(POST("/api/procedimientos", vet.bearer(), mapa("pacienteId", paciente, "consultaId", consulta, "itemId", itemCatalogo,
                "anestesiaTipo", "LOCAL", "premedicacion", "Ninguna", "descripcionTecnica", "Lavado con solucion salina, desbridamiento y sutura simple",
                "hallazgos", "Herida cortante de 3 cm sin compromiso muscular", "complicaciones", "Ninguna",
                "diagnosticoPostoperatorio", "Laceracion cutanea", "resultado", "Satisfactorio",
                "indicacionesPostoperatorias", "Curacion cada 48 h, collar isabelino", "controles", "Retiro de puntos a los 10 dias",
                "proximoControl", hoy().plusDays(10).toString(),
                "insumos", List.of(Map.of("descripcion", "Sutura nylon 3-0", "cantidad", 1, "lote", "L-123"),
                        Map.of("descripcion", "Gasa esteril", "cantidad", 4.5))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("PROCEDIMIENTO"))
                .andExpect(jsonPath("$.estado").value("REALIZADA")).andExpect(jsonPath("$.codigo").value(containsString("PRC-")))
                .andExpect(jsonPath("$.procedimiento").value("Curacion simple")).andExpect(jsonPath("$.cirujano.id").value(vet.id()))
                .andExpect(jsonPath("$.consultaId").value(consulta)).andExpect(jsonPath("$.finEn").exists())
                .andExpect(jsonPath("$.insumos", hasSize(2))).andExpect(jsonPath("$.anestesiaTipo").value("LOCAL"))
                .andExpect(jsonPath("$.proximoControl").value(hoy().plusDays(10).toString())));
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();

        // edicion parcial y reemplazo de insumos; auditado por trigger
        PUT("/api/procedimientos/" + id, vet.bearer(), Map.of("complicaciones", "Sangrado leve controlado", "insumos",
                List.of(Map.of("descripcion", "Sutura nylon 3-0", "cantidad", 2)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.complicaciones").value("Sangrado leve controlado")).andExpect(jsonPath("$.insumos", hasSize(1)))
                .andExpect(jsonPath("$.resultado").value("Satisfactorio"));
        assertThat(jdbc.sql("select count(*) from auditoria where entidad = 'cirugia' and entidad_id = :id").param("id", id).query(Long.class).single())
                .isGreaterThanOrEqualTo(2L);
        GET("/api/procedimientos?pacienteId=" + paciente, vet.bearer()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].consentimientoEstado").doesNotExist());
        GET("/api/pacientes/" + paciente + "/procedimientos?tipo=CIRUGIA", vet.bearer()).andExpect(jsonPath("$.items", hasSize(0)));
        GET("/api/procedimientos?consultaId=" + consulta + "&tipo=PROCEDIMIENTO&estado=REALIZADA", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1));
        GET("/api/procedimientos?tipo=OTRO", vet.bearer()).andExpect(status().isUnprocessableEntity());

        // validaciones
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "procedimiento", "X", "inicioEn", "2026-01-01T10:00:00",
                "finEn", "2026-01-01T09:00:00")).andExpect(status().isUnprocessableEntity());
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente)).andExpect(status().isUnprocessableEntity());
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", 99999999, "procedimiento", "X")).andExpect(status().isNotFound());
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", pacienteDe("Gato", "F", null), "consultaId", consulta, "procedimiento", "X"))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "procedimiento", "X", "veterinarioId", usuario("RECEPCION").id()))
                .andExpect(status().isUnprocessableEntity());

        // historia: aparece el procedimiento realizado
        GET("/api/pacientes/" + paciente + "/historia?tipo=PROCEDIMIENTO", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].titulo").value("Curacion simple")).andExpect(jsonPath("$.items[0].referenciaId").value(id))
                .andExpect(jsonPath("$.items[0].estado").value("REALIZADA"));
    }

    @Test
    void cirugiaExigeConsentimientoFirmadoMonitoreoYReportePdf() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "F", LocalDate.now().minusYears(2));
        long tutorId = jdbc.sql("select tutor_id from paciente_tutor where paciente_id = :p").param("p", paciente).query(Long.class).single();
        LocalDateTime manana = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

        // no se puede registrar ya iniciada sin consentimiento (salvo urgencia)
        POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "tipo", "CIRUGIA", "procedimiento", "Ovariohisterectomia",
                "estado", "EN_CURSO")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("consentimiento")));
        String res = json(POST("/api/procedimientos", vet.bearer(), mapa("pacienteId", paciente, "tipo", "CIRUGIA", "procedimiento", "Ovariohisterectomia",
                "programadaPara", manana.toString(), "asa", 2, "ayunoHoras", 12, "pesoKg", 18.4, "diagnosticoPreoperatorio", "Esterilizacion electiva",
                "anestesiaTipo", "GENERAL", "premedicacion", "Acepromacina 0.02 mg/kg IM", "induccion", "Propofol a efecto",
                "mantenimiento", "Isoflurano 1.5%", "analgesia", "Meloxicam 0.2 mg/kg SC",
                "ayudanteId", aux.id(), "anestesiologoId", vet.id()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PROGRAMADA")).andExpect(jsonPath("$.codigo").value(containsString("CIR-")))
                .andExpect(jsonPath("$.asa").value(2)).andExpect(jsonPath("$.ayudante.id").value(aux.id())));
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
        String base = "/api/procedimientos/" + id;

        // sin consentimiento no inicia
        POST(base + "/estado", vet.bearer(), Map.of("estado", "EN_CURSO")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("consentimiento")));
        // el tutor rechaza: tampoco inicia
        POST(base + "/consentimiento", vet.bearer(), Map.of("aceptado", false, "firmanteNombre", "Tutor Prueba")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADO")).andExpect(jsonPath("$.textoFirmado").value(containsString("Declaro")));
        POST(base + "/estado", vet.bearer(), Map.of("estado", "EN_CURSO")).andExpect(status().isUnprocessableEntity());
        // el consentimiento firmado: archivo de tipo CONSENTIMIENTO del mismo paciente
        long otroPaciente = pacienteDe("Perro", "M", null);
        long ajeno = idDe(subir(vet.bearer(), otroPaciente, "CONSENTIMIENTO", "ajeno.pdf", pdfMinimo(), Map.of()).andExpect(status().isCreated()));
        long mal = idDe(subir(vet.bearer(), paciente, "INFORME", "informe.pdf", pdfMinimo(), Map.of()).andExpect(status().isCreated()));
        long firmado = idDe(subir(vet.bearer(), paciente, "CONSENTIMIENTO", "consentimiento-firmado.pdf", pdfMinimo(),
                Map.of("cirugiaId", String.valueOf(id))).andExpect(status().isCreated()));
        Map<String, Object> cuerpo = Map.of("aceptado", true, "firmanteNombre", "Tutor Prueba", "firmanteDocumento", "CC 123456",
                "metodoFirma", "PAPEL_ESCANEADO", "archivoId", ajeno);
        POST(base + "/consentimiento", vet.bearer(), cuerpo).andExpect(status().isUnprocessableEntity());
        POST(base + "/consentimiento", vet.bearer(), Map.of("aceptado", true, "firmanteNombre", "T", "archivoId", mal)).andExpect(status().isUnprocessableEntity());
        POST(base + "/consentimiento", vet.bearer(), Map.of("aceptado", true, "firmanteNombre", "T", "tutorId", 99999999)).andExpect(status().isUnprocessableEntity());
        POST(base + "/consentimiento", aux.bearer(), Map.of("aceptado", true, "firmanteNombre", "T")).andExpect(status().isForbidden());
        POST(base + "/consentimiento", vet.bearer(), Map.of("firmanteNombre", "T")).andExpect(status().isBadRequest());
        POST(base + "/consentimiento", vet.bearer(), Map.of("aceptado", true, "firmanteNombre", "Tutor Prueba", "firmanteDocumento", "CC 123456",
                "metodoFirma", "PAPEL_ESCANEADO", "archivoId", firmado, "tutorId", tutorId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("FIRMADO")).andExpect(jsonPath("$.archivoId").value(firmado))
                .andExpect(jsonPath("$.tutor.id").value(tutorId)).andExpect(jsonPath("$.firmadoEn").exists());
        POST(base + "/consentimiento", vet.bearer(), Map.of("aceptado", true, "firmanteNombre", "Otra")).andExpect(status().isConflict());

        // monitoreo anestesico (el auxiliar lo registra) y estados
        POST(base + "/estado", vet.bearer(), Map.of("estado", "EN_CURSO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO")).andExpect(jsonPath("$.inicioEn").exists());
        POST(base + "/monitoreo-anestesico", aux.bearer(), Map.of("frecuenciaCardiaca", 96, "frecuenciaRespiratoria", 14, "spo2", 98.5,
                "etco2", 38, "presionSistolica", 110, "temperatura", 37.4, "agenteInhalatorioPct", 1.5, "fluidoMlH", 10, "notas", "Estable"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registradoPor.id").value(aux.id())).andExpect(jsonPath("$.spo2").value(98.5));
        POST(base + "/monitoreo-anestesico", aux.bearer(), Map.of("frecuenciaCardiaca", 88, "spo2", 99)).andExpect(status().isCreated());
        POST(base + "/monitoreo-anestesico", aux.bearer(), Map.of("notas", "solo nota")).andExpect(status().isUnprocessableEntity());
        POST(base + "/monitoreo-anestesico", aux.bearer(), Map.of("spo2", 150)).andExpect(status().isBadRequest());
        GET(base + "/monitoreo-anestesico", aux.bearer()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        PUT(base, vet.bearer(), mapa("descripcionTecnica", "Abordaje por linea media, ligadura de pedículos y cierre por planos",
                "hallazgos", "Utero y ovarios sin alteraciones", "complicaciones", "Ninguna", "materialSutura", "Poliglactina 2-0",
                "muestrasHistopatologia", false, "diagnosticoPostoperatorio", "Sin hallazgos patologicos", "resultado", "Cirugia sin complicaciones",
                "indicacionesPostoperatorias", "Reposo 10 dias, collar isabelino, analgesia", "controles", "Revision a las 48 h y retiro de puntos a los 10 dias",
                "insumos", List.of(Map.of("descripcion", "Poliglactina 2-0", "cantidad", 2), Map.of("descripcion", "Campo quirurgico", "cantidad", 1))))
                .andExpect(status().isOk());
        POST(base + "/estado", vet.bearer(), Map.of("estado", "REALIZADA")).andExpect(status().isOk()).andExpect(jsonPath("$.finEn").exists());
        POST(base + "/estado", vet.bearer(), Map.of("estado", "EN_CURSO")).andExpect(status().isConflict());
        POST(base + "/estado", vet.bearer(), Map.of("estado", "CANCELADA", "motivo", "x")).andExpect(status().isConflict());

        GET(base, aux.bearer()).andExpect(status().isOk()).andExpect(jsonPath("$.consentimiento.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.archivos", hasSize(1))).andExpect(jsonPath("$.monitoreo", hasSize(2))).andExpect(jsonPath("$.insumos", hasSize(2)));

        // reporte quirurgico PDF
        byte[] pdf = mockMvc.perform(get(base + "/reporte.pdf").header("Authorization", vet.bearer()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(textoPdf(pdf)).contains("Clinica Veterinaria").contains("Reporte quirurgico").contains("Ovariohisterectomia")
                .contains("Valoracion preoperatoria").contains("Anestesia").contains("Isoflurano").contains("Monitoreo anestesico")
                .contains("Tecnica y hallazgos").contains("Poliglactina 2-0").contains("Insumos utilizados").contains("Consentimiento informado")
                .contains("FIRMADO").contains("Tutor Prueba").contains("Usuario veterinario").contains("Cuidados postoperatorios")
                .contains("98.5");
        mockMvc.perform(get(base + "/reporte.pdf").header("Authorization", aux.bearer())).andExpect(status().isForbidden());

        // historia
        GET("/api/pacientes/" + paciente + "/historia?tipo=CIRUGIA", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].tipo").value("CIRUGIA")).andExpect(jsonPath("$.items[0].estado").value("REALIZADA"));
        GET("/api/pacientes/" + paciente + "/historia?tipo=PROCEDIMIENTO", vet.bearer()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void urgenciaIniciaSinConsentimientoYTransicionesDeEstado() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Gato", "M", LocalDate.now().minusYears(1));
        long urgente = idDe(POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "tipo", "CIRUGIA", "urgencia", true,
                "procedimiento", "Laparotomia exploratoria", "estado", "EN_CURSO")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_CURSO")).andExpect(jsonPath("$.urgencia").value(true)).andExpect(jsonPath("$.inicioEn").exists()));
        POST("/api/procedimientos/" + urgente + "/estado", vet.bearer(), Map.of("estado", "REALIZADA")).andExpect(status().isOk());
        GET("/api/procedimientos/" + urgente + "/reporte.pdf", vet.bearer()).andExpect(status().isOk());

        long programada = idDe(POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "tipo", "CIRUGIA", "procedimiento", "Castracion"))
                .andExpect(status().isCreated()));
        String estado = "/api/procedimientos/" + programada + "/estado";
        POST(estado, vet.bearer(), Map.of("estado", "APLAZADA")).andExpect(status().isUnprocessableEntity()); // requiere motivo
        POST(estado, vet.bearer(), Map.of("estado", "APLAZADA", "motivo", "Tutor solicito otra fecha")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLAZADA"));
        POST(estado, vet.bearer(), Map.of("estado", "REALIZADA")).andExpect(status().isConflict()); // APLAZADA -> REALIZADA no existe
        POST(estado, vet.bearer(), Map.of("estado", "PROGRAMADA")).andExpect(status().isOk());
        POST(estado, vet.bearer(), Map.of("estado", "CANCELADA", "motivo", "Paciente no apto para anestesia")).andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoCancelacion").value("Paciente no apto para anestesia"));
        PUT("/api/procedimientos/" + programada, vet.bearer(), Map.of("resultado", "x")).andExpect(status().isConflict());
        POST("/api/procedimientos/" + programada + "/monitoreo-anestesico", vet.bearer(), Map.of("spo2", 98)).andExpect(status().isConflict());
        POST(estado, vet.bearer(), Map.of("estado", "INVENTADO")).andExpect(status().isBadRequest());
        GET("/api/procedimientos/99999999", vet.bearer()).andExpect(status().isNotFound());
    }

    @Test
    void rolesDeProcedimientos() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        long paciente = pacienteDe("Perro", "F", null);
        POST("/api/procedimientos", aux.bearer(), Map.of("pacienteId", paciente, "procedimiento", "Curacion")).andExpect(status().isForbidden());
        POST("/api/procedimientos", recepcion.bearer(), Map.of("pacienteId", paciente, "procedimiento", "Curacion")).andExpect(status().isForbidden());
        long id = idDe(POST("/api/procedimientos", vet.bearer(), Map.of("pacienteId", paciente, "procedimiento", "Curacion")).andExpect(status().isCreated()));
        GET("/api/procedimientos/" + id, aux.bearer()).andExpect(status().isOk());
        GET("/api/procedimientos/" + id, recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/procedimientos", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/pacientes/" + paciente + "/procedimientos", recepcion.bearer()).andExpect(status().isForbidden());
        PUT("/api/procedimientos/" + id, aux.bearer(), Map.of("resultado", "x")).andExpect(status().isForbidden());
        POST("/api/procedimientos/" + id + "/monitoreo-anestesico", recepcion.bearer(), Map.of("spo2", 98)).andExpect(status().isForbidden());
        POST("/api/procedimientos/" + id + "/monitoreo-anestesico", aux.bearer(), Map.of("spo2", 98)).andExpect(status().isCreated());
    }
}
