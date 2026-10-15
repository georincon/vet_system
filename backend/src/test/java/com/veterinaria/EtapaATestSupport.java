package com.veterinaria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Utilidades de la Etapa A: directorio temporal de archivos, API key de prueba, pacientes por especie, ordenes y archivos. */
abstract class EtapaATestSupport extends ApiTestSupport {

    static final String API_KEY = "clave-lis-de-prueba";
    @org.springframework.beans.factory.annotation.Autowired
    protected org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private static final Path DIRECTORIO;
    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    static {
        try {
            DIRECTORIO = Files.createTempDirectory("vet-archivos-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("vet.archivos.ruta", () -> DIRECTORIO.toString());
        registry.add("vet.archivos.max-bytes", () -> "1048576");
        registry.add("vet.archivos.max-bytes-dicom", () -> "4194304");
        registry.add("vet.laboratorio.api-key", () -> API_KEY);
    }

    protected static Path directorioArchivos() {
        return DIRECTORIO;
    }

    // ------------------------------------------------------------------ pacientes

    protected long especieId(String nombre) throws Exception {
        return primerId(json(GET("/api/catalogos/especies", admin())), "$[?(@.nombre=='" + nombre + "')].id");
    }

    /** Paciente de la especie, sexo (M/F) y fecha de nacimiento (opcional) dados. */
    protected long pacienteDe(String especie, String sexo, LocalDate nacimiento) throws Exception {
        String admin = admin();
        int n = SEQ.incrementAndGet();
        long tutor = idDe(POST("/api/tutores", admin, Map.of("tipoDocumento", "CC", "numeroDocumento", "88" + n,
                "nombres", "Tutor" + n, "apellidos", "EtapaA", "email", "tutor" + n + "@correo.test", "telefono", "310" + n))
                .andExpect(status().isCreated()));
        Map<String, Object> body = new HashMap<>(Map.of("nombre", "Etapa" + n, "especieId", especieId(especie), "sexo", sexo,
                "tutorIds", List.of(tutor), "tutorPrincipalId", tutor));
        if (nacimiento != null) {
            body.put("fechaNacimiento", nacimiento.toString());
        }
        return idDe(POST("/api/pacientes", admin, body).andExpect(status().isCreated()));
    }

    // ------------------------------------------------------------------ laboratorio

    protected long examenId(String codigo) throws Exception {
        return primerId(json(GET("/api/laboratorio/examenes?q=" + codigo + "&soloActivos=false", admin())),
                "$[?(@.codigo=='" + codigo + "')].id");
    }

    protected long parametroId(String examenCodigo, String parametroCodigo) throws Exception {
        String ex = json(GET("/api/laboratorio/examenes/" + examenId(examenCodigo), admin()));
        return primerId(ex, "$.parametros[?(@.codigo=='" + parametroCodigo + "')].id");
    }

    /** Crea la orden (como veterinario) con los examenes por codigo y devuelve su id. */
    protected long ordenCon(Usuario vet, long paciente, String prioridad, Long consultaId, String... examenes) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pacienteId", paciente);
        body.put("prioridad", prioridad);
        body.put("examenCodigos", List.of(examenes));
        body.put("indicacionClinica", "Control clinico de prueba");
        if (consultaId != null) {
            body.put("consultaId", consultaId);
        }
        return idDe(POST("/api/laboratorio/ordenes", vet.bearer(), body).andExpect(status().isCreated()));
    }

    protected long orden(Usuario vet, long paciente, String... examenes) throws Exception {
        return ordenCon(vet, paciente, "RUTINA", null, examenes);
    }

    protected void muestra(Usuario quien, long orden) throws Exception {
        POST("/api/laboratorio/ordenes/" + orden + "/muestra", quien.bearer(), Map.of("observaciones", "Tubo EDTA"))
                .andExpect(status().isOk());
    }

    protected long itemId(long orden, String examenCodigo) throws Exception {
        String o = json(GET("/api/laboratorio/ordenes/" + orden, admin()));
        return primerId(o, "$.items[?(@.examenCodigo=='" + examenCodigo + "')].id");
    }

    protected ResultActions capturar(String bearer, long orden, long item, Object... parametroValor) throws Exception {
        List<Map<String, Object>> resultados = new ArrayList<>();
        for (int i = 0; i < parametroValor.length; i += 2) {
            Map<String, Object> r = new HashMap<>();
            r.put("parametroCodigo", parametroValor[i]);
            if (parametroValor[i + 1] instanceof Number n) {
                r.put("valorNumerico", n);
            } else {
                r.put("valorTexto", parametroValor[i + 1]);
            }
            resultados.add(r);
        }
        return PUT("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/resultados", bearer, Map.of("resultados", resultados));
    }

    protected ResultActions validar(String bearer, long orden, long item, String interpretacion) throws Exception {
        return POST("/api/laboratorio/ordenes/" + orden + "/items/" + item + "/validar", bearer,
                interpretacion == null ? Map.of() : Map.of("interpretacion", interpretacion));
    }

    // ------------------------------------------------------------------ archivos

    protected ResultActions subir(String bearer, long paciente, String tipo, String nombre, byte[] contenido,
            Map<String, String> extra) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/archivos")
                .file(new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido))
                .param("pacienteId", String.valueOf(paciente)).param("tipo", tipo);
        extra.forEach(req::param);
        return mockMvc.perform(req.header("Authorization", bearer));
    }

    protected static byte[] png(int ancho, int alto) throws IOException {
        return imagen("png", ancho, alto);
    }

    protected static byte[] jpg(int ancho, int alto) throws IOException {
        return imagen("jpg", ancho, alto);
    }

    private static byte[] imagen(String formato, int ancho, int alto) throws IOException {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < ancho; x++) {
            for (int y = 0; y < alto; y++) {
                img.setRGB(x, y, ((x * 3) << 16) | ((y * 2) << 8) | ((x + y) & 0xFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return out.toByteArray();
    }

    protected static byte[] pdfMinimo() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    protected static byte[] dicom(int extra) {
        byte[] b = new byte[132 + extra];
        b[128] = 'D';
        b[129] = 'I';
        b[130] = 'C';
        b[131] = 'M';
        return b;
    }

    /** Texto plano de todas las paginas de un PDF. */
    protected static String textoPdf(byte[] pdf) throws IOException {
        var reader = new com.lowagie.text.pdf.PdfReader(pdf);
        var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append('\n');
        }
        return sb.toString();
    }

    /** Mapa a partir de pares clave, valor (sin el limite de 10 pares de Map.of). */
    protected static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected static String leer(String json, String path) {
        Object v = JsonPath.read(json, path);
        return String.valueOf(v instanceof List<?> l ? (l.isEmpty() ? null : l.get(0)) : v);
    }
}
