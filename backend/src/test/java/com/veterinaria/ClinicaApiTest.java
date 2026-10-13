package com.veterinaria;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class ClinicaApiTest extends ApiTestSupport {

    /** Crea cita para manana, la lleva a sala y devuelve {citaId, consultaId} iniciando la consulta. */
    private long[] consultaDesdeCita(String vetBearer, long vetId, long pacienteId, int dias, String hora)
            throws Exception {
        long cita = idDe(crearCita(admin(), pacienteId, vetId, dias, hora, Map.of("motivo", "Vomito")));
        long consulta = idDe(POST("/api/citas/" + cita + "/iniciar-consulta", vetBearer, Map.of())
                .andExpect(status().isCreated()));
        return new long[] {cita, consulta};
    }

    @Test
    void iniciarConsultaDesdeCitaCreaConsultaAbiertaYPoneCitaEnCurso() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        long cita = idDe(crearCita(admin(), paciente, vet.id(), 2, "09:00", Map.of("motivo", "Vomito")));

        String plantillas = json(GET("/api/clinica/plantillas", vet.bearer()).andExpect(status().isOk()));
        long plantilla = primerId(plantillas, "$[?(@.nombre=='Consulta general')].id");

        POST("/api/citas/" + cita + "/iniciar-consulta", vet.bearer(),
                Map.of("plantillaId", plantilla, "tipoConsulta", "CONTROL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.cerrada").value(false))
                .andExpect(jsonPath("$.citaId").value(cita))
                .andExpect(jsonPath("$.motivo").value("Vomito"))
                .andExpect(jsonPath("$.tipoConsulta").value("CONTROL"))
                .andExpect(jsonPath("$.subjetivo").value(containsString("Motivo de consulta")));
        GET("/api/citas/" + cita, vet.bearer()).andExpect(jsonPath("$.estadoCita.codigo").value("EN_CURSO"));
        // la cita ya tiene consulta
        POST("/api/citas/" + cita + "/iniciar-consulta", vet.bearer(), Map.of()).andExpect(status().isConflict());
        // una cita cancelada no inicia consulta
        long cancelada = idDe(crearCita(admin(), paciente, vet.id(), 2, "11:00", Map.of()));
        POST("/api/citas/" + cancelada + "/estado", admin(), Map.of("estado", "CANCELADA")).andExpect(status().isOk());
        POST("/api/citas/" + cancelada + "/iniciar-consulta", vet.bearer(), Map.of()).andExpect(status().isConflict());
    }

    @Test
    void consultaCompletaCierreInmutableYAddendum() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario auxiliar = usuario("AUXILIAR");
        long paciente = paciente();
        long[] ids = consultaDesdeCita(vet.bearer(), vet.id(), paciente, 3, "09:00");
        String base = "/api/consultas/" + ids[1];

        // signos vitales: el auxiliar los registra; el peso actualiza al paciente
        POST(base + "/signos-vitales", auxiliar.bearer(), Map.of("temperatura", 38.6, "frecuenciaCardiaca", 110,
                "frecuenciaRespiratoria", 24, "pesoKg", 12.4, "puntajeCondicionCorporal", 5, "mucosas", "Rosadas",
                "escalaDolor", 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.pesoKg").value(12.4)).andExpect(jsonPath("$.mucosas").value("Rosadas"));
        POST(base + "/signos-vitales", auxiliar.bearer(), Map.of()).andExpect(status().isUnprocessableEntity());
        POST(base + "/signos-vitales", auxiliar.bearer(), Map.of("temperatura", 80)).andExpect(status().isBadRequest());
        GET("/api/pacientes/" + paciente, admin()).andExpect(jsonPath("$.pesoActual").value(12.4));
        GET("/api/pacientes/" + paciente + "/pesos", vet.bearer()).andExpect(jsonPath("$", hasSize(1)));

        // diagnosticos: del catalogo y libre
        String catalogo = json(GET("/api/clinica/diagnosticos?q=gastroenteritis", vet.bearer()).andExpect(status().isOk()));
        long dxCat = ((Number) JsonPath.read(catalogo, "$[0].id")).longValue();
        POST(base + "/diagnosticos", vet.bearer(), Map.of("catalogoId", dxCat, "tipo", "DEFINITIVO"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.descripcion").value("Gastroenteritis aguda"));
        POST(base + "/diagnosticos", vet.bearer(), Map.of("descripcion", "Posible cuerpo extrano"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("PRESUNTIVO"))
                .andExpect(jsonPath("$.orden").value(2));
        POST(base + "/diagnosticos", vet.bearer(), Map.of()).andExpect(status().isUnprocessableEntity());

        // receta con items
        POST(base + "/recetas", vet.bearer(), Map.of("indicacionesGenerales", "Dieta blanda 3 dias",
                "items", List.of(Map.of("medicamento", "Metronidazol", "dosis", "15 mg/kg", "via", "Oral",
                        "frecuencia", "cada 12 h", "duracion", "5 dias", "cantidad", 10))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items", hasSize(1)));
        POST(base + "/recetas", vet.bearer(), Map.of("items", List.of())).andExpect(status().isUnprocessableEntity());

        // SOAP + detalle
        PUT(base, vet.bearer(), Map.of("evaluacion", "Cuadro digestivo", "plan", "Hidratar", "triaje", "VERDE"))
                .andExpect(status().isOk());
        GET(base, vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.signosVitales", hasSize(1)))
                .andExpect(jsonPath("$.diagnosticos", hasSize(2)))
                .andExpect(jsonPath("$.recetas[0].items[0].medicamento").value("Metronidazol"))
                .andExpect(jsonPath("$.tipoCita.nombre").doesNotExist());

        // addendum antes del cierre: no corresponde
        POST(base + "/addendums", vet.bearer(), Map.of("texto", "Nota")).andExpect(status().isUnprocessableEntity());

        // cierre
        POST(base + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA")).andExpect(jsonPath("$.cerrada").value(true))
                .andExpect(jsonPath("$.cerradaEn").exists()).andExpect(jsonPath("$.firmaHash").isString());
        GET("/api/citas/" + ids[0], vet.bearer()).andExpect(jsonPath("$.estadoCita.codigo").value("ATENDIDA"));

        // inmutable: toda escritura clinica responde 409
        POST(base + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isConflict());
        PUT(base, vet.bearer(), Map.of("motivo", "Cambio")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CERRADA")));
        POST(base + "/signos-vitales", auxiliar.bearer(), Map.of("temperatura", 39)).andExpect(status().isConflict());
        POST(base + "/diagnosticos", vet.bearer(), Map.of("descripcion", "Otro")).andExpect(status().isConflict());
        POST(base + "/recetas", vet.bearer(), Map.of("items", List.of(Map.of("medicamento", "X"))))
                .andExpect(status().isConflict());

        // addendums (append-only): se agregan y se listan en orden
        POST(base + "/addendums", vet.bearer(), Map.of("tipo", "RESULTADO_TARDIO", "texto", "Coproscopico negativo"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.autor.id").value(vet.id()));
        POST(base + "/addendums", vet.bearer(), Map.of("texto", "Tutor informado")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ACLARACION"));
        POST(base + "/addendums", vet.bearer(), Map.of("texto", " ")).andExpect(status().isBadRequest());
        GET(base + "/addendums", auxiliar.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].texto", contains("Coproscopico negativo", "Tutor informado")));
    }

    @Test
    void putConCadenaVaciaLimpiaElTexto() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente(),
                "veterinarioId", vet.id(), "fecha", hoy().toString(), "motivo", "Control",
                "observaciones", "Nota inicial")).andExpect(status().isCreated()));
        PUT("/api/consultas/" + consulta, vet.bearer(), Map.of("observaciones", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.observaciones").doesNotExist()).andExpect(jsonPath("$.motivo").value("Control"));
    }

    @Test
    void historiaEsPaginadaYOrdenadaPorFechaDescendente() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        String hoy = hoy().toString();
        String hace10 = hoy().minusDays(10).toString();
        String hace30 = hoy().minusDays(30).toString();
        POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hace10, "motivo", "Consulta vieja",
                "vacunas", List.of(Map.of("nombre", "Rabia", "lote", "L1", "fechaAplicacion", hace10))))
                .andExpect(status().isCreated());
        POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy, "motivo", "Consulta reciente")).andExpect(status().isCreated());
        POST("/api/pacientes/" + paciente + "/pesos", vet.bearer(), Map.of("pesoKg", 9.5, "fecha", hace30))
                .andExpect(status().isCreated());
        POST("/api/pacientes/" + paciente + "/alergias", vet.bearer(), Map.of("descripcion", "Penicilina",
                "severidad", "ALTA", "tipo", "MEDICAMENTO")).andExpect(status().isCreated());

        String historia = json(GET("/api/pacientes/" + paciente + "/historia", vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)));
        List<String> fechas = JsonPath.read(historia, "$.items[*].fecha");
        List<String> ordenadas = fechas.stream().sorted(java.util.Comparator.reverseOrder()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(ordenadas, fechas);
        List<String> tipos = JsonPath.read(historia, "$.items[*].tipo");
        // hoy: consulta reciente y alergia (misma fecha: consulta primero); hace 10 d: consulta + vacuna; hace 30 d: peso
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("CONSULTA", "ALERGIA", "CONSULTA", "VACUNA", "PESO"), tipos);

        GET("/api/pacientes/" + paciente + "/historia?size=2&page=1", vet.bearer())
                .andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(false));
        GET("/api/pacientes/" + paciente + "/historia?tipo=VACUNA", vet.bearer())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].titulo").value("Rabia"));
        GET("/api/pacientes/999999/historia", vet.bearer()).andExpect(status().isNotFound());
    }

    @Test
    void alergiasYPesosActualizanElPaciente() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        String base = "/api/pacientes/" + paciente;
        long alergia = idDe(POST(base + "/alergias", vet.bearer(), Map.of("descripcion", "Pollo", "tipo", "ALIMENTO"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.severidad").value("BAJA")));
        PUT(base + "/alergias/" + alergia, vet.bearer(), Map.of("severidad", "MEDIA", "reaccion", "Prurito"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.severidad").value("MEDIA"));
        DELETE(base + "/alergias/" + alergia, vet.bearer()).andExpect(status().isNoContent());
        GET(base + "/alergias", vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
        GET(base + "/alergias?incluirInactivas=true", vet.bearer()).andExpect(jsonPath("$", hasSize(1)));
        POST(base + "/alergias", vet.bearer(), Map.of("severidad", "ALTA")).andExpect(status().isBadRequest());

        POST(base + "/pesos", vet.bearer(), Map.of("pesoKg", 8.0, "fecha", hoy().minusDays(5).toString()))
                .andExpect(status().isCreated());
        POST(base + "/pesos", vet.bearer(), Map.of("pesoKg", 8.4)).andExpect(status().isCreated());
        // un peso con fecha anterior no desplaza al actual
        POST(base + "/pesos", vet.bearer(), Map.of("pesoKg", 7.0, "fecha", hoy().minusDays(20).toString()))
                .andExpect(status().isCreated());
        GET(base, vet.bearer()).andExpect(jsonPath("$.pesoActual").value(8.4));
        GET(base + "/pesos", vet.bearer()).andExpect(jsonPath("$[0].pesoKg").value(8.4))
                .andExpect(jsonPath("$", hasSize(3)));
        POST(base + "/pesos", vet.bearer(), Map.of("pesoKg", 8.0, "fecha", hoy().plusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aislamientoDeRolesEnClinica() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario auxiliar = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        long paciente = paciente();
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente,
                "veterinarioId", vet.id(), "fecha", hoy().toString())).andExpect(status().isCreated()));
        String base = "/api/consultas/" + consulta;

        // RECEPCION no ve datos clinicos
        GET("/api/consultas", recepcion.bearer()).andExpect(status().isForbidden());
        GET(base, recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/pacientes/" + paciente + "/historia", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/clinica/plantillas", recepcion.bearer()).andExpect(status().isForbidden());
        // AUXILIAR lee y registra signos vitales / peso, nada mas
        GET(base, auxiliar.bearer()).andExpect(status().isOk());
        GET("/api/pacientes/" + paciente + "/historia", auxiliar.bearer()).andExpect(status().isOk());
        POST(base + "/signos-vitales", auxiliar.bearer(), Map.of("frecuenciaCardiaca", 90)).andExpect(status().isCreated());
        POST("/api/pacientes/" + paciente + "/pesos", auxiliar.bearer(), Map.of("pesoKg", 5)).andExpect(status().isCreated());
        POST(base + "/diagnosticos", auxiliar.bearer(), Map.of("descripcion", "x")).andExpect(status().isForbidden());
        POST(base + "/recetas", auxiliar.bearer(), Map.of("items", List.of(Map.of("medicamento", "X"))))
                .andExpect(status().isForbidden());
        POST(base + "/cerrar", auxiliar.bearer(), Map.of()).andExpect(status().isForbidden());
        POST("/api/consultas", auxiliar.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString())).andExpect(status().isForbidden());
        POST("/api/pacientes/" + paciente + "/alergias", auxiliar.bearer(), Map.of("descripcion", "x"))
                .andExpect(status().isForbidden());
        // iniciar consulta: solo veterinario/admin
        long cita = idDe(crearCita(admin(), paciente, vet.id(), 12, "09:00", Map.of()));
        POST("/api/citas/" + cita + "/iniciar-consulta", recepcion.bearer(), Map.of()).andExpect(status().isForbidden());
        POST("/api/citas/" + cita + "/iniciar-consulta", auxiliar.bearer(), Map.of()).andExpect(status().isForbidden());
    }
}
