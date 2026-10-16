package com.veterinaria;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Utilidades de la Etapa B: tutores con consentimientos, veterinario con tarjeta profesional, consultas con formula. */
abstract class EtapaBTestSupport extends EtapaATestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    protected record CasoB(long tutorId, long pacienteId, String email, String telefono, String tutorNombre) {
    }

    /** Tutor con los consentimientos indicados (email y telefono validos) y un perro. */
    protected CasoB casoB(boolean aceptaEmail, boolean aceptaWhatsapp, boolean autorizaDatos) throws Exception {
        return casoB(aceptaEmail, aceptaWhatsapp, autorizaDatos, null);
    }

    protected CasoB casoB(boolean aceptaEmail, boolean aceptaWhatsapp, boolean autorizaDatos, String telefono) throws Exception {
        String admin = admin();
        int n = SEQ.incrementAndGet();
        String email = "tutorb" + n + "@correo.test";
        String tel = telefono != null ? telefono : "3001" + (100000 + n);
        Map<String, Object> t = new HashMap<>(Map.of("tipoDocumento", "CC", "numeroDocumento", "66" + n,
                "nombres", "TutorB" + n, "apellidos", "Etapa", "email", email, "telefono", tel));
        t.put("aceptaEmail", aceptaEmail);
        t.put("aceptaWhatsapp", aceptaWhatsapp);
        t.put("autorizaTratamientoDatos", autorizaDatos);
        long tutorId = idDe(POST("/api/tutores", admin, t).andExpect(status().isCreated()));
        Map<String, Object> p = new HashMap<>(Map.of("nombre", "Mascota" + n, "especieId", especieId("Perro"), "sexo", "M",
                "tutorIds", List.of(tutorId), "tutorPrincipalId", tutorId));
        long pacienteId = idDe(POST("/api/pacientes", admin, p).andExpect(status().isCreated()));
        return new CasoB(tutorId, pacienteId, email, tel, "TutorB" + n + " Etapa");
    }

    /** Veterinario con tarjeta profesional registrada. */
    protected Usuario vetConTarjeta(String tarjeta) throws Exception {
        Usuario vet = usuario("VETERINARIO");
        PUT("/api/perfil/profesional", vet.bearer(), Map.of("numeroProfesional", tarjeta, "especialidad", "Medicina interna"))
                .andExpect(status().isOk());
        return vet;
    }

    protected record ConsultaB(long consultaId, long recetaId) {
    }

    /** Consulta del paciente con diagnostico y una receta (Amoxicilina 1 tableta); opcionalmente cerrada. */
    protected ConsultaB consultaConFormula(Usuario vet, long paciente, boolean cerrar) throws Exception {
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString(), "motivo", "Vomito y decaimiento")).andExpect(status().isCreated()));
        String base = "/api/consultas/" + consulta;
        POST(base + "/diagnosticos", vet.bearer(), Map.of("descripcion", "Gastroenteritis aguda")).andExpect(status().isCreated());
        long receta = idDe(POST(base + "/recetas", vet.bearer(), Map.of("indicacionesGenerales", "Dieta blanda",
                "items", List.of(mapa("medicamento", "Amoxicilina", "concentracion", "250 mg", "presentacion", "Tabletas x 10",
                        "dosis", "1 tableta", "via", "Oral", "frecuencia", "cada 12 h", "duracion", "7 dias", "cantidad", 14))))
                .andExpect(status().isCreated()));
        if (cerrar) {
            POST(base + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());
        }
        return new ConsultaB(consulta, receta);
    }
}
