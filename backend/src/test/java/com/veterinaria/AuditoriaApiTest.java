package com.veterinaria;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class AuditoriaApiTest extends ApiTestSupport {

    @Test
    void elTriggerRegistraQuienHizoElCambio() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = paciente(); // creado por admin
        // el veterinario modifica el peso del paciente (UPDATE sobre paciente) y emite una consulta
        POST("/api/pacientes/" + paciente + "/pesos", vet.bearer(), Map.of("pesoKg", 11.2)).andExpect(status().isCreated());
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente,
                "veterinarioId", vet.id(), "fecha", hoy().toString(), "motivo", "Control"))
                .andExpect(status().isCreated()));

        String res = json(GET("/api/auditoria?entidad=consulta&entidadId=" + consulta, admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)));
        org.junit.jupiter.api.Assertions.assertEquals((long) vet.id(),
                ((Number) JsonPath.read(res, "$.items[0].usuarioId")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("INSERT", JsonPath.read(res, "$.items[0].accion"));
        org.junit.jupiter.api.Assertions.assertEquals("Control", JsonPath.read(res, "$.items[0].detalle.despues.motivo"));

        // filtro por usuario: todo lo que hizo el veterinario (paciente UPDATE + consulta INSERT)
        GET("/api/auditoria?usuarioId=" + vet.id(), admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items[0].usuario").exists());
        // alta de paciente hecha por admin queda con el id del admin
        GET("/api/auditoria?entidad=paciente&entidadId=" + paciente + "&desde=" + hoy().minusDays(1) + "&hasta=" + hoy(),
                admin()).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].accion").isArray());
    }

    @Test
    void soloAdminConsultaLaAuditoria() throws Exception {
        for (String rol : new String[] {"VETERINARIO", "RECEPCION", "AUXILIAR"}) {
            GET("/api/auditoria", usuario(rol).bearer()).andExpect(status().isForbidden());
        }
        GET("/api/auditoria", admin()).andExpect(status().isOk());
        GET("/api/auditoria?desde=2026-10-10&hasta=2026-10-01", admin()).andExpect(status().isUnprocessableEntity());
    }
}
