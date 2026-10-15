package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ImagenPatologiaApiTest extends EtapaATestSupport {

    private static Map<String, Object> informeEco() {
        return Map.of("region", "Abdomen craneal", "tecnica", "Ecografia abdominal con transductor lineal de 10 MHz",
                "hallazgos", "Hígado de tamano y ecogenicidad normales. Vejiga con contenido anecoico y pared delgada.",
                "impresionDiagnostica", "Sin alteraciones ecograficas significativas",
                "recomendaciones", "Control clinico en 6 meses", "equipo", "Ecografo Mindray DP-10",
                "fechaEstudio", LocalDate.now().toString());
    }

    @Test
    void ecografiaConInformeEstructuradoImagenesYPdf() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "F", LocalDate.now().minusYears(5));
        long orden = orden(vet, paciente, "ECO-ABD");
        long item = itemId(orden, "ECO-ABD");
        String base = "/api/laboratorio/ordenes/" + orden + "/items/" + item;

        // solo ADMIN/VETERINARIO informan; la imagen no necesita toma de muestra
        PUT(base + "/informe-imagen", aux.bearer(), informeEco()).andExpect(status().isForbidden());
        PUT(base + "/informe-imagen", vet.bearer(), Map.of("tecnica", "sin region")).andExpect(status().isBadRequest());
        // un examen de imagen no recibe parametros de laboratorio ni informe de patologia
        capturar(aux.bearer(), orden, item, "X", 1).andExpect(status().isUnprocessableEntity());
        PUT(base + "/informe-patologia", vet.bearer(), Map.of("tipoMuestra", "Tejido")).andExpect(status().isUnprocessableEntity());

        // el auxiliar sube las imagenes del estudio asociadas al item
        long imagen = idDe(subir(aux.bearer(), paciente, "ECOGRAFIA", "hígado.jpg", jpg(640, 480),
                Map.of("ordenExamenId", String.valueOf(orden), "ordenItemId", String.valueOf(item), "descripcion", "Corte longitudinal de higado"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tieneMiniatura").value(true)));
        subir(aux.bearer(), paciente, "ECOGRAFIA", "vejiga.png", png(500, 500),
                Map.of("ordenExamenId", String.valueOf(orden), "ordenItemId", String.valueOf(item), "descripcion", "Vejiga")).andExpect(status().isCreated());
        subir(aux.bearer(), paciente, "DICOM", "estudio.dcm", dicom(500),
                Map.of("ordenExamenId", String.valueOf(orden), "ordenItemId", String.valueOf(item))).andExpect(status().isCreated());

        // borrador: se puede reescribir; la orden pasa a EN_PROCESO
        PUT(base + "/informe-imagen", vet.bearer(), Map.of("region", "Abdomen", "hallazgos", "Borrador inicial"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESULTADO"))
                .andExpect(jsonPath("$.informeImagen.estado").value("BORRADOR")).andExpect(jsonPath("$.archivos", hasSize(3)));
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(jsonPath("$.estado").value("EN_PROCESO"));
        // no se valida sin hallazgos e impresion
        validar(vet.bearer(), orden, item, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("impresion diagnostica")));
        PUT(base + "/informe-imagen", vet.bearer(), informeEco()).andExpect(status().isOk())
                .andExpect(jsonPath("$.informeImagen.region").value("Abdomen craneal"))
                .andExpect(jsonPath("$.informeImagen.informadoPor.id").value(vet.id()))
                .andExpect(jsonPath("$.informeImagen.equipo").value("Ecografo Mindray DP-10"));
        assertThat(jdbc.sql("select count(*) from informe_imagen where orden_item_id = :i").param("i", item).query(Long.class).single()).isEqualTo(1L);

        // el informe aun no validado sale como preliminar en el PDF; validado, firmado
        validar(vet.bearer(), orden, item, null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.interpretacion").value("Sin alteraciones ecograficas significativas"))
                .andExpect(jsonPath("$.informeImagen.estado").value("VALIDADO"));
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(jsonPath("$.estado").value("COMPLETADA"));
        PUT(base + "/informe-imagen", vet.bearer(), informeEco()).andExpect(status().isConflict());

        byte[] pdf = mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf").header("Authorization", aux.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(textoPdf(pdf)).contains("Informe de imagenologia").contains("Ecografia abdominal").contains("Abdomen craneal")
                .contains("Hallazgos").contains("Impresion diagnostica").contains("Sin alteraciones ecograficas significativas")
                .contains("Recomendaciones").contains("Ecografo Mindray").contains("Imagenes del estudio")
                .contains("Corte longitudinal de higado").contains("estudio.dcm").contains("VALIDADO por");
        // las miniaturas JPG/PNG van incrustadas como imagenes en el PDF
        String crudo = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(crudo.split("/Subtype\\s*/Image", -1).length - 1).isEqualTo(2);

        // correccion enlazada del informe
        POST(base + "/correccion", vet.bearer(), Map.of("motivo", "Se omitio un hallazgo en el bazo", "informeImagen",
                Map.of("region", "Abdomen craneal", "hallazgos", "Higado normal. Bazo con nodulo hipoecoico de 8 mm.",
                        "impresionDiagnostica", "Nodulo esplenico: se recomienda citologia"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.informeImagen.version").value(2))
                .andExpect(jsonPath("$.informeImagen.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.informeImagen.motivoCorreccion").value("Se omitio un hallazgo en el bazo"))
                .andExpect(jsonPath("$.interpretacion").value("Sin alteraciones ecograficas significativas"));
        assertThat(jdbc.sql("select count(*) from informe_imagen where orden_item_id = :i").param("i", item).query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("select estado from informe_imagen where orden_item_id = :i order by version").param("i", item).query(String.class).list())
                .containsExactly("CORREGIDO", "VALIDADO");
        assertThat(jdbc.sql("select reemplazado_por from informe_imagen where orden_item_id = :i and version = 1").param("i", item).query(Long.class).single()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("update informe_imagen set hallazgos = 'x' where orden_item_id = :i").param("i", item).update())
                .hasMessageContaining("inmutable");

        // historia: el informe validado y los archivos asociados se ven dentro del evento IMAGEN (no como ARCHIVO suelto)
        subir(vet.bearer(), paciente, "INFORME", "informe-remision.pdf", pdfMinimo(), Map.of("descripcion", "Informe de remision externa")).andExpect(status().isCreated());
        GET("/api/pacientes/" + paciente + "/historia?tipo=IMAGEN", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].tipo").value("IMAGEN")).andExpect(jsonPath("$.items[0].referenciaId").value(orden))
                .andExpect(jsonPath("$.items[0].titulo").value("Ecografia abdominal - Abdomen craneal"));
        GET("/api/pacientes/" + paciente + "/historia?tipo=ARCHIVO", vet.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].titulo").value("Informe de remision externa"));
        GET("/api/archivos/" + imagen + "/contenido", vet.bearer()).andExpect(status().isOk());
    }

    @Test
    void radiografiaYElectrocardiogramaUsanElMismoInformeEstructurado() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Gato", "M", LocalDate.now().minusYears(8));
        long orden = orden(vet, paciente, "RX", "ECG");
        for (String examen : List.of("RX", "ECG")) {
            long item = itemId(orden, examen);
            PUT("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/informe-imagen", vet.bearer(),
                    Map.of("region", examen.equals("RX") ? "Torax latero-lateral derecho" : "Derivacion II", "hallazgos", "Hallazgos de " + examen,
                            "impresionDiagnostica", "Impresion de " + examen)).andExpect(status().isOk());
            validar(vet.bearer(), orden, item, "Interpretacion " + examen).andExpect(status().isOk());
        }
        GET("/api/laboratorio/ordenes/" + orden, vet.bearer()).andExpect(jsonPath("$.estado").value("COMPLETADA"));
        GET("/api/pacientes/" + paciente + "/historia?tipo=IMAGEN", vet.bearer()).andExpect(jsonPath("$.totalElements").value(2));
        // informar por otro veterinario: debe ser un veterinario/administrador activo
        long orden2 = orden(vet, paciente, "ECO-CARD");
        String url = "/api/laboratorio/ordenes/" + orden2 + "/items/" + itemId(orden2, "ECO-CARD") + "/informe-imagen";
        PUT(url, vet.bearer(), Map.of("region", "Corazon", "informadoPorId", usuario("AUXILIAR").id())).andExpect(status().isUnprocessableEntity());
        PUT(url, admin(), Map.of("region", "Corazon", "informadoPorId", vet.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.informeImagen.informadoPor.id").value(vet.id()));
    }

    @Test
    void citologiaConInformeDePatologiaMuestraYPdf() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        long paciente = pacienteDe("Perro", "F", LocalDate.now().minusYears(9));
        long orden = orden(vet, paciente, "CITO");
        long item = itemId(orden, "CITO");
        String base = "/api/laboratorio/ordenes/" + orden + "/items/" + item;
        Map<String, Object> informe = mapa("tipoMuestra", "Aspirado con aguja fina", "sitioAnatomico", "Glandula mamaria caudal izquierda",
                "fechaToma", LocalDate.now().toString(), "descripcionMacroscopica", "Liquido turbio, 0.5 ml",
                "descripcionMicroscopica", "Celularidad moderada de celulas epiteliales con criterios de malignidad leves",
                "diagnosticoMorfologico", "Proliferacion epitelial: sospecha de neoplasia mamaria", "margenes", "No aplica (citologia)",
                "comentarios", "Se sugiere biopsia excisional con histopatologia", "laboratorioExterno", "Laboratorio Patovet",
                "numeroCasoExterno", "PV-2026-1234", "patologo", "Dra. Rojas", "fechaInforme", LocalDate.now().toString());
        // patologia exige toma de muestra
        PUT(base + "/informe-patologia", vet.bearer(), informe).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("toma de muestra")));
        muestra(aux, orden);
        PUT(base + "/informe-patologia", aux.bearer(), informe).andExpect(status().isForbidden());
        PUT(base + "/informe-patologia", vet.bearer(), Map.of("tipoMuestra", "Aspirado", "comentarios", "pendiente")).andExpect(status().isOk());
        validar(vet.bearer(), orden, item, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("diagnostico morfologico")));
        PUT(base + "/informe-patologia", vet.bearer(), informe).andExpect(status().isOk())
                .andExpect(jsonPath("$.informePatologia.tipoMuestra").value("Aspirado con aguja fina"))
                .andExpect(jsonPath("$.informePatologia.laboratorioExterno").value("Laboratorio Patovet"));
        long pdfExterno = idDe(subir(aux.bearer(), paciente, "PDF_LABORATORIO", "patologia-externa.pdf", pdfMinimo(),
                Map.of("ordenExamenId", String.valueOf(orden), "ordenItemId", String.valueOf(item))).andExpect(status().isCreated()));
        subir(aux.bearer(), paciente, "IMAGEN", "microscopia.png", png(300, 200),
                Map.of("ordenExamenId", String.valueOf(orden), "ordenItemId", String.valueOf(item), "descripcion", "Campo 40x")).andExpect(status().isCreated());
        validar(vet.bearer(), orden, item, "Sospecha de neoplasia mamaria; realizar histopatologia").andExpect(status().isOk())
                .andExpect(jsonPath("$.informePatologia.estado").value("VALIDADO"));

        byte[] pdf = mockMvc.perform(get("/api/laboratorio/ordenes/" + orden + "/informe.pdf").header("Authorization", vet.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(textoPdf(pdf)).contains("Informe de patologia").contains("Citologia").contains("Aspirado con aguja fina")
                .contains("Glandula mamaria caudal izquierda").contains("Descripcion macroscopica").contains("Descripcion microscopica")
                .contains("Diagnostico morfologico").contains("sospecha de neoplasia mamaria").contains("Laboratorio Patovet")
                .contains("PV-2026-1234").contains("Campo 40x").contains("patologia-externa.pdf").contains("Interpretacion del veterinario");
        GET("/api/pacientes/" + paciente + "/historia?tipo=PATOLOGIA", aux.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].detalle").value(containsString("neoplasia")));
        GET("/api/archivos/" + pdfExterno + "/contenido", aux.bearer()).andExpect(status().isOk());
        // correccion enlazada del informe de patologia
        POST(base + "/correccion", vet.bearer(), Map.of("motivo", "Informe definitivo del laboratorio externo", "informePatologia",
                Map.of("tipoMuestra", "Aspirado con aguja fina", "diagnosticoMorfologico", "Adenoma mamario benigno")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.informePatologia.version").value(2))
                .andExpect(jsonPath("$.informePatologia.diagnosticoMorfologico").value("Adenoma mamario benigno"));
        GET("/api/pacientes/" + paciente + "/historia?tipo=PATOLOGIA", aux.bearer()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].detalle").value(containsString("Adenoma")));
    }
}
