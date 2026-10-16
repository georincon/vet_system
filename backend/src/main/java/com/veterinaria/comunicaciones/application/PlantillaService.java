package com.veterinaria.comunicaciones.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.comunicaciones.api.dto.PlantillaRequest;
import com.veterinaria.comunicaciones.api.dto.PlantillaResponse;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;

/** CRUD de plantillas de mensaje y renderizado de variables {{nombre}}. */
@Service
public class PlantillaService {

    /** Variables que el motor sabe resolver. */
    public static final Set<String> VARIABLES = Set.of("tutor", "paciente", "vacuna", "fecha", "hora", "clinica", "sede",
            "documento", "enlace");

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final JdbcClient jdbc;

    public PlantillaService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PlantillaResponse> listar(String canal, Boolean activo) {
        return jdbc.sql("""
                select id, codigo, canal, idioma, asunto, cuerpo, activo from plantilla_mensaje
                 where (cast(:canal as text) is null or canal = cast(:canal as text))
                   and (cast(:activo as boolean) is null or activo = cast(:activo as boolean))
                 order by codigo, canal
                """).param("canal", canal).param("activo", activo).query(this::mapear).list();
    }

    @Transactional(readOnly = true)
    public PlantillaResponse obtener(Long id) {
        return jdbc.sql("select id, codigo, canal, idioma, asunto, cuerpo, activo from plantilla_mensaje where id = :id")
                .param("id", id).query(this::mapear).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla", id));
    }

    @Transactional
    public PlantillaResponse crear(PlantillaRequest r) {
        String idioma = r.idioma() == null ? "es" : r.idioma();
        String cuerpo = r.cuerpo().trim();
        String asunto = Textos.limpiar(r.asunto());
        validar(asunto, cuerpo);
        if (existe(r.codigo().trim(), r.canal(), idioma, null)) {
            throw new ConflictException("Ya existe la plantilla %s/%s/%s".formatted(r.codigo().trim(), r.canal(), idioma));
        }
        Long id = jdbc.sql("""
                insert into plantilla_mensaje (codigo, canal, idioma, asunto, cuerpo, variables, activo)
                values (:codigo, :canal, :idioma, :asunto, :cuerpo, cast(:vars as jsonb), :activo) returning id
                """).param("codigo", r.codigo().trim()).param("canal", r.canal()).param("idioma", idioma)
                .param("asunto", asunto).param("cuerpo", cuerpo).param("vars", jsonVariables(asunto, cuerpo))
                .param("activo", r.activo() == null || r.activo()).query(Long.class).single();
        return obtener(id);
    }

    @Transactional
    public PlantillaResponse actualizar(Long id, PlantillaRequest r) {
        PlantillaResponse actual = obtener(id);
        String codigo = r.codigo() == null ? actual.codigo() : r.codigo().trim();
        String canal = r.canal() == null ? actual.canal() : r.canal();
        String idioma = r.idioma() == null ? actual.idioma() : r.idioma();
        String asunto = Textos.actualizar(actual.asunto(), r.asunto());
        String cuerpo = r.cuerpo() == null ? actual.cuerpo() : r.cuerpo().trim();
        if (cuerpo.isEmpty()) {
            throw new BusinessException("El cuerpo no puede quedar vacio");
        }
        validar(asunto, cuerpo);
        if (existe(codigo, canal, idioma, id)) {
            throw new ConflictException("Ya existe la plantilla %s/%s/%s".formatted(codigo, canal, idioma));
        }
        jdbc.sql("""
                update plantilla_mensaje set codigo = :codigo, canal = :canal, idioma = :idioma, asunto = :asunto,
                       cuerpo = :cuerpo, variables = cast(:vars as jsonb), activo = :activo where id = :id
                """).param("codigo", codigo).param("canal", canal).param("idioma", idioma).param("asunto", asunto)
                .param("cuerpo", cuerpo).param("vars", jsonVariables(asunto, cuerpo))
                .param("activo", r.activo() == null ? actual.activo() : r.activo()).param("id", id).update();
        return obtener(id);
    }

    /** Sustituye {{variable}} por su valor (vacio si no hay dato). */
    public static String renderizar(String texto, Map<String, String> valores) {
        if (texto == null) {
            return null;
        }
        Matcher m = VARIABLE.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(valores.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private boolean existe(String codigo, String canal, String idioma, Long excluir) {
        return jdbc.sql("""
                select count(*) from plantilla_mensaje where codigo = :c and canal = :k and idioma = :i
                   and (cast(:excluir as bigint) is null or id <> cast(:excluir as bigint))
                """).param("c", codigo).param("k", canal).param("i", idioma).param("excluir", excluir)
                .query(Long.class).single() > 0;
    }

    private static Set<String> variablesDe(String asunto, String cuerpo) {
        Set<String> vars = new LinkedHashSet<>();
        for (String t : new String[] {asunto, cuerpo}) {
            if (t != null) {
                Matcher m = VARIABLE.matcher(t);
                while (m.find()) {
                    vars.add(m.group(1));
                }
            }
        }
        return vars;
    }

    private static void validar(String asunto, String cuerpo) {
        Set<String> desconocidas = variablesDe(asunto, cuerpo).stream().filter(v -> !VARIABLES.contains(v))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!desconocidas.isEmpty()) {
            throw new BusinessException("Variables no soportadas: " + desconocidas + ". Permitidas: "
                    + new java.util.TreeSet<>(VARIABLES));
        }
    }

    private static String jsonVariables(String asunto, String cuerpo) {
        return variablesDe(asunto, cuerpo).stream().map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private PlantillaResponse mapear(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String asunto = rs.getString("asunto");
        String cuerpo = rs.getString("cuerpo");
        return new PlantillaResponse(rs.getLong("id"), rs.getString("codigo"), rs.getString("canal"),
                rs.getString("idioma"), asunto, cuerpo, List.copyOf(variablesDe(asunto, cuerpo)),
                rs.getBoolean("activo"));
    }
}
