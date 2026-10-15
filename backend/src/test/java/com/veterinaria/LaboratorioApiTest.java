package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class LaboratorioApiTest extends EtapaATestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Examen de prueba con rangos por especie, sexo y edad; devuelve su codigo. */
    private String examenDePrueba() throws Exception {
        String codigo = "TST" + SEQ.incrementAndGet();
        long perro = especieId("Perro");
        long gato = especieId("Gato");
        Map<String, Object> analito = Map.of("codigo", "ANL", "nombre", "Analito de prueba", "unidad", "mg/dL",
                "tipoValor", "NUMERICO", "codigoLoinc", "0000-0", "rangos", List.of(
                        Map.of("especieId", perro, "valorMin", 10, "valorMax", 20, "criticoMin", 2, "criticoMax", 40),
                        Map.of("especieId", perro, "sexo", "M", "valorMin", 12, "valorMax", 22),
                        Map.of("especieId", perro, "edadMinMeses", 0, "edadMaxMeses", 3, "valorMin", 5, "valorMax", 8),
                        Map.of("especieId", gato, "valorMin", 1, "valorMax", 2)));
        Map<String, Object> cualitativo = Map.of("codigo", "QLT", "nombre", "Prueba cualitativa", "tipoValor", "CUALITATIVO",
                "valorNormalTexto", "NEGATIVO", "opciones", "NEGATIVO|POSITIVO");
        POST("/api/laboratorio/examenes", admin(), Map.of("codigo", codigo, "nombre", "Examen " + codigo, "tipo", "LAB",
                "muestra", "Suero", "parametros", List.of(analito, cualitativo))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.esPanel").value(true))
                .andExpect(jsonPath("$.parametros[0].rangos", hasSize(4)))
                .andExpect(jsonPath("$.parametros[0].codificacionEstado").value("POR_VALIDAR"));
        return codigo;
    }

    /** Orden + muestra + captura de un analito; devuelve el JSON del item. */
    private String capturaAnalito(Usuario vet, Usuario aux, long paciente, String examen, Object valor) throws Exception {
        long orden = orden(vet, paciente, examen);
        muestra(aux, orden);
        long item = itemId(orden, examen);
        return json(capturar(aux.bearer(), orden, item, "ANL", valor).andExpect(status().isOk()));
    }

    @Test
    void banderaAutomaticaSegunEspecieSexoYEdadConSnapshotDelRango() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        String examen = examenDePrueba();
        long perraAdulta = pacienteDe("Perro", "F", hoy().minusYears(3));
        long perroAdulto = pacienteDe("Perro", "M", hoy().minusYears(3));
        long cachorra = pacienteDe("Perro", "F", hoy().minusMonths(2));
        long gato = pacienteDe("Gato", "M", hoy().minusYears(2));

        // perra adulta: rango general 10-20, critico <2 / >40
        assertThat(leer(capturaAnalito(vet, aux, perraAdulta, examen, 25), "$.resultados[?(@.parametroCodigo=='ANL')].bandera")).isEqualTo("H");
        assertThat(leer(capturaAnalito(vet, aux, perraAdulta, examen, 15), "$.resultados[0].bandera")).isEqualTo("N");
        assertThat(leer(capturaAnalito(vet, aux, perraAdulta, examen, 5), "$.resultados[0].bandera")).isEqualTo("L");
        String critico = capturaAnalito(vet, aux, perraAdulta, examen, 45);
        assertThat(leer(critico, "$.resultados[0].bandera")).isEqualTo("HH");
        assertThat(leer(critico, "$.resultados[0].critico")).isEqualTo("true");
        String criticoBajo = capturaAnalito(vet, aux, perraAdulta, examen, 1);
        assertThat(leer(criticoBajo, "$.resultados[0].bandera")).isEqualTo("LL");
        // snapshot del rango aplicado
        assertThat(new BigDecimal(leer(critico, "$.resultados[0].refMin"))).isEqualByComparingTo("10");
        assertThat(new BigDecimal(leer(critico, "$.resultados[0].refMax"))).isEqualByComparingTo("20");
        assertThat(new BigDecimal(leer(critico, "$.resultados[0].refCriticoMax"))).isEqualByComparingTo("40");
        assertThat(leer(critico, "$.resultados[0].unidad")).isEqualTo("mg/dL");

        // macho adulto: gana el rango especifico por sexo (12-22): 11 es bajo, 21 normal (en el general seria alto)
        assertThat(leer(capturaAnalito(vet, aux, perroAdulto, examen, 21), "$.resultados[0].bandera")).isEqualTo("N");
        assertThat(leer(capturaAnalito(vet, aux, perroAdulto, examen, 11), "$.resultados[0].bandera")).isEqualTo("L");

        // cachorra de 2 meses: gana el rango por edad (5-8): 6 normal; 9 alto (para un adulto 9 seria bajo)
        assertThat(leer(capturaAnalito(vet, aux, cachorra, examen, 6), "$.resultados[0].bandera")).isEqualTo("N");
        assertThat(leer(capturaAnalito(vet, aux, cachorra, examen, 9), "$.resultados[0].bandera")).isEqualTo("H");
        assertThat(leer(capturaAnalito(vet, aux, perraAdulta, examen, 9), "$.resultados[0].bandera")).isEqualTo("L");

        // otra especie: gato 1-2
        assertThat(leer(capturaAnalito(vet, aux, gato, examen, 3), "$.resultados[0].bandera")).isEqualTo("H");
        assertThat(leer(capturaAnalito(vet, aux, gato, examen, 1.5), "$.resultados[0].bandera")).isEqualTo("N");

        // valor con coma decimal en texto y comparador
        long orden = orden(vet, perraAdulta, examen);
        muestra(aux, orden);
        long item = itemId(orden, examen);
        PUT("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/resultados", aux.bearer(),
                Map.of("resultados", List.of(Map.of("parametroCodigo", "ANL", "valorTexto", "12,5"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultados[0].valorNumerico").value(12.5))
                .andExpect(jsonPath("$.resultados[0].bandera").value("N"));
    }

    @Test
    void cualitativosYValidacionesDeEntrada() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        String examen = examenDePrueba();
        long paciente = pacienteDe("Perro", "F", hoy().minusYears(1));
        long orden = orden(vet, paciente, examen);
        long item = itemId(orden, examen);
        // sin muestra no se captura
        capturar(aux.bearer(), orden, item, "ANL", 12).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("toma de muestra")));
        muestra(aux, orden);
        // la muestra no puede registrarse dos veces
        POST("/api/laboratorio/ordenes/" + orden + "/muestra", aux.bearer(), Map.of()).andExpect(status().isConflict());

        capturar(aux.bearer(), orden, item, "QLT", "POS").andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='QLT')].valorTexto").value("POSITIVO"))
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='QLT')].bandera").value("A"));
        capturar(aux.bearer(), orden, item, "QLT", "negativo").andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='QLT')].bandera").value("N"));
        capturar(aux.bearer(), orden, item, "QLT", "tal vez").andExpect(status().isUnprocessableEntity());
        capturar(aux.bearer(), orden, item, "ANL", "abc").andExpect(status().isUnprocessableEntity());
        capturar(aux.bearer(), orden, item, "NOEXISTE", 3).andExpect(status().isUnprocessableEntity());
        // unidad que no coincide con la del parametro
        PUT("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/resultados", aux.bearer(),
                Map.of("resultados", List.of(Map.of("parametroCodigo", "ANL", "valorNumerico", 12, "unidad", "g/L"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("no coincide")));
        // un parametro de otro examen
        PUT("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/resultados", aux.bearer(),
                Map.of("resultados", List.of(Map.of("parametroId", parametroId("HEMO", "HCT"), "valorNumerico", 40))))
                .andExpect(status().isUnprocessableEntity());
        // la orden pasa a EN_PROCESO al capturar
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.items[0].estado").value("RESULTADO"));
    }

    @Test
    void sembradoDeHemogramaConRangosPorEspecieYCriticos() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long perro = pacienteDe("Perro", "M", hoy().minusYears(4));
        long gato = pacienteDe("Gato", "F", hoy().minusYears(4));
        long cachorro = pacienteDe("Perro", "M", hoy().minusMonths(2));

        long o1 = orden(vet, perro, "HEMO");
        muestra(aux, o1);
        capturar(aux.bearer(), o1, itemId(o1, "HEMO"), "HCT", 30, "WBC", 22, "PLT", 350).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='HCT')].bandera").value("L"))
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='WBC')].bandera").value("H"))
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='PLT')].bandera").value("N"))
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='HCT')].codigoLoinc").value("4544-3"));
        capturar(aux.bearer(), o1, itemId(o1, "HEMO"), "HCT", 12).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='HCT')].bandera").value("LL"))
                .andExpect(jsonPath("$.resultados[?(@.parametroCodigo=='HCT')].critico").value(true));
        GET("/api/laboratorio/ordenes/" + o1, vet.bearer()).andExpect(jsonPath("$.hayCriticos").value(true));

        long o2 = orden(vet, gato, "HEMO");
        muestra(aux, o2);
        capturar(aux.bearer(), o2, itemId(o2, "HEMO"), "HCT", 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].bandera").value("H"));

        // cachorro de 2 meses: rango juvenil 26-43 (un 30 seria bajo en adulto)
        long o3 = orden(vet, cachorro, "HEMO");
        muestra(aux, o3);
        capturar(aux.bearer(), o3, itemId(o3, "HEMO"), "HCT", 30).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].bandera").value("N"));

        // catalogo: LOINC y UCUM por validar, paneles semillados
        String hemo = json(GET("/api/laboratorio/examenes/" + examenId("HEMO"), vet.bearer()).andExpect(status().isOk()));
        assertThat(leer(hemo, "$.parametros[?(@.codigo=='WBC')].unidadUcum")).isEqualTo("10*3/uL");
        assertThat(leer(hemo, "$.parametros[?(@.codigo=='WBC')].codificacionEstado")).isEqualTo("POR_VALIDAR");
        assertThat(leer(hemo, "$.parametros[?(@.codigo=='FROT')].codigoLoinc")).isEqualTo("null");
        for (String codigo : List.of("QUIM", "HEPA", "RENAL", "ELEC", "UROA", "COPRO", "PARVO", "FELVFIV", "HEMOPAR", "RX", "ECO-ABD", "CITO", "HISTO")) {
            assertThat(examenId(codigo)).isPositive();
        }
        GET("/api/laboratorio/examenes?tipo=IMAGEN", vet.bearer()).andExpect(jsonPath("$[*].codigo", hasItem("ECO-ABD")));
        GET("/api/laboratorio/examenes?tipo=INVALIDO", vet.bearer()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validacionInmutabilidadYCorreccionEnlazadaAppendOnly() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "F", hoy().minusYears(2));
        long orden = orden(vet, paciente, "HEMO");
        muestra(aux, orden);
        long item = itemId(orden, "HEMO");
        capturar(aux.bearer(), orden, item, "HCT", 12, "WBC", 10).andExpect(status().isOk());

        // el auxiliar captura pero no valida
        validar(aux.bearer(), orden, item, "ok").andExpect(status().isForbidden());
        // valor critico: la interpretacion es obligatoria
        validar(vet.bearer(), orden, item, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("critico")));
        validar(vet.bearer(), orden, item, "Anemia severa: transfusion").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.validadoPor.id").value(vet.id()))
                .andExpect(jsonPath("$.interpretacion").value("Anemia severa: transfusion"))
                .andExpect(jsonPath("$.resultados[0].estado").value("VALIDADO"));
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.completadaEn").exists());

        // inmutable: no se captura, no se revalida y la base de datos rechaza UPDATE/DELETE directos
        capturar(aux.bearer(), orden, item, "HCT", 41).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("correccion")));
        validar(vet.bearer(), orden, item, "otra").andExpect(status().isConflict());
        long resultadoId = jdbc.sql("select id from resultado_examen where orden_item_id = :i and estado = 'VALIDADO' order by id limit 1")
                .param("i", item).query(Long.class).single();
        assertThatThrownBy(() -> jdbc.sql("update resultado_examen set valor_numerico = 99 where id = :id").param("id", resultadoId).update())
                .hasMessageContaining("inmutable");
        assertThatThrownBy(() -> jdbc.sql("delete from resultado_examen where id = :id").param("id", resultadoId).update())
                .hasMessageContaining("no se puede eliminar");
        assertThatThrownBy(() -> jdbc.sql("update orden_item_validacion set interpretacion = 'x' where orden_item_id = :i").param("i", item).update())
                .hasMessageContaining("append-only");

        // correccion enlazada: exige motivo; el original se conserva como CORREGIDO
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(),
                Map.of("resultados", List.of(Map.of("parametroCodigo", "HCT", "valorNumerico", 41)))).andExpect(status().isBadRequest());
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(),
                Map.of("motivo", "abc", "resultados", List.of(Map.of("parametroCodigo", "HCT", "valorNumerico", 41))))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", aux.bearer(),
                Map.of("motivo", "Error de digitacion", "resultados", List.of(Map.of("parametroCodigo", "HCT", "valorNumerico", 41))))
                .andExpect(status().isForbidden());
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(), Map.of("motivo", "Error de digitacion"))
                .andExpect(status().isUnprocessableEntity());
        String corregido = json(POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(),
                Map.of("motivo", "Error de digitacion del hematocrito", "interpretacion", "Hematocrito normal tras correccion",
                        "resultados", List.of(Map.of("parametroCodigo", "HCT", "valorNumerico", 41))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.corregido").value(true))
                .andExpect(jsonPath("$.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.interpretacion").value("Hematocrito normal tras correccion")));
        String nuevoHct = "$.resultados[?(@.parametroCodigo=='HCT')]";
        assertThat(new BigDecimal(leer(corregido, nuevoHct + ".valorNumerico"))).isEqualByComparingTo("41");
        assertThat(leer(corregido, nuevoHct + ".bandera")).isEqualTo("N");
        assertThat(leer(corregido, nuevoHct + ".estado")).isEqualTo("VALIDADO");
        assertThat(leer(corregido, nuevoHct + ".motivoCorreccion")).isEqualTo("Error de digitacion del hematocrito");
        long nuevoId = Long.parseLong(leer(corregido, nuevoHct + ".id"));
        assertThat(leer(corregido, nuevoHct + ".reemplazaA")).isEqualTo(String.valueOf(resultadoId));
        // el original sigue ahi, marcado CORREGIDO y enlazado al nuevo; el otro parametro (WBC) no se toco
        String original = "$.resultadosCorregidos[?(@.id==" + resultadoId + ")]";
        assertThat(leer(corregido, original + ".estado")).isEqualTo("CORREGIDO");
        assertThat(new BigDecimal(leer(corregido, original + ".valorNumerico"))).isEqualByComparingTo("12");
        assertThat(leer(corregido, original + ".reemplazadoPor")).isEqualTo(String.valueOf(nuevoId));
        assertThat(leer(corregido, "$.resultados[?(@.parametroCodigo=='WBC')].estado")).isEqualTo("VALIDADO");
        assertThat(leer(corregido, "$.resultadosCorregidos.length()")).isEqualTo("1");
        // append-only en la base: el original no cambia ni se borra; el corregido tampoco puede revertirse
        assertThatThrownBy(() -> jdbc.sql("update resultado_examen set estado = 'VALIDADO' where id = :id").param("id", resultadoId).update())
                .hasMessageContaining("inmutable");
        assertThat(jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i").param("i", item).query(Long.class).single()).isEqualTo(3L);
        // auditoria por trigger: la captura del auxiliar y la validacion del veterinario quedan con su usuario
        assertThat(jdbc.sql("select count(*) from auditoria where entidad = 'resultado_examen' and usuario_id = :u").param("u", aux.id())
                .query(Long.class).single()).isGreaterThanOrEqualTo(2L);
        assertThat(jdbc.sql("select count(*) from auditoria where entidad = 'resultado_examen' and accion = 'UPDATE' and usuario_id = :u")
                .param("u", vet.id()).query(Long.class).single()).isGreaterThanOrEqualTo(1L);
        // la bitacora registra todo el flujo
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[*].tipo", org.hamcrest.Matchers.contains("CREADA", "MUESTRA", "ESTADO", "CAPTURA", "VALIDACION", "ESTADO", "CORRECCION")))
                .andExpect(jsonPath("$.hayCriticos").value(false));
        // una segunda correccion enlaza a la anterior (cadena de versiones)
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(),
                Map.of("motivo", "Reconfirmacion en otro equipo", "resultados", List.of(Map.of("parametroCodigo", "HCT", "valorNumerico", 43))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.resultadosCorregidos", hasSize(2)));
    }

    @Test
    void flujoDeEstadosBandejaYRoles() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        long paciente = pacienteDe("Gato", "F", hoy().minusYears(5));

        // solo ADMIN/VETERINARIO ordenan
        POST("/api/laboratorio/ordenes", aux.bearer(), Map.of("pacienteId", paciente, "examenCodigos", List.of("HEMO")))
                .andExpect(status().isForbidden());
        POST("/api/laboratorio/ordenes", recepcion.bearer(), Map.of("pacienteId", paciente, "examenCodigos", List.of("HEMO")))
                .andExpect(status().isForbidden());
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", paciente)).andExpect(status().isUnprocessableEntity());
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", paciente, "examenCodigos", List.of("NOEXISTE")))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", paciente, "prioridad", "YA", "examenCodigos", List.of("HEMO")))
                .andExpect(status().isBadRequest());
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", 9999999, "examenCodigos", List.of("HEMO")))
                .andExpect(status().isNotFound());

        long rutina = ordenCon(vet, paciente, "RUTINA", null, "HEMO");
        long stat = ordenCon(vet, paciente, "STAT", null, "QUIM", "UROA");
        long urgente = ordenCon(vet, paciente, "URGENTE", null, "HEMO");
        GET("/api/laboratorio/ordenes?pacienteId=" + paciente, aux.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", org.hamcrest.Matchers.contains((int) stat, (int) urgente, (int) rutina)))
                .andExpect(jsonPath("$.items[0].prioridad").value("STAT"))
                .andExpect(jsonPath("$.items[0].itemsTotal").value(2))
                .andExpect(jsonPath("$.items[0].examenes", hasSize(2)));
        GET("/api/laboratorio/ordenes", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/laboratorio/ordenes/" + stat, recepcion.bearer()).andExpect(status().isForbidden());

        // transiciones
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", vet.bearer(), Map.of("estado", "EN_PROCESO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("muestra")));
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", aux.bearer(), Map.of("estado", "CANCELADA", "motivo", "x"))
                .andExpect(status().isForbidden());
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", vet.bearer(), Map.of("estado", "CANCELADA"))
                .andExpect(status().isUnprocessableEntity());
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", vet.bearer(), Map.of("estado", "COMPLETADA")).andExpect(status().isBadRequest());
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", vet.bearer(), Map.of("estado", "CANCELADA", "motivo", "Tutor desistio"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.items[0].estado").value("CANCELADO")).andExpect(jsonPath("$.motivoCancelacion").value("Tutor desistio"));
        POST("/api/laboratorio/ordenes/" + rutina + "/estado", vet.bearer(), Map.of("estado", "CANCELADA", "motivo", "otra vez"))
                .andExpect(status().isConflict());
        muestra(aux, stat);
        capturar(aux.bearer(), rutina, itemId(rutina, "HEMO"), "HCT", 35).andExpect(status().isConflict());
        // canceladas al final de la bandeja; filtros por estado/prioridad/tipo
        GET("/api/laboratorio/ordenes?pacienteId=" + paciente, vet.bearer())
                .andExpect(jsonPath("$.items[*].id", org.hamcrest.Matchers.contains((int) stat, (int) urgente, (int) rutina)));
        GET("/api/laboratorio/ordenes?pacienteId=" + paciente + "&estado=SOLICITADA", vet.bearer())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value(urgente));
        GET("/api/laboratorio/ordenes?pacienteId=" + paciente + "&estado=SOLICITADA,MUESTRA_TOMADA&prioridad=STAT", vet.bearer())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].estado").value("MUESTRA_TOMADA"));
        GET("/api/laboratorio/ordenes?pacienteId=" + paciente + "&tipo=IMAGEN", vet.bearer()).andExpect(jsonPath("$.items", hasSize(0)));
        GET("/api/laboratorio/ordenes?estado=INVENTADO", vet.bearer()).andExpect(status().isUnprocessableEntity());

        // completar: valida un examen y luego el otro; la orden queda COMPLETADA solo con todos validados
        long quim = itemId(stat, "QUIM");
        long uroa = itemId(stat, "UROA");
        capturar(aux.bearer(), stat, quim, "GLU", 95).andExpect(status().isOk());
        capturar(aux.bearer(), stat, uroa, "DENS", 1.040, "PROT", "NEGATIVO").andExpect(status().isOk());
        validar(vet.bearer(), stat, quim, "Glucosa normal").andExpect(status().isOk());
        GET("/api/laboratorio/ordenes/" + stat, vet.bearer()).andExpect(jsonPath("$.estado").value("EN_PROCESO"));
        // no se cancela una orden con examenes ya validados
        POST("/api/laboratorio/ordenes/" + stat + "/estado", vet.bearer(), Map.of("estado", "CANCELADA", "motivo", "Equivocada"))
                .andExpect(status().isConflict());
        validar(vet.bearer(), stat, uroa, null).andExpect(status().isOk());
        GET("/api/laboratorio/ordenes/" + stat, vet.bearer()).andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.items[?(@.examenCodigo=='UROA')].resultados[?(@.parametroCodigo=='DENS')].bandera").value("N"));
    }

    @Test
    void tendenciaDeUnParametroEnElTiempoSoloValidados() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "M", hoy().minusYears(6));
        long hct = parametroId("HEMO", "HCT");
        for (int v : new int[] {30, 36, 44}) {
            long o = orden(vet, paciente, "HEMO");
            muestra(aux, o);
            long item = itemId(o, "HEMO");
            capturar(aux.bearer(), o, item, "HCT", v).andExpect(status().isOk());
            validar(vet.bearer(), o, item, "Control de hematocrito").andExpect(status().isOk());
        }
        // un borrador sin validar no entra en la serie
        long pendiente = orden(vet, paciente, "HEMO");
        muestra(aux, pendiente);
        capturar(aux.bearer(), pendiente, itemId(pendiente, "HEMO"), "HCT", 10).andExpect(status().isOk());

        GET("/api/pacientes/" + paciente + "/laboratorio/tendencia?parametroId=" + hct, vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.parametro.codigo").value("HCT")).andExpect(jsonPath("$.parametro.unidad").value("%"))
                .andExpect(jsonPath("$.puntos", hasSize(3)))
                .andExpect(jsonPath("$.puntos[*].valorNumerico", org.hamcrest.Matchers.contains(30.0, 36.0, 44.0)))
                .andExpect(jsonPath("$.puntos[0].bandera").value("L")).andExpect(jsonPath("$.puntos[2].bandera").value("N"))
                .andExpect(jsonPath("$.resumen.ultimo").value(44.0)).andExpect(jsonPath("$.resumen.anterior").value(36.0))
                .andExpect(jsonPath("$.resumen.variacionAbsoluta").value(8.0)).andExpect(jsonPath("$.resumen.direccion").value("SUBE"))
                .andExpect(jsonPath("$.resumen.minimo").value(30.0)).andExpect(jsonPath("$.resumen.maximo").value(44.0));
        GET("/api/pacientes/" + paciente + "/laboratorio/tendencia?parametroId=" + hct, aux.bearer()).andExpect(status().isOk());
        GET("/api/pacientes/" + paciente + "/laboratorio/tendencia", vet.bearer()).andExpect(status().isBadRequest());
        GET("/api/pacientes/" + paciente + "/laboratorio/tendencia?parametroId=99999999", vet.bearer()).andExpect(status().isNotFound());
        // historial de ordenes del paciente (4 ordenes)
        GET("/api/pacientes/" + paciente + "/laboratorio", vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.items[0].id").value(pendiente));
        // tendencia de un paciente sin resultados
        long otro = pacienteDe("Perro", "M", null);
        GET("/api/pacientes/" + otro + "/laboratorio/tendencia?parametroId=" + hct, vet.bearer())
                .andExpect(jsonPath("$.puntos", hasSize(0))).andExpect(jsonPath("$.resumen.direccion").value("SIN_DATOS"));
    }

    @Test
    void historiaIncluyeResultadosValidadosYConsultaCerradaRecibeAddendumTardio() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        long paciente = pacienteDe("Perro", "F", hoy().minusYears(3));
        long consulta = idDe(POST("/api/consultas", vet.bearer(), Map.of("pacienteId", paciente, "veterinarioId", vet.id(),
                "fecha", hoy().toString(), "motivo", "Decaimiento")).andExpect(status().isCreated()));
        // una orden no puede vincularse a la consulta de otro paciente
        long otro = pacienteDe("Perro", "F", null);
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", otro, "consultaId", consulta, "examenCodigos", List.of("HEMO")))
                .andExpect(status().isUnprocessableEntity());

        long orden = ordenCon(vet, paciente, "URGENTE", consulta, "HEMO");
        muestra(aux, orden);
        long item = itemId(orden, "HEMO");
        capturar(aux.bearer(), orden, item, "HCT", 25, "WBC", 30).andExpect(status().isOk());
        GET("/api/laboratorio/ordenes?consultaId=" + consulta, vet.bearer()).andExpect(jsonPath("$.items", hasSize(1)));
        // sin validar no aparece en la historia
        GET("/api/pacientes/" + paciente + "/historia?tipo=LABORATORIO", vet.bearer()).andExpect(jsonPath("$.totalElements").value(0));

        // la consulta se cierra antes de que llegue el resultado: queda inmutable
        POST("/api/consultas/" + consulta + "/cerrar", vet.bearer(), Map.of()).andExpect(status().isOk());
        validar(vet.bearer(), orden, item, "Anemia leve y leucocitosis: infeccion").andExpect(status().isOk());
        String addendums = json(GET("/api/consultas/" + consulta + "/addendums", vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].tipo").value("RESULTADO_TARDIO")));
        assertThat(leer(addendums, "$[0].texto")).contains("Hemograma completo").contains("HCT 25 (L)").contains("WBC 30 (HH)".replace("HH", "H"))
                .contains("Anemia leve");
        // correcciones posteriores tambien dejan constancia
        POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/correccion", vet.bearer(),
                Map.of("motivo", "Muestra mal rotulada, repetida", "resultados", List.of(Map.of("parametroCodigo", "WBC", "valorNumerico", 12))))
                .andExpect(status().isOk());
        GET("/api/consultas/" + consulta + "/addendums", vet.bearer()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].tipo").value("CORRECCION"));

        // historia: aparece el examen validado, con filtros por tipo y sin alterar los tipos existentes
        String historia = json(GET("/api/pacientes/" + paciente + "/historia", vet.bearer()).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(historia, "$.items[*].tipo")).contains("CONSULTA", "LABORATORIO");
        GET("/api/pacientes/" + paciente + "/historia?tipo=LABORATORIO", aux.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].tipo").value("LABORATORIO"))
                .andExpect(jsonPath("$.items[0].referenciaId").value(orden)).andExpect(jsonPath("$.items[0].titulo").value("Hemograma completo"))
                .andExpect(jsonPath("$.items[0].estado").value("VALIDADO"))
                .andExpect(jsonPath("$.items[0].detalle").value(containsString("fuera de rango")));
        GET("/api/pacientes/" + paciente + "/historia?tipo=CONSULTA,LABORATORIO", vet.bearer()).andExpect(jsonPath("$.totalElements").value(2));
        GET("/api/pacientes/" + paciente + "/historia?tipo=CONSULTA", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1));
        GET("/api/pacientes/" + paciente + "/historia?tipo=NOEXISTE", vet.bearer()).andExpect(status().isBadRequest());
        GET("/api/pacientes/" + paciente + "/historia?tipo=LABORATORIO", recepcion.bearer()).andExpect(status().isForbidden());
    }

    @Test
    void informePdfValidoConMembreteTablaYFirma() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "M", hoy().minusYears(3));
        long orden = orden(vet, paciente, "HEMO");
        muestra(aux, orden);
        long item = itemId(orden, "HEMO");
        capturar(aux.bearer(), orden, item, "HCT", 30, "WBC", 22, "PLT", 350).andExpect(status().isOk());

        // sin validar: preliminar
        byte[] preliminar = mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf").header("Authorization", aux.bearer()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(textoPdf(preliminar)).contains("PRELIMINAR");

        validar(vet.bearer(), orden, item, "Anemia leve con leucocitosis").andExpect(status().isOk());
        byte[] pdf = mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf").header("Authorization", vet.bearer()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String texto = textoPdf(pdf);
        assertThat(texto).contains("Clinica Veterinaria").contains("Informe de laboratorio")
                .contains("Hematocrito").contains("Leucocitos").contains("Plaquetas").contains("Bajo").contains("Alto")
                .contains("37 - 55").contains("Anemia leve con leucocitosis").contains("VALIDADO por")
                .contains("Usuario veterinario").contains("Control clinico de prueba")
                .contains("Tutor").contains("Perro").doesNotContain("PRELIMINAR");
        // por examen y control de acceso
        mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf?itemId=" + item).header("Authorization", vet.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf?itemId=999999").header("Authorization", vet.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf").header("Authorization", usuario("RECEPCION").bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoSePuedeActualizarYLosParametrosAusentesSeDesactivan() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        String codigo = examenDePrueba();
        long id = examenId(codigo);
        // codigo duplicado -> 409; auxiliar lee pero no edita
        POST("/api/laboratorio/examenes", vet.bearer(), Map.of("codigo", codigo, "nombre", "Otro", "tipo", "LAB")).andExpect(status().isConflict());
        POST("/api/laboratorio/examenes", aux.bearer(), Map.of("codigo", "X" + codigo, "nombre", "Otro", "tipo", "LAB")).andExpect(status().isForbidden());
        GET("/api/laboratorio/examenes/" + id, aux.bearer()).andExpect(status().isOk());
        POST("/api/laboratorio/examenes", vet.bearer(), Map.of("codigo", "Y" + codigo, "nombre", "Malo", "tipo", "XX")).andExpect(status().isBadRequest());
        POST("/api/laboratorio/examenes", vet.bearer(), Map.of("codigo", "Z" + codigo, "nombre", "Rango malo", "tipo", "LAB",
                "parametros", List.of(Map.of("codigo", "A", "nombre", "A", "rangos",
                        List.of(Map.of("especieId", especieId("Perro"), "valorMin", 5, "valorMax", 1))))))
                .andExpect(status().isUnprocessableEntity());
        // PUT: se conserva ANL (con nuevos rangos), se confirma su LOINC y QLT queda inactivo
        PUT("/api/laboratorio/examenes/" + id, vet.bearer(), Map.of("codigo", codigo, "nombre", "Examen renombrado", "tipo", "LAB",
                "parametros", List.of(Map.of("codigo", "ANL", "nombre", "Analito", "unidad", "mg/dL", "tipoValor", "NUMERICO",
                        "codigoLoinc", "2345-7", "codificacionEstado", "VALIDADO", "rangos",
                        List.of(Map.of("especieId", especieId("Perro"), "valorMin", 50, "valorMax", 60))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Examen renombrado"))
                .andExpect(jsonPath("$.esPanel").value(false))
                .andExpect(jsonPath("$.parametros[?(@.codigo=='ANL')].codificacionEstado").value("VALIDADO"))
                .andExpect(jsonPath("$.parametros[?(@.codigo=='ANL')].rangos[0].valorMin").value(50.0))
                .andExpect(jsonPath("$.parametros[?(@.codigo=='QLT')].activo").value(false));
        // un parametro inactivo ya no se puede capturar
        long paciente = pacienteDe("Perro", "F", hoy().minusYears(1));
        long orden = orden(vet, paciente, codigo);
        muestra(aux, orden);
        capturar(aux.bearer(), orden, itemId(orden, codigo), "QLT", "NEGATIVO").andExpect(status().isUnprocessableEntity());
        capturar(aux.bearer(), orden, itemId(orden, codigo), "ANL", 55).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].bandera").value("N"));
        // un examen inactivo no se ordena
        PUT("/api/laboratorio/examenes/" + id, vet.bearer(), Map.of("codigo", codigo, "nombre", "Inactivo", "tipo", "LAB", "activo", false))
                .andExpect(status().isOk());
        POST("/api/laboratorio/ordenes", vet.bearer(), Map.of("pacienteId", paciente, "examenCodigos", List.of(codigo)))
                .andExpect(status().isUnprocessableEntity());
        GET("/api/laboratorio/examenes?q=" + codigo, vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
    }
}
