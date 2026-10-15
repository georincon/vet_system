package com.veterinaria.laboratorio.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ParametroRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ParametroResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.RangoRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.RangoResponse;
import com.veterinaria.laboratorio.domain.Banderas.Rango;

/** SQL del catalogo: examenes (paneles), parametros y rangos de referencia. */
@Repository
public class CatalogoRepository {

    /** Parametro del catalogo con lo necesario para validar y evaluar un resultado. */
    public record ParametroInfo(Long id, Long examenId, String examenCodigo, String codigo, String nombre, String unidad,
            String unidadUcum, String tipoValor, String codigoLoinc, String valorNormalTexto, String opciones,
            boolean activo) {
    }

    private static final RowMapper<ExamenResponse> EXAMEN = (rs, n) -> new ExamenResponse(rs.getLong("id"),
            rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"), rs.getString("categoria"),
            rs.getString("descripcion"), rs.getString("muestra"), rs.getBoolean("requiere_ayuno"),
            (Integer) rs.getObject("tiempo_entrega_horas"), rs.getBoolean("es_externo"), rs.getBoolean("activo"),
            (Long) rs.getObject("item_id"), rs.getString("codigo_loinc"), rs.getString("loinc_estado"),
            rs.getInt("np") > 1, new ArrayList<>());

    private static final String EXAMEN_SQL = """
            select e.id, e.codigo, e.nombre, e.tipo, e.categoria, e.descripcion, e.muestra, e.requiere_ayuno,
                   e.tiempo_entrega_horas, e.es_externo, e.activo, e.item_id, e.codigo_loinc, e.loinc_estado,
                   (select count(*) from examen_parametro p where p.examen_id = e.id and p.activo) as np
              from examen_catalogo e
            """;

    private final JdbcClient jdbc;

    public CatalogoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ lectura

    public List<ExamenResponse> listar(String tipo, String q, boolean soloActivos) {
        return jdbc.sql(EXAMEN_SQL + """
                 where (:todos or e.activo)
                   and (cast(:tipo as text) is null or e.tipo = cast(:tipo as text))
                   and (cast(:q as text) is null or lower(e.nombre) like lower(cast(:q as text))
                        or lower(e.codigo) like lower(cast(:q as text)))
                 order by e.tipo, e.nombre
                """).param("todos", !soloActivos).param("tipo", tipo)
                .param("q", q == null || q.isBlank() ? null : "%" + q.trim() + "%").query(EXAMEN).list();
    }

    public Optional<ExamenResponse> detalle(Long id) {
        Optional<ExamenResponse> base = jdbc.sql(EXAMEN_SQL + " where e.id = :id").param("id", id).query(EXAMEN).optional();
        base.ifPresent(e -> e.parametros().addAll(parametros(id)));
        return base;
    }

    public Optional<Long> idPorCodigo(String codigo) {
        return jdbc.sql("select id from examen_catalogo where upper(codigo) = upper(:c)").param("c", codigo)
                .query(Long.class).optional();
    }

    public List<ParametroResponse> parametros(Long examenId) {
        List<ParametroResponse> sinRangos = jdbc.sql("""
                select id, codigo, nombre, unidad, unidad_ucum, tipo_valor, orden, codigo_loinc, codificacion_estado,
                       valor_normal_texto, opciones, decimales, activo
                  from examen_parametro where examen_id = :e order by orden, id
                """).param("e", examenId).query((rs, n) -> new ParametroResponse(rs.getLong("id"), rs.getString("codigo"),
                rs.getString("nombre"), rs.getString("unidad"), rs.getString("unidad_ucum"), rs.getString("tipo_valor"),
                rs.getInt("orden"), rs.getString("codigo_loinc"), rs.getString("codificacion_estado"),
                rs.getString("valor_normal_texto"), rs.getString("opciones"), corto((Number) rs.getObject("decimales")),
                rs.getBoolean("activo"), new ArrayList<>())).list();
        Map<Long, List<RangoResponse>> rangos = new LinkedHashMap<>();
        jdbc.sql("""
                select r.id, r.parametro_id, r.especie_id, e.nombre as especie, r.sexo, r.edad_min_meses, r.edad_max_meses,
                       r.valor_min, r.valor_max, r.critico_min, r.critico_max, r.fuente
                  from rango_referencia r join especie e on e.id = r.especie_id
                  join examen_parametro p on p.id = r.parametro_id
                 where p.examen_id = :e order by r.parametro_id, e.nombre, r.sexo nulls first, r.edad_min_meses nulls first
                """).param("e", examenId).query((rs, n) -> {
                    rangos.computeIfAbsent(rs.getLong("parametro_id"), k -> new ArrayList<>()).add(new RangoResponse(
                            rs.getLong("id"), rs.getLong("especie_id"), rs.getString("especie"), rs.getString("sexo"),
                            (Integer) rs.getObject("edad_min_meses"), (Integer) rs.getObject("edad_max_meses"),
                            rs.getBigDecimal("valor_min"), rs.getBigDecimal("valor_max"), rs.getBigDecimal("critico_min"),
                            rs.getBigDecimal("critico_max"), rs.getString("fuente")));
                    return null;
                }).list();
        sinRangos.forEach(p -> p.rangos().addAll(rangos.getOrDefault(p.id(), List.of())));
        return sinRangos;
    }

    public List<ParametroInfo> parametrosInfo(Long examenId) {
        return jdbc.sql(PARAM_INFO + " where p.examen_id = :e order by p.orden, p.id").param("e", examenId)
                .query(PARAM_MAPPER).list();
    }

    public Optional<ParametroInfo> parametroInfo(Long parametroId) {
        return jdbc.sql(PARAM_INFO + " where p.id = :id").param("id", parametroId).query(PARAM_MAPPER).optional();
    }

    private static final String PARAM_INFO = """
            select p.id, p.examen_id, e.codigo as examen_codigo, p.codigo, p.nombre, p.unidad, p.unidad_ucum, p.tipo_valor,
                   p.codigo_loinc, p.valor_normal_texto, p.opciones, p.activo
              from examen_parametro p join examen_catalogo e on e.id = p.examen_id
            """;

    private static final RowMapper<ParametroInfo> PARAM_MAPPER = (rs, n) -> new ParametroInfo(rs.getLong("id"),
            rs.getLong("examen_id"), rs.getString("examen_codigo"), rs.getString("codigo"), rs.getString("nombre"),
            rs.getString("unidad"), rs.getString("unidad_ucum"), rs.getString("tipo_valor"), rs.getString("codigo_loinc"),
            rs.getString("valor_normal_texto"), rs.getString("opciones"), rs.getBoolean("activo"));

    public List<Rango> rangos(Long parametroId, Long especieId) {
        return jdbc.sql("""
                select id, sexo, edad_min_meses, edad_max_meses, valor_min, valor_max, critico_min, critico_max
                  from rango_referencia where parametro_id = :p and especie_id = :e
                """).param("p", parametroId).param("e", especieId)
                .query((rs, n) -> new Rango(rs.getLong("id"), rs.getString("sexo"), (Integer) rs.getObject("edad_min_meses"),
                        (Integer) rs.getObject("edad_max_meses"), rs.getBigDecimal("valor_min"), rs.getBigDecimal("valor_max"),
                        rs.getBigDecimal("critico_min"), rs.getBigDecimal("critico_max"))).list();
    }

    public boolean existeCodigo(String codigo, Long excluirId) {
        return jdbc.sql("select count(*) from examen_catalogo where upper(codigo) = upper(:c) and id <> :id")
                .param("c", codigo).param("id", excluirId == null ? -1L : excluirId).query(Long.class).single() > 0;
    }

    public boolean existeEspecie(Long id) {
        return jdbc.sql("select count(*) from especie where id = :id").param("id", id).query(Long.class).single() > 0;
    }

    public boolean existeItem(Long id) {
        return jdbc.sql("select count(*) from item where id = :id").param("id", id).query(Long.class).single() > 0;
    }

    // ------------------------------------------------------------------ escritura

    public long insertarExamen(ExamenRequest r) {
        return jdbc.sql("""
                insert into examen_catalogo (item_id, codigo, nombre, tipo, muestra, requiere_ayuno, tiempo_entrega_horas,
                       es_externo, activo, categoria, descripcion, codigo_loinc, loinc_estado)
                values (:item, :codigo, :nombre, :tipo, :muestra, :ayuno, :horas, :externo, :activo, :cat, :desc, :loinc, :loincE)
                returning id
                """).param("item", r.itemId()).param("codigo", r.codigo().trim()).param("nombre", r.nombre().trim())
                .param("tipo", r.tipo()).param("muestra", r.muestra()).param("ayuno", Boolean.TRUE.equals(r.requiereAyuno()))
                .param("horas", r.tiempoEntregaHoras()).param("externo", Boolean.TRUE.equals(r.esExterno()))
                .param("activo", r.activo() == null || r.activo()).param("cat", r.categoria()).param("desc", r.descripcion())
                .param("loinc", r.codigoLoinc()).param("loincE", r.loincEstado() == null ? "POR_VALIDAR" : r.loincEstado())
                .query(Long.class).single();
    }

    public void actualizarExamen(Long id, ExamenRequest r) {
        jdbc.sql("""
                update examen_catalogo set item_id = :item, codigo = :codigo, nombre = :nombre, tipo = :tipo, muestra = :muestra,
                       requiere_ayuno = :ayuno, tiempo_entrega_horas = :horas, es_externo = :externo, activo = :activo,
                       categoria = :cat, descripcion = :desc, codigo_loinc = :loinc, loinc_estado = :loincE
                 where id = :id
                """).param("id", id).param("item", r.itemId()).param("codigo", r.codigo().trim())
                .param("nombre", r.nombre().trim()).param("tipo", r.tipo()).param("muestra", r.muestra())
                .param("ayuno", Boolean.TRUE.equals(r.requiereAyuno())).param("horas", r.tiempoEntregaHoras())
                .param("externo", Boolean.TRUE.equals(r.esExterno())).param("activo", r.activo() == null || r.activo())
                .param("cat", r.categoria()).param("desc", r.descripcion()).param("loinc", r.codigoLoinc())
                .param("loincE", r.loincEstado() == null ? "POR_VALIDAR" : r.loincEstado()).update();
    }

    public long insertarParametro(Long examenId, ParametroRequest p, int orden) {
        return jdbc.sql("""
                insert into examen_parametro (examen_id, codigo, nombre, unidad, unidad_ucum, tipo_valor, orden, codigo_loinc,
                       codificacion_estado, valor_normal_texto, opciones, decimales, activo)
                values (:e, :codigo, :nombre, :unidad, :ucum, :tipo, :orden, :loinc, :codif, :normal, :opciones, :dec, :activo)
                returning id
                """).param("e", examenId).param("codigo", p.codigo().trim()).param("nombre", p.nombre().trim())
                .param("unidad", p.unidad()).param("ucum", p.unidadUcum())
                .param("tipo", p.tipoValor() == null ? "NUMERICO" : p.tipoValor()).param("orden", p.orden() == null ? orden : p.orden())
                .param("loinc", p.codigoLoinc()).param("codif", p.codificacionEstado() == null ? "POR_VALIDAR" : p.codificacionEstado())
                .param("normal", p.valorNormalTexto()).param("opciones", p.opciones()).param("dec", p.decimales())
                .param("activo", p.activo() == null || p.activo()).query(Long.class).single();
    }

    public void actualizarParametro(Long id, ParametroRequest p, int orden) {
        jdbc.sql("""
                update examen_parametro set codigo = :codigo, nombre = :nombre, unidad = :unidad, unidad_ucum = :ucum,
                       tipo_valor = :tipo, orden = :orden, codigo_loinc = :loinc, codificacion_estado = :codif,
                       valor_normal_texto = :normal, opciones = :opciones, decimales = :dec, activo = :activo
                 where id = :id
                """).param("id", id).param("codigo", p.codigo().trim()).param("nombre", p.nombre().trim())
                .param("unidad", p.unidad()).param("ucum", p.unidadUcum())
                .param("tipo", p.tipoValor() == null ? "NUMERICO" : p.tipoValor()).param("orden", p.orden() == null ? orden : p.orden())
                .param("loinc", p.codigoLoinc()).param("codif", p.codificacionEstado() == null ? "POR_VALIDAR" : p.codificacionEstado())
                .param("normal", p.valorNormalTexto()).param("opciones", p.opciones()).param("dec", p.decimales())
                .param("activo", p.activo() == null || p.activo()).update();
    }

    /** Los parametros que ya no llegan se desactivan (los resultados historicos los referencian). */
    public void desactivarParametrosExcepto(Long examenId, List<Long> conservar) {
        if (conservar.isEmpty()) {
            jdbc.sql("update examen_parametro set activo = false where examen_id = :e and activo").param("e", examenId).update();
            return;
        }
        jdbc.sql("update examen_parametro set activo = false where examen_id = :e and activo and id not in (:ids)")
                .param("e", examenId).param("ids", conservar).update();
    }

    public void reemplazarRangos(Long parametroId, List<RangoRequest> rangos) {
        jdbc.sql("delete from rango_referencia where parametro_id = :p").param("p", parametroId).update();
        for (RangoRequest r : rangos) {
            jdbc.sql("""
                    insert into rango_referencia (parametro_id, especie_id, sexo, edad_min_meses, edad_max_meses, valor_min,
                           valor_max, critico_min, critico_max, fuente)
                    values (:p, :esp, :sexo, :emin, :emax, :vmin, :vmax, :cmin, :cmax, :fuente)
                    """).param("p", parametroId).param("esp", r.especieId()).param("sexo", r.sexo())
                    .param("emin", r.edadMinMeses()).param("emax", r.edadMaxMeses()).param("vmin", r.valorMin())
                    .param("vmax", r.valorMax()).param("cmin", r.criticoMin()).param("cmax", r.criticoMax())
                    .param("fuente", r.fuente()).update();
        }
    }

    public Optional<Long> parametroDeExamenPorCodigo(Long examenId, String codigo) {
        return jdbc.sql("select id from examen_parametro where examen_id = :e and upper(codigo) = upper(:c)")
                .param("e", examenId).param("c", codigo).query(Long.class).optional();
    }

    private static Short corto(Number n) {
        return n == null ? null : n.shortValue();
    }
}
