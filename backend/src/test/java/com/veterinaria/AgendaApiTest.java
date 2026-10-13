package com.veterinaria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class AgendaApiTest extends ApiTestSupport {

    @Test
    void veterinariosListaSoloVeterinariosActivosParaCualquierRol() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario recepcion = usuario("RECEPCION");
        GET("/api/veterinarios", recepcion.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + vet.id() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id==" + recepcion.id() + ")]").doesNotExist())
                .andExpect(jsonPath("$[0].nombre").exists());
    }

    @Test
    void solapeDeVeterinarioDevuelve409ConMensaje() throws Exception {
        String admin = admin();
        Usuario vet = usuario("VETERINARIO");
        long p1 = paciente();
        long p2 = paciente();
        crearCita(admin, p1, vet.id(), 3, "09:00", Map.of("horaFin", "09:30")).andExpect(status().isCreated());
        crearCita(admin, p2, vet.id(), 3, "09:15", Map.of("horaFin", "09:45")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El veterinario ya tiene una cita en ese horario"));
        // contiguas no se solapan
        crearCita(admin, p2, vet.id(), 3, "09:30", Map.of("horaFin", "10:00")).andExpect(status().isCreated());
    }

    @Test
    void solapeDeSalaDevuelve409() throws Exception {
        String admin = admin();
        Usuario vet1 = usuario("VETERINARIO");
        Usuario vet2 = usuario("VETERINARIO");
        long sala = idDe(POST("/api/agenda/salas", admin, Map.of("nombre", "Sala test 1", "tipo", "CONSULTORIO")));
        crearCita(admin, paciente(), vet1.id(), 4, "10:00", Map.of("salaId", sala)).andExpect(status().isCreated());
        crearCita(admin, paciente(), vet2.id(), 4, "10:10", Map.of("salaId", sala)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("sala")));
    }

    @Test
    void reagendarSobreCitaCanceladaEsPosible() throws Exception {
        String admin = admin();
        Usuario vet = usuario("VETERINARIO");
        long cita = idDe(crearCita(admin, paciente(), vet.id(), 5, "11:00", Map.of()));
        POST("/api/citas/" + cita + "/estado", admin, Map.of("estado", "CANCELADA", "motivo", "Tutor no puede"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoCita.codigo").value("CANCELADA"));
        // mismo veterinario y mismo inicio: ya no choca (ni en la app ni en la restriccion de la BD)
        crearCita(admin, paciente(), vet.id(), 5, "11:00", Map.of()).andExpect(status().isCreated());
    }

    @Test
    void bloqueoImpideCitasYNoSePuedeCrearSobreCitas() throws Exception {
        String admin = admin();
        Usuario vet = usuario("VETERINARIO");
        String dia = hoy().plusDays(6).toString();
        POST("/api/agenda/bloqueos", admin, Map.of("veterinarioId", vet.id(), "inicio", dia + "T12:00:00",
                "fin", dia + "T14:00:00", "tipo", "ALMUERZO", "motivo", "Almuerzo")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.veterinario.id").value(vet.id()));
        crearCita(admin, paciente(), vet.id(), 6, "13:00", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("bloqueo")));
        // fuera del bloqueo si
        crearCita(admin, paciente(), vet.id(), 6, "14:00", Map.of()).andExpect(status().isCreated());
        // bloqueo sobre una cita vigente -> 409
        POST("/api/agenda/bloqueos", admin, Map.of("veterinarioId", vet.id(), "inicio", dia + "T14:10:00",
                "fin", dia + "T15:00:00")).andExpect(status().isConflict());
        // sin veterinario ni sala -> 422
        POST("/api/agenda/bloqueos", admin, Map.of("inicio", dia + "T16:00:00", "fin", dia + "T17:00:00"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bloqueoDeSalaYCrudDeBloqueos() throws Exception {
        String admin = admin();
        long sala = idDe(POST("/api/agenda/salas", admin, Map.of("nombre", "Sala bloqueable")));
        Usuario vet = usuario("VETERINARIO");
        String dia = hoy().plusDays(7).toString();
        long bloqueo = idDe(POST("/api/agenda/bloqueos", admin, Map.of("salaId", sala, "inicio", dia + "T08:00:00",
                "fin", dia + "T12:00:00", "tipo", "MANTENIMIENTO")));
        crearCita(admin, paciente(), vet.id(), 7, "09:00", Map.of("salaId", sala)).andExpect(status().isConflict());
        PUT("/api/agenda/bloqueos/" + bloqueo, admin, Map.of("motivo", "Limpieza profunda")).andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("Limpieza profunda"));
        GET("/api/agenda/bloqueos?desde=" + dia + "&hasta=" + dia + "&salaId=" + sala, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        DELETE("/api/agenda/bloqueos/" + bloqueo, admin).andExpect(status().isNoContent());
        crearCita(admin, paciente(), vet.id(), 7, "09:00", Map.of("salaId", sala)).andExpect(status().isCreated());
    }

    @Test
    void citaUsaDuracionDelTipoYAgendaDevuelveCitasYBloqueos() throws Exception {
        String admin = admin();
        Usuario vet = usuario("VETERINARIO");
        String tipos = json(GET("/api/agenda/tipos-cita", admin).andExpect(status().isOk()));
        long cirugia = primerId(tipos, "$[?(@.nombre=='Cirugia')].id");
        long sala = idDe(POST("/api/agenda/salas", admin, Map.of("nombre", "Quirofano test", "tipo", "QUIROFANO")));
        String dia = hoy().plusDays(8).toString();
        crearCita(admin, paciente(), vet.id(), 8, "08:00", Map.of("tipoCitaId", cirugia, "salaId", sala))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaFin").value("09:30:00"))
                .andExpect(jsonPath("$.tipoCita.nombre").value("Cirugia"))
                .andExpect(jsonPath("$.sala.nombre").value("Quirofano test"));
        POST("/api/agenda/bloqueos", admin, Map.of("veterinarioId", vet.id(), "inicio", dia + "T12:00:00",
                "fin", dia + "T13:00:00")).andExpect(status().isCreated());

        GET("/api/agenda?desde=" + dia + "&hasta=" + dia + "&veterinarioId=" + vet.id(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citas", hasSize(1)))
                .andExpect(jsonPath("$.bloqueos", hasSize(1)));
        GET("/api/agenda?desde=" + dia + "&hasta=" + dia + "&salaId=" + sala, admin)
                .andExpect(jsonPath("$.citas", hasSize(1)));
        GET("/api/agenda?desde=" + dia + "&hasta=" + hoy().plusDays(200), admin)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transicionesDeEstadoSeValidan() throws Exception {
        String admin = admin();
        Usuario vet = usuario("VETERINARIO");
        long cita = idDe(crearCita(admin, paciente(), vet.id(), 9, "09:00", Map.of()));
        String url = "/api/citas/" + cita + "/estado";
        // saltos invalidos
        POST(url, admin, Map.of("estado", "ATENDIDA")).andExpect(status().isConflict());
        POST(url, admin, Map.of("estado", "EN_CURSO")).andExpect(status().isConflict());
        POST(url, admin, Map.of("estado", "INVENTADO")).andExpect(status().isUnprocessableEntity());
        // flujo normal
        POST(url, admin, Map.of("estado", "CONFIRMADA")).andExpect(status().isOk());
        POST(url, admin, Map.of("estado", "EN_SALA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.horaLlegada").exists());
        POST(url, vet.bearer(), Map.of("estado", "EN_CONSULTA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCita.codigo").value("EN_CURSO"))
                .andExpect(jsonPath("$.atencionInicio").exists());
        POST(url, vet.bearer(), Map.of("estado", "COMPLETADA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCita.codigo").value("ATENDIDA"));
        // estado final: no admite mas cambios
        POST(url, admin, Map.of("estado", "CANCELADA")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ATENDIDA -> CANCELADA")));
    }

    @Test
    void salasYListaDeEsperaCrud() throws Exception {
        String admin = admin();
        long sala = idDe(POST("/api/agenda/salas", admin, Map.of("nombre", "Sala CRUD", "capacidad", 2)));
        POST("/api/agenda/salas", admin, Map.of("nombre", "sala crud")).andExpect(status().isConflict());
        PUT("/api/agenda/salas/" + sala, admin, Map.of("tipo", "IMAGENES")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("IMAGENES")).andExpect(jsonPath("$.capacidad").value(2));
        POST("/api/agenda/salas", admin, Map.of("nombre", "Mala", "tipo", "XX")).andExpect(status().isBadRequest());
        DELETE("/api/agenda/salas/" + sala, admin).andExpect(status().isNoContent());
        GET("/api/agenda/salas", admin).andExpect(jsonPath("$[?(@.id==" + sala + ")]").doesNotExist());
        GET("/api/agenda/salas?todas=true", admin).andExpect(jsonPath("$[?(@.id==" + sala + ")]").exists());

        long paciente = paciente();
        long espera = idDe(POST("/api/agenda/lista-espera", admin,
                Map.of("pacienteId", paciente, "prioridad", 1, "franjaPreferida", "MANANA", "notas", "Cualquier hueco")));
        GET("/api/agenda/lista-espera?estado=ACTIVA", admin).andExpect(jsonPath("$[0].id").value(espera))
                .andExpect(jsonPath("$[0].tutor.nombre").exists());
        PUT("/api/agenda/lista-espera/" + espera, admin, Map.of("notas", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.notas").doesNotExist());
        POST("/api/agenda/lista-espera", admin, Map.of("pacienteId", paciente, "prioridad", 9))
                .andExpect(status().isBadRequest());
        DELETE("/api/agenda/lista-espera/" + espera, admin).andExpect(status().isNoContent());
        GET("/api/agenda/lista-espera/" + espera, admin).andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void rolesDeAgenda() throws Exception {
        Usuario auxiliar = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente();
        // AUXILIAR solo lee la agenda
        GET("/api/citas", auxiliar.bearer()).andExpect(status().isOk());
        GET("/api/agenda?desde=" + hoy() + "&hasta=" + hoy(), auxiliar.bearer())
                .andExpect(status().isOk());
        crearCita(auxiliar.bearer(), paciente, vet.id(), 10, "09:00", Map.of()).andExpect(status().isForbidden());
        // RECEPCION agenda y gestiona bloqueos/salas
        crearCita(recepcion.bearer(), paciente, vet.id(), 10, "09:00", Map.of()).andExpect(status().isCreated());
        POST("/api/agenda/salas", recepcion.bearer(), Map.of("nombre", "Sala recepcion")).andExpect(status().isCreated());
        POST("/api/agenda/salas", auxiliar.bearer(), Map.of("nombre", "Sala aux")).andExpect(status().isForbidden());
        // el veterinario no gestiona bloqueos de agenda
        POST("/api/agenda/bloqueos", vet.bearer(), Map.of("veterinarioId", vet.id(), "inicio", "2030-01-01T08:00:00",
                "fin", "2030-01-01T09:00:00")).andExpect(status().isForbidden());
    }
}
