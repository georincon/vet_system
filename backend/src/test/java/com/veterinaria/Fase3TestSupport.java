package com.veterinaria;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jayway.jsonpath.JsonPath;

/** Utilidades de Fase 3: pacientes con tutor/consentimiento, catalogos, lotes y aplicaciones. */
abstract class Fase3TestSupport extends ApiTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired
    protected JdbcClient jdbc;

    protected record Caso(long tutorId, long pacienteId, String email) {
    }

    /** Tutor (con o sin consentimiento de correo) y perro con la fecha de nacimiento dada (puede ser null). */
    protected Caso caso(boolean aceptaEmail, LocalDate nacimiento) throws Exception {
        String admin = admin();
        int n = SEQ.incrementAndGet();
        String email = "tutor" + n + "@correo.test";
        Map<String, Object> t = new HashMap<>(Map.of("tipoDocumento", "CC", "numeroDocumento", "77" + n,
                "nombres", "Tutor" + n, "apellidos", "Fase3", "email", email, "telefono", "300" + n));
        t.put("aceptaEmail", aceptaEmail);
        long tutorId = idDe(POST("/api/tutores", admin, t).andExpect(status().isCreated()));
        String especies = json(GET("/api/catalogos/especies", admin));
        long perro = primerId(especies, "$[?(@.nombre=='Perro')].id");
        Map<String, Object> p = new HashMap<>(Map.of("nombre", "Fase3-" + n, "especieId", perro, "sexo", "M",
                "tutorIds", List.of(tutorId), "tutorPrincipalId", tutorId));
        if (nacimiento != null) {
            p.put("fechaNacimiento", nacimiento.toString());
        }
        long pacienteId = idDe(POST("/api/pacientes", admin, p).andExpect(status().isCreated()));
        return new Caso(tutorId, pacienteId, email);
    }

    protected long vacunaId(String codigo) throws Exception {
        return primerId(json(GET("/api/inmunizacion/vacunas?q=" + codigo, admin())), "$[?(@.codigo=='" + codigo + "')].id");
    }

    protected long antiparasitarioId(String codigo) throws Exception {
        return primerId(json(GET("/api/inmunizacion/antiparasitarios", admin())), "$[?(@.codigo=='" + codigo + "')].id");
    }

    /** Lote nuevo con stock y vencimiento dados; devuelve su id. */
    protected long lote(String numero, int cantidad, LocalDate vencimiento) {
        long item = jdbc.sql("""
                insert into item (codigo, nombre, tipo, controla_stock) values (:c, :n, 'MEDICAMENTO', true) returning id
                """).param("c", "T-" + numero + "-" + SEQ.incrementAndGet()).param("n", "Item " + numero)
                .query(Long.class).single();
        long bodega = jdbc.sql("select id from bodega order by id limit 1").query(Long.class).single();
        return jdbc.sql("""
                insert into lote (item_id, bodega_id, numero_lote, fecha_vencimiento, cantidad_inicial, cantidad_actual)
                values (:i, :b, :n, :v, :q, :q) returning id
                """).param("i", item).param("b", bodega).param("n", numero).param("v", vencimiento)
                .param("q", BigDecimal.valueOf(cantidad)).query(Long.class).single();
    }

    protected BigDecimal stock(long loteId) {
        return jdbc.sql("select cantidad_actual from lote where id = :id").param("id", loteId)
                .query(BigDecimal.class).single();
    }

    protected Map<String, Object> aplicacionVacuna(long vacunaId, LocalDate fecha, Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>(Map.of("tipo", "VACUNA", "vacunaId", vacunaId,
                "fechaAplicacion", fecha.toString()));
        m.putAll(extra);
        return m;
    }

    protected String primerValor(String json, String path) {
        Object v = JsonPath.read(json, path);
        return String.valueOf(v instanceof List<?> l ? l.get(0) : v);
    }
}
