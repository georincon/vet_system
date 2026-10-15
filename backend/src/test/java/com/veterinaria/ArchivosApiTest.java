package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

class ArchivosApiTest extends EtapaATestSupport {

    @Test
    void subidaGuardaConNombreAleatorioHashYMiniatura() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Perro", "M", null);
        byte[] contenido = png(800, 600);
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));

        String res = json(subir(vet.bearer(), paciente, "RADIOGRAFIA", "torax lateral.PNG", contenido,
                Map.of("descripcion", "Torax lateral derecho")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RADIOGRAFIA"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.hashSha256").value(esperado))
                .andExpect(jsonPath("$.tamanoBytes").value(contenido.length))
                .andExpect(jsonPath("$.tieneMiniatura").value(true))
                .andExpect(jsonPath("$.anchoPx").value(800)).andExpect(jsonPath("$.altoPx").value(600))
                .andExpect(jsonPath("$.nombreOriginal").value("torax lateral.PNG")));
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();

        // ni la ruta ni el nombre original aparecen: en disco solo hay UUID.png y su miniatura
        assertThat(res).doesNotContain("ruta").doesNotContain("almacenamiento");
        try (Stream<Path> archivos = Files.walk(directorioArchivos())) {
            assertThat(archivos.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .anyMatch(n -> n.matches("[0-9a-f-]{36}\\.png"))
                    .anyMatch(n -> n.matches("[0-9a-f-]{36}_thumb\\.jpg"))
                    .noneMatch(n -> n.contains("torax"));
        }

        byte[] bajado = mockMvc.perform(get("/api/archivos/" + id + "/contenido").header("Authorization", vet.bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bajado).isEqualTo(contenido);
        byte[] mini = mockMvc.perform(get("/api/archivos/" + id + "/miniatura").header("Authorization", vet.bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(mini).hasSizeLessThan(contenido.length);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(mini)).getWidth()).isEqualTo(400);

        GET("/api/pacientes/" + paciente + "/archivos", vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(id));
        GET("/api/pacientes/" + paciente + "/archivos?tipo=ECOGRAFIA", vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tipoFalsoSeRechazaPorMagicBytes() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Perro", "F", null);
        // un ejecutable/texto binario renombrado como .jpg y como .pdf
        byte[] falso = "MZ\u0090\u0000\u0003\u0000\u0000\u0000 contenido ejecutable".getBytes(StandardCharsets.ISO_8859_1);
        subir(vet.bearer(), paciente, "IMAGEN", "foto.jpg", falso, Map.of()).andExpect(status().isUnsupportedMediaType());
        subir(vet.bearer(), paciente, "INFORME", "informe.pdf", falso, Map.of()).andExpect(status().isUnsupportedMediaType());
        // un PNG real con extension .pdf: el contenido no coincide con la extension
        subir(vet.bearer(), paciente, "INFORME", "imagen.pdf", png(20, 20), Map.of()).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(containsString("no coincide")));
        // extension no permitida aunque el contenido sea valido
        subir(vet.bearer(), paciente, "OTRO", "script.exe", pdfMinimo(), Map.of()).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(containsString("Extension no permitida")));
        // coherencia con el tipo clinico: un jpg no es un PDF_LABORATORIO
        subir(vet.bearer(), paciente, "PDF_LABORATORIO", "resultado.jpg", jpg(30, 30), Map.of()).andExpect(status().isUnprocessableEntity());
        // archivo vacio
        subir(vet.bearer(), paciente, "OTRO", "vacio.txt", new byte[0], Map.of()).andExpect(status().isUnprocessableEntity());
        // los validos si pasan: pdf, jpg, txt, dcm
        subir(vet.bearer(), paciente, "PDF_LABORATORIO", "res.pdf", pdfMinimo(), Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf")).andExpect(jsonPath("$.tieneMiniatura").value(false));
        subir(vet.bearer(), paciente, "ECOGRAFIA", "eco.jpeg", jpg(50, 40), Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
        subir(vet.bearer(), paciente, "OTRO", "nota.txt", "Observaciones del caso: tilde y enie".getBytes(StandardCharsets.UTF_8), Map.of())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("text/plain"));
        subir(vet.bearer(), paciente, "DICOM", "estudio.dcm", dicom(2000), Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/dicom"));
    }

    @Test
    void tamanoMaximoConfigurableYDicomConLimiteMayor() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Gato", "M", null);
        // limite de prueba: 1 MB general, 4 MB para DICOM
        byte[] grandeTxt = new byte[1_048_576 + 10];
        java.util.Arrays.fill(grandeTxt, (byte) 'a');
        subir(vet.bearer(), paciente, "OTRO", "grande.txt", grandeTxt, Map.of()).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(containsString("tamano maximo")));
        subir(vet.bearer(), paciente, "DICOM", "mediano.dcm", dicom(2_000_000), Map.of()).andExpect(status().isCreated());
        subir(vet.bearer(), paciente, "DICOM", "enorme.dcm", dicom(4_300_000), Map.of()).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void nombreConPathTraversalNoEscapaDelDirectorio() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Perro", "M", null);
        String res = json(subir(vet.bearer(), paciente, "INFORME", "../../../../tmp/evil.pdf", pdfMinimo(), Map.of())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombreOriginal").value("evil.pdf")));
        subir(vet.bearer(), paciente, "INFORME", "..\\..\\windows\\system32\\evil2.pdf", pdfMinimo(), Map.of())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombreOriginal").value("evil2.pdf"));
        assertThat(Path.of("/tmp/evil.pdf")).doesNotExist();
        assertThat(Path.of("/tmp/tmp/evil.pdf")).doesNotExist();
        try (Stream<Path> archivos = Files.walk(directorioArchivos())) {
            assertThat(archivos.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .noneMatch(n -> n.contains("evil"));
        }
        assertThat(res).doesNotContain("..");
    }

    @Test
    void vinculosDebenPertenecerAlPacienteYBajaLogicaConMotivo() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        long paciente = pacienteDe("Perro", "F", null);
        long otro = pacienteDe("Perro", "M", null);
        long orden = orden(vet, otro, "HEMO");
        // la orden es de otro paciente
        subir(vet.bearer(), paciente, "PDF_LABORATORIO", "r.pdf", pdfMinimo(), Map.of("ordenExamenId", String.valueOf(orden)))
                .andExpect(status().isUnprocessableEntity());
        subir(vet.bearer(), paciente, "OTRO", "r.txt", "x".getBytes(), Map.of("consultaId", "99999999"))
                .andExpect(status().isUnprocessableEntity());
        // vinculado correctamente
        long ok = idDe(subir(vet.bearer(), otro, "PDF_LABORATORIO", "r.pdf", pdfMinimo(), Map.of("ordenExamenId", String.valueOf(orden)))
                .andExpect(status().isCreated()));
        GET("/api/archivos?ordenExamenId=" + orden, vet.bearer()).andExpect(jsonPath("$", hasSize(1)));
        GET("/api/archivos", vet.bearer()).andExpect(status().isUnprocessableEntity());

        // baja logica: motivo obligatorio; el archivo se conserva pero no se sirve ni se lista
        DELETE("/api/archivos/" + ok, vet.bearer()).andExpect(status().isBadRequest());
        DELETE("/api/archivos/" + ok + "?motivo=abc", vet.bearer()).andExpect(status().isUnprocessableEntity());
        DELETE("/api/archivos/" + ok + "?motivo=Archivo cargado por error", vet.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false)).andExpect(jsonPath("$.motivoEliminacion").value("Archivo cargado por error"));
        DELETE("/api/archivos/" + ok + "?motivo=Archivo cargado por error", vet.bearer()).andExpect(status().isConflict());
        GET("/api/archivos/" + ok + "/contenido", vet.bearer()).andExpect(status().isNotFound());
        GET("/api/pacientes/" + otro + "/archivos", vet.bearer()).andExpect(jsonPath("$", hasSize(0)));
        GET("/api/pacientes/" + otro + "/archivos?incluirEliminados=true", vet.bearer()).andExpect(jsonPath("$", hasSize(1)));
        // sin DELETE fisico en la base
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("delete from adjunto where id = :id").param("id", ok).update())
                .hasMessageContaining("no admite DELETE");
    }

    @Test
    void rolesYAutenticacion() throws Exception {
        Usuario vet = usuario("VETERINARIO");
        Usuario aux = usuario("AUXILIAR");
        Usuario recepcion = usuario("RECEPCION");
        long paciente = pacienteDe("Perro", "F", null);

        subir(recepcion.bearer(), paciente, "OTRO", "a.txt", "hola".getBytes(), Map.of()).andExpect(status().isForbidden());
        GET("/api/pacientes/" + paciente + "/archivos", recepcion.bearer()).andExpect(status().isForbidden());
        long id = idDe(subir(aux.bearer(), paciente, "OTRO", "a.txt", "hola".getBytes(), Map.of()).andExpect(status().isCreated()));
        GET("/api/archivos/" + id + "/contenido", recepcion.bearer()).andExpect(status().isForbidden());
        GET("/api/archivos/" + id + "/contenido", aux.bearer()).andExpect(status().isOk());
        mockMvc.perform(get("/api/archivos/" + id + "/contenido")).andExpect(status().isUnauthorized());
        // el auxiliar sube y lee pero no elimina
        DELETE("/api/archivos/" + id + "?motivo=Duplicado de otro archivo", aux.bearer()).andExpect(status().isForbidden());
        DELETE("/api/archivos/" + id + "?motivo=Duplicado de otro archivo", vet.bearer()).andExpect(status().isOk());
        // tipo desconocido -> 400
        subir(vet.bearer(), paciente, "VIDEO", "a.txt", "hola".getBytes(), Map.of()).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/archivos/" + id).header("Authorization", vet.bearer()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
