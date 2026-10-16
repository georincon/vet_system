package com.veterinaria.comunicaciones.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador WHATSAPP contra Meta WhatsApp Cloud API: {@code POST {base}/{version}/{phoneId}/messages} con Bearer.
 * <ul>
 *   <li>Con {@code vet.whatsapp.template-name}: mensaje de plantilla aprobada (parametros del cuerpo segun
 *       {@code template-params}; opcionalmente el documento en la cabecera).</li>
 *   <li>Sin plantilla: un mensaje {@code document} por documento (por URL) o {@code text}; solo entrega dentro de la
 *       ventana de 24 h del tutor.</li>
 *   <li>Sin token/phone-id: modo simulado (ENVIADO_SIMULADO). Reintentos con backoff ante red/429/5xx.</li>
 * </ul>
 */
@Component
public class WhatsappCloudApiCanalEnvio implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(WhatsappCloudApiCanalEnvio.class);

    private final WhatsappHttpClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String token;
    private final String phoneId;
    private final String apiVersion;
    private final String baseUrl;
    private final String plantilla;
    private final String idioma;
    private final List<String> parametros;
    private final boolean cabeceraDocumento;
    private final String pais;
    private final int maxReintentos;
    private final long backoffMs;

    @Autowired
    public WhatsappCloudApiCanalEnvio(WhatsappHttpClient http,
            @Value("${vet.whatsapp.token:}") String token, @Value("${vet.whatsapp.phone-id:}") String phoneId,
            @Value("${vet.whatsapp.api-version:v21.0}") String apiVersion,
            @Value("${vet.whatsapp.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${vet.whatsapp.template-name:}") String plantilla,
            @Value("${vet.whatsapp.template-lang:es}") String idioma,
            @Value("${vet.whatsapp.template-params:tutor,paciente,enlace}") String parametros,
            @Value("${vet.whatsapp.template-header-document:false}") boolean cabeceraDocumento,
            @Value("${vet.whatsapp.pais-defecto:57}") String pais,
            @Value("${vet.whatsapp.max-reintentos:2}") int maxReintentos,
            @Value("${vet.whatsapp.backoff-ms:500}") long backoffMs) {
        this.http = http;
        this.token = token.trim();
        this.phoneId = phoneId.trim();
        this.apiVersion = apiVersion;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.plantilla = plantilla.trim();
        this.idioma = idioma;
        this.parametros = List.of(parametros.split("\\s*,\\s*"));
        this.cabeceraDocumento = cabeceraDocumento;
        this.pais = pais;
        this.maxReintentos = maxReintentos;
        this.backoffMs = backoffMs;
    }

    @Override
    public String canal() {
        return "WHATSAPP";
    }

    @Override
    public boolean admiteRecordatorios() {
        return false;
    }

    /** true si hay credenciales (si no, el modo es simulado). */
    public boolean configurado() {
        return !token.isEmpty() && !phoneId.isEmpty();
    }

    @Override
    public ResultadoEnvio enviar(Mensaje m) {
        String numero = Telefonos.aE164(m.destinatario(), pais);
        if (numero == null) {
            throw new EnvioFallidoException("Numero de WhatsApp invalido (se espera formato E.164)", null);
        }
        if (!configurado()) {
            log.info("[WHATSAPP simulado] para={} docs={}\n{}", Telefonos.enmascarar(numero),
                    m.documentos() == null ? 0 : m.documentos().size(), m.cuerpo());
            return new ResultadoEnvio(true, "WHATSAPP_SIMULADO", "sim-wa-" + UUID.randomUUID());
        }
        String to = numero.substring(1);
        List<Map<String, Object>> cargas = new ArrayList<>();
        List<DocumentoEnlace> docs = m.documentos() == null ? List.of() : m.documentos();
        if (!plantilla.isEmpty()) {
            cargas.add(cargaPlantilla(to, m, docs));
        } else if (docs.isEmpty()) {
            cargas.add(base(to, "text", Map.of("preview_url", true, "body", m.cuerpo())));
        } else {
            for (int i = 0; i < docs.size(); i++) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("link", docs.get(i).url());
                d.put("filename", docs.get(i).nombre());
                if (i == 0 && m.cuerpo() != null) {
                    d.put("caption", m.cuerpo());
                }
                cargas.add(base(to, "document", d));
            }
        }
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> c : cargas) {
            ids.add(enviarConReintentos(c));
        }
        return new ResultadoEnvio(false, "WHATSAPP_CLOUD_API", String.join(",", ids));
    }

    private Map<String, Object> base(String to, String tipo, Object contenido) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("messaging_product", "whatsapp");
        p.put("recipient_type", "individual");
        p.put("to", to);
        p.put("type", tipo);
        p.put(tipo, contenido);
        return p;
    }

    private Map<String, Object> cargaPlantilla(String to, Mensaje m, List<DocumentoEnlace> docs) {
        List<Map<String, Object>> componentes = new ArrayList<>();
        if (cabeceraDocumento && !docs.isEmpty()) {
            componentes.add(Map.of("type", "header", "parameters", List.of(Map.of("type", "document",
                    "document", Map.of("link", docs.get(0).url(), "filename", docs.get(0).nombre())))));
        }
        Map<String, String> vars = m.variables() == null ? Map.of() : m.variables();
        List<Map<String, Object>> ps = new ArrayList<>();
        for (String v : parametros) {
            if (!v.isBlank()) {
                ps.add(Map.of("type", "text", "text", vars.getOrDefault(v, "-")));
            }
        }
        if (!ps.isEmpty()) {
            componentes.add(Map.of("type", "body", "parameters", ps));
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", plantilla);
        t.put("language", Map.of("code", idioma));
        if (!componentes.isEmpty()) {
            t.put("components", componentes);
        }
        return base(to, "template", t);
    }

    private String enviarConReintentos(Map<String, Object> carga) {
        String url = baseUrl + "/" + apiVersion + "/" + phoneId + "/messages";
        String json = mapper.writeValueAsString(carga);
        String ultimoError = "sin respuesta";
        for (int intento = 0; intento <= maxReintentos; intento++) {
            if (intento > 0) {
                try {
                    Thread.sleep(backoffMs * (1L << (intento - 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new EnvioFallidoException("Interrumpido", e);
                }
            }
            try {
                WhatsappHttpClient.Respuesta r = http.post(url, token, json);
                if (r.status() >= 200 && r.status() < 300) {
                    JsonNode id = mapper.readTree(r.cuerpo()).path("messages").path(0).path("id");
                    return id.isMissingNode() ? "-" : id.asString();
                }
                ultimoError = "HTTP " + r.status() + ": " + mensajeError(r.cuerpo());
                if (r.status() != 429 && r.status() < 500) {
                    throw new EnvioFallidoException("WhatsApp " + ultimoError, null);
                }
            } catch (IOException e) {
                ultimoError = "red: " + e.getMessage();
            }
        }
        throw new EnvioFallidoException("WhatsApp " + ultimoError + " (tras " + (maxReintentos + 1) + " intentos)", null);
    }

    private String mensajeError(String cuerpo) {
        try {
            JsonNode m = mapper.readTree(cuerpo).path("error").path("message");
            return m.isMissingNode() ? "(sin detalle)" : m.asString();
        } catch (RuntimeException e) {
            return "(respuesta no JSON)";
        }
    }
}
