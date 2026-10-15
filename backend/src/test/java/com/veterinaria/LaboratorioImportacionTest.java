package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class LaboratorioImportacionTest extends EtapaATestSupport {

    private record Caso(Usuario vet, Usuario aux, long paciente, String pacienteCodigo, long orden, String ordenCodigo, long item) {
    }

    /** Perro adulto con una orden de hemograma con muestra tomada (lista para recibir resultados). */
    private Caso caso(boolean conMuestra, String... examenes) throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "M", LocalDate.now().minusYears(4));
        String pacienteCodigo = leer(json(GET("/api/pacientes/" + paciente, vet.bearer())), "$.codigo");
        long orden = orden(vet, paciente, examenes.length == 0 ? new String[] {"HEMO"} : examenes);
        if (conMuestra) {
            muestra(aux, orden);
        }
        String ordenCodigo = leer(json(GET("/api/laboratorio/ordenes/" + orden, vet.bearer())), "$.codigo");
        return new Caso(vet, aux, paciente, pacienteCodigo, orden, ordenCodigo, itemId(orden, examenes.length == 0 ? "HEMO" : examenes[0]));
    }

    private static String hl7(Caso c, String ordenRef) {
        return String.join("\r",
                "MSH|^~\\&|ANALIZADOR|LAB|VETSYS|CLINICA|20261015101500||ORU^R01|MSG" + System.nanoTime() + "|P|2.5",
                "PID|1||" + c.pacienteCodigo() + "^^^CLINICA^MR||Mascota^Prueba",
                "OBR|1|" + ordenRef + "|FILL123|HEMO^Hemograma^L|||20261015100000",
                "OBX|1|NM|4544-3^Hematocrit^LN||30.5|%|37-55|L|||F",
                "OBX|2|NM|WBC^Leucocitos^L||22,3|10*3/uL|6-17|H|||F",
                "OBX|3|NM|PLT^Plaquetas^L||350|x10\\S\\3/uL|||||F",
                "OBX|4|NM|XYZ^Desconocido^L||5|u||||F",
                "NTE|1||Parametro no configurado en el catalogo",
                "OBX|5|NM|HGB^Hemoglobina^L||13|mg/dL||||F",
                "") ;
    }

    private ResultActions importarJson(String bearer, Object body) throws Exception {
        return POST("/api/laboratorio/importar", bearer, body);
    }

    private ResultActions integracionHl7(String apiKey, String mensaje, String query) throws Exception {
        var req = post("/api/laboratorio/integracion/hl7" + query).contentType("text/plain").content(mensaje);
        if (apiKey != null) {
            req.header("X-API-Key", apiKey);
        }
        return mockMvc.perform(req);
    }

    @Test
    void hl7DryRunInformaQueSeImportariaYQueNoCoincideSinEscribirNada() throws Exception {
        Caso c = caso(true);
        long importacionesAntes = jdbc.sql("select count(*) from lab_importacion").query(Long.class).single();
        String mensaje = hl7(c, c.ordenCodigo());
        importarJson(c.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje, "dryRun", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.importados").value(3))
                .andExpect(jsonPath("$.noCoinciden").value(1)).andExpect(jsonPath("$.rechazados").value(1))
                .andExpect(jsonPath("$.importacionId").doesNotExist())
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='HCT')]").doesNotExist())
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].estado").value("IMPORTABLE"))
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].bandera").value("L"))
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].parametro").value("Hematocrito"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='WBC')].bandera").value("H"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='PLT')].estado").value("IMPORTABLE"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='XYZ')].estado").value("SIN_COINCIDENCIA"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='HGB')].estado").value("UNIDAD_NO_COINCIDE"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='HGB')].mensaje", org.hamcrest.Matchers.hasItem(containsString("no coincide"))))
                .andExpect(jsonPath("$.ordenIds", hasSize(1)));
        // no se escribio nada
        assertThat(jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i").param("i", c.item()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from lab_importacion").query(Long.class).single()).isEqualTo(importacionesAntes);
        GET("/api/laboratorio/ordenes/" + c.orden(), c.vet().bearer()).andExpect(jsonPath("$.estado").value("MUESTRA_TOMADA"));
    }

    @Test
    void hl7RealImportaBorradoresConBanderasYNoSeReprocesa() throws Exception {
        Caso c = caso(true);
        String mensaje = hl7(c, c.ordenCodigo());
        importarJson(c.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje, "nombreArchivo", "x"))
                .andExpect(status().isBadRequest()); // propiedad desconocida: el contrato es estricto
        String res = json(importarJson(c.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(false)).andExpect(jsonPath("$.importados").value(3))
                .andExpect(jsonPath("$.importacionId").isNumber()).andExpect(jsonPath("$.origen").value("ARCHIVO"))
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].estado").value("IMPORTADO")));
        assertThat(res).contains("SIN_COINCIDENCIA");

        String orden = json(GET("/api/laboratorio/ordenes/" + c.orden(), c.vet().bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO")).andExpect(jsonPath("$.items[0].estado").value("RESULTADO"))
                .andExpect(jsonPath("$.items[0].resultados", hasSize(3)))
                .andExpect(jsonPath("$.eventos[?(@.tipo=='IMPORTACION')]").exists()));
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='HCT')].estado")).isEqualTo("BORRADOR");
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='HCT')].origen")).isEqualTo("ARCHIVO");
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='HCT')].bandera")).isEqualTo("L");
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='HCT')].observacion")).contains("Bandera del equipo: L");
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='WBC')].valorNumerico")).isEqualTo("22.3");
        assertThat(leer(orden, "$.items[0].resultados[?(@.parametroCodigo=='WBC')].bandera")).isEqualTo("H");

        // el mismo mensaje no se importa dos veces; el mensaje original queda guardado
        importarJson(c.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje)).andExpect(status().isConflict());
        assertThat(jdbc.sql("select contenido from lab_importacion order by id desc limit 1").query(String.class).single()).contains("OBX|1|NM|4544-3");
        // el veterinario valida lo importado
        validar(c.vet().bearer(), c.orden(), c.item(), "Anemia y leucocitosis").andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].estado").value("VALIDADO"));
        // con todos los examenes validados la orden queda COMPLETADA y rechaza nuevas importaciones (hay que corregir)
        importarJson(c.aux().bearer(), Map.of("formato", "HL7", "dryRun", true, "contenido", hl7(c, c.ordenCodigo())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importados").value(0))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='WBC')].estado").value("ORDEN_CERRADA"));
    }

    @Test
    void hl7ResuelveLaOrdenPorPacienteYExigeMuestra() throws Exception {
        Caso sinMuestra = caso(false);
        // OBR-2 vacio: se resuelve por el paciente (PID-3) porque solo tiene una orden abierta
        String mensaje = hl7(sinMuestra, "");
        importarJson(sinMuestra.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje, "dryRun", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importados").value(0))
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].estado").value("ORDEN_SIN_MUESTRA"))
                .andExpect(jsonPath("$.lineas[?(@.loinc=='4544-3')].ordenCodigo").value(sinMuestra.ordenCodigo()));
        importarJson(sinMuestra.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje)).andExpect(status().isUnprocessableEntity());
        muestra(sinMuestra.aux(), sinMuestra.orden());
        importarJson(sinMuestra.aux().bearer(), Map.of("formato", "HL7", "contenido", mensaje)).andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(3));

        // con dos ordenes abiertas hay ambiguedad: se pide la orden
        Caso dos = caso(true);
        orden(dos.vet(), dos.paciente(), "HEMO");
        importarJson(dos.aux().bearer(), Map.of("formato", "HL7", "contenido", hl7(dos, ""), "dryRun", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.noCoinciden").value(5))
                .andExpect(jsonPath("$.lineas[0].estado").value("SIN_ORDEN"))
                .andExpect(jsonPath("$.lineas[0].mensaje").value(containsString("2 ordenes abiertas")));
        // orden inexistente / mensaje invalido
        importarJson(dos.aux().bearer(), Map.of("formato", "HL7", "contenido", hl7(dos, "ORD-NO-EXISTE"), "dryRun", true))
                .andExpect(jsonPath("$.lineas[0].estado").value("SIN_ORDEN"));
        importarJson(dos.aux().bearer(), Map.of("formato", "HL7", "contenido", "esto no es HL7")).andExpect(status().isUnprocessableEntity());
        importarJson(dos.aux().bearer(), Map.of("formato", "HL7", "contenido", "MSH|^~\\&|A|B|C|D|2026||ADT^A01|1|P|2.5"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(containsString("ORU^R01")));
        // ordenId explicito manda sobre la linea: si la linea apunta a otra orden se rechaza (evita cargar resultados a otro paciente)
        importarJson(dos.aux().bearer(), Map.of("formato", "HL7", "contenido", hl7(dos, sinMuestra.ordenCodigo()), "ordenId", dos.orden(), "dryRun", true))
                .andExpect(jsonPath("$.lineas[0].estado").value("ORDEN_NO_COINCIDE"));
    }

    @Test
    void csvConPlantillaDescargableDryRunYReal() throws Exception {
        Caso c = caso(true, "HEMO", "PARVO");
        String plantilla = mockMvc.perform(get("/api/laboratorio/plantilla-csv").header("Authorization", c.aux().bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("plantilla-resultados-laboratorio.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(plantilla).startsWith("orden_codigo,examen_codigo,parametro_codigo,loinc,valor,unidad,observacion");
        // la plantilla se puede cargar tal cual (sus filas de ejemplo apuntan a una orden que no existe)
        importarJson(c.aux().bearer(), Map.of("formato", "CSV", "contenido", plantilla, "dryRun", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importados").value(0)).andExpect(jsonPath("$.total").value(3));

        String csv = "orden_codigo;examen_codigo;parametro_codigo;loinc;valor;unidad;observacion\n"
                + "# comentario\n"
                + c.ordenCodigo() + ";HEMO;HCT;;41,5;%;Control\n"
                + c.ordenCodigo() + ";PARVO;PARVO;;NEG;;\n"
                + c.ordenCodigo() + ";HEMO;ZZZ;;1;;\n"
                + c.ordenCodigo() + ";HEMO;WBC;;abc;;\n"
                + "\"" + c.ordenCodigo() + "\";HEMO;PLT;;\"350\";x10^3/uL;\"con, coma\"\n";
        importarJson(c.aux().bearer(), Map.of("formato", "CSV", "contenido", csv, "dryRun", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.importados").value(3)).andExpect(jsonPath("$.noCoinciden").value(1))
                .andExpect(jsonPath("$.rechazados").value(1))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='ZZZ')].estado").value("SIN_COINCIDENCIA"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='WBC')].estado").value("VALOR_INVALIDO"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='PARVO')].bandera").value("N"));
        // archivo multipart con ordenId (sin columna de orden)
        String csvSinOrden = "parametro_codigo,valor,unidad\nHCT,41.5,%\nPARVO,POSITIVO,\n";
        mockMvc.perform(multipart("/api/laboratorio/importar").file(new MockMultipartFile("archivo", "resultados.csv", "text/csv",
                        csvSinOrden.getBytes(StandardCharsets.UTF_8)))
                .param("formato", "CSV").param("ordenId", String.valueOf(c.orden())).param("dryRun", "false")
                .header("Authorization", c.aux().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importados").value(2)).andExpect(jsonPath("$.origen").value("ARCHIVO"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='PARVO')].bandera").value("A"))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='PARVO')].critico").value(false));
        GET("/api/laboratorio/ordenes/" + c.orden(), c.vet().bearer())
                .andExpect(jsonPath("$.items[?(@.examenCodigo=='PARVO')].resultados[0].valorTexto").value("POSITIVO"))
                .andExpect(jsonPath("$.items[?(@.examenCodigo=='PARVO')].resultados[0].bandera").value("A"))
                .andExpect(jsonPath("$.items[?(@.examenCodigo=='HEMO')].resultados[0].valorNumerico").value(41.5));
        // un item validado dentro de una orden abierta se rechaza con ITEM_VALIDADO (hay que corregir)
        validar(c.vet().bearer(), c.orden(), itemId(c.orden(), "PARVO"), null).andExpect(status().isOk());
        importarJson(c.aux().bearer(), Map.of("formato", "CSV", "dryRun", true, "ordenId", c.orden(),
                "contenido", "parametro_codigo,valor\nPARVO,NEGATIVO\nHCT,40\n")).andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.lineas[?(@.parametroCodigo=='PARVO')].estado").value("ITEM_VALIDADO"));
        // csv sin encabezado valido
        importarJson(c.aux().bearer(), Map.of("formato", "CSV", "contenido", "a,b\n1,2\n")).andExpect(status().isUnprocessableEntity());
        // recepcion no importa
        importarJson(usuario("RECEPCION").bearer(), Map.of("formato", "CSV", "contenido", csv)).andExpect(status().isForbidden());
    }

    @Test
    void jsonDirectoYEndpointDeIntegracionConApiKey() throws Exception {
        Caso c = caso(true);
        Map<String, Object> cuerpo = Map.of("ordenCodigo", c.ordenCodigo(), "resultados", List.of(
                Map.of("parametroCodigo", "HCT", "valor", "44", "unidad", "%"),
                Map.of("loinc", "6690-2", "valor", "8.5")));
        // seguridad del endpoint de integracion: solo la API key (un JWT, aunque sea ADMIN, no sirve)
        mockMvc.perform(post("/api/laboratorio/integracion/resultados").contentType("application/json")
                .content(mapper.writeValueAsString(cuerpo))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/laboratorio/integracion/resultados").header("X-API-Key", "clave-equivocada")
                .contentType("application/json").content(mapper.writeValueAsString(cuerpo))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/laboratorio/integracion/resultados").header("Authorization", admin()).contentType("application/json")
                .content(mapper.writeValueAsString(cuerpo))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/laboratorio/integracion/resultados").header("Authorization", c.vet().bearer())
                .header("X-API-Key", API_KEY).contentType("application/json").content(mapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk()); // con la clave correcta pasa aunque haya un JWT
        // la API key no abre el resto de la API
        mockMvc.perform(get("/api/laboratorio/ordenes").header("X-API-Key", API_KEY)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pacientes/" + c.paciente() + "/historia").header("X-API-Key", API_KEY)).andExpect(status().isUnauthorized());
        // dryRun por integracion no escribe
        long otraOrden = orden(c.vet(), c.paciente(), "HEMO");
        muestra(c.aux(), otraOrden);
        Map<String, Object> sim = Map.of("ordenId", otraOrden, "resultados", List.of(Map.of("parametroCodigo", "HCT", "valor", "44")));
        mockMvc.perform(post("/api/laboratorio/integracion/resultados?dryRun=true").header("X-API-Key", API_KEY)
                .contentType("application/json").content(mapper.writeValueAsString(sim)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true)).andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.origen").value("INTEGRACION"));
        GET("/api/laboratorio/ordenes/" + otraOrden, c.vet().bearer()).andExpect(jsonPath("$.items[0].resultados", hasSize(0)));

        // el primer POST con clave ya importo (origen INTEGRACION, sin usuario)
        GET("/api/laboratorio/ordenes/" + c.orden(), c.vet().bearer())
                .andExpect(jsonPath("$.items[0].resultados[?(@.parametroCodigo=='HCT')].origen").value("INTEGRACION"))
                .andExpect(jsonPath("$.items[0].resultados[?(@.parametroCodigo=='HCT')].registradoPor", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.items[0].resultados[?(@.parametroCodigo=='WBC')].bandera").value("N"))
                .andExpect(jsonPath("$.eventos[?(@.tipo=='IMPORTACION')].origen").value("INTEGRACION"));

        // HL7 por integracion
        Caso otro = caso(true);
        integracionHl7(API_KEY, hl7(otro, otro.ordenCodigo()), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("INTEGRACION")).andExpect(jsonPath("$.importados").value(3));
        integracionHl7(null, hl7(otro, otro.ordenCodigo()), "").andExpect(status().isUnauthorized());
        integracionHl7("mala", hl7(otro, otro.ordenCodigo()), "").andExpect(status().isUnauthorized());
        // JSON via importar con contenido como texto
        importarJson(c.aux().bearer(), Map.of("formato", "JSON", "dryRun", true, "contenido",
                mapper.writeValueAsString(Map.of("ordenCodigo", c.ordenCodigo(), "resultados", List.of(Map.of("parametroCodigo", "HGB", "valor", "14"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lineas[0].parametroCodigo").value("HGB"));
        importarJson(c.aux().bearer(), Map.of("formato", "JSON", "resultados", List.of())).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pdfExternoComoAdjuntoDeLaOrden() throws Exception {
        Caso c = caso(true);
        long id = idDe(subir(c.aux().bearer(), c.paciente(), "PDF_LABORATORIO", "resultado-externo.pdf", pdfMinimo(),
                Map.of("ordenExamenId", String.valueOf(c.orden()), "ordenItemId", String.valueOf(c.item()),
                        "descripcion", "Resultado del laboratorio externo")).andExpect(status().isCreated()));
        GET("/api/laboratorio/ordenes/" + c.orden(), c.vet().bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].archivos", hasSize(1))).andExpect(jsonPath("$.items[0].archivos[0].id").value(id))
                .andExpect(jsonPath("$.items[0].archivos[0].tipo").value("PDF_LABORATORIO"));
        GET("/api/archivos?ordenExamenId=" + c.orden(), c.vet().bearer()).andExpect(jsonPath("$", hasSize(1)));
        // un item de otra orden no se puede vincular
        Caso otro = caso(true);
        subir(c.aux().bearer(), c.paciente(), "PDF_LABORATORIO", "r.pdf", pdfMinimo(),
                Map.of("ordenExamenId", String.valueOf(c.orden()), "ordenItemId", String.valueOf(otro.item()))).andExpect(status().isUnprocessableEntity());
        assertThat(textoPdf(mockMvc.perform(get("/api/laboratorio/ordenes/" + c.orden() + "/informe.pdf").header("Authorization", c.vet().bearer()))
                .andReturn().getResponse().getContentAsByteArray())).contains("PRELIMINAR");
    }
}
