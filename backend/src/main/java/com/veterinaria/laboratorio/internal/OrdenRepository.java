package com.veterinaria.laboratorio.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.laboratorio.api.dto.LabDtos.EventoOrden;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResumen;

/** SQL de ordenes de examen, sus items y su bitacora. */
@Repository
public class OrdenRepository {

    public record OrdenRow(Long id, String codigo, Long pacienteId, Long consultaId, String prioridad, String estado,
            String indicacion, String laboratorioExterno, Instant solicitadaEn, Long solicitadoPor, String solicitadoNombre,
            Instant muestraEn, Long muestraPor, String muestraPorNombre, String muestraObs, Instant completadaEn,
            Instant canceladaEn, String motivoCancelacion) {
    }

    public record ItemRow(Long id, Long ordenId, Long examenId, String examenCodigo, String examenNombre, String tipo,
            String estado, String interpretacion, Long validadoPor, String validadoNombre, Instant validadoEn,
            int version, Long capturadoPor, String capturadoNombre, Instant capturadoEn, String motivoCancelacion) {
    }

    private static final String ORDEN_SQL = """
            select o.id, o.codigo, o.paciente_id, o.consulta_id, o.prioridad, o.estado, o.indicacion_clinica,
                   o.laboratorio_externo, o.solicitada_en, o.solicitado_por, us.nombre as solicitado_nombre,
                   o.muestra_tomada_en, o.muestra_tomada_por, um.nombre as muestra_nombre, o.muestra_observaciones,
                   o.completada_en, o.cancelada_en, o.motivo_cancelacion
              from orden_examen o
              left join usuario us on us.id = o.solicitado_por
              left join usuario um on um.id = o.muestra_tomada_por
            """;

    private static final RowMapper<OrdenRow> ORDEN = (rs, n) -> new OrdenRow(rs.getLong("id"), rs.getString("codigo"),
            rs.getLong("paciente_id"), Sql.largo(rs, "consulta_id"), rs.getString("prioridad"), rs.getString("estado"),
            rs.getString("indicacion_clinica"), rs.getString("laboratorio_externo"), Sql.instante(rs, "solicitada_en"),
            Sql.largo(rs, "solicitado_por"), rs.getString("solicitado_nombre"), Sql.instante(rs, "muestra_tomada_en"),
            Sql.largo(rs, "muestra_tomada_por"), rs.getString("muestra_nombre"), rs.getString("muestra_observaciones"),
            Sql.instante(rs, "completada_en"), Sql.instante(rs, "cancelada_en"), rs.getString("motivo_cancelacion"));

    private static final String ITEM_SQL = """
            select i.id, i.orden_id, i.examen_id, e.codigo as examen_codigo, e.nombre as examen_nombre, e.tipo,
                   i.estado, i.interpretacion, i.validado_por, uv.nombre as validado_nombre, i.validado_en,
                   i.version_actual, i.capturado_por, uc.nombre as capturado_nombre, i.capturado_en, i.motivo_cancelacion
              from orden_examen_item i
              join examen_catalogo e on e.id = i.examen_id
              left join usuario uv on uv.id = i.validado_por
              left join usuario uc on uc.id = i.capturado_por
            """;

    private static final RowMapper<ItemRow> ITEM = (rs, n) -> new ItemRow(rs.getLong("id"), rs.getLong("orden_id"),
            rs.getLong("examen_id"), rs.getString("examen_codigo"), rs.getString("examen_nombre"), rs.getString("tipo"),
            rs.getString("estado"), rs.getString("interpretacion"), Sql.largo(rs, "validado_por"),
            rs.getString("validado_nombre"), Sql.instante(rs, "validado_en"), rs.getInt("version_actual"),
            Sql.largo(rs, "capturado_por"), rs.getString("capturado_nombre"), Sql.instante(rs, "capturado_en"),
            rs.getString("motivo_cancelacion"));

    private final JdbcClient jdbc;

    public OrdenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ ordenes

    public long siguienteId() {
        return jdbc.sql("select nextval(pg_get_serial_sequence('orden_examen', 'id'))").query(Long.class).single();
    }

    public void insertarOrden(long id, String codigo, Long sedeId, Long pacienteId, Long consultaId, Long solicitadoPor,
            String prioridad, String laboratorioExterno, String indicacion) {
        jdbc.sql("""
                insert into orden_examen (id, codigo, sede_id, paciente_id, consulta_id, solicitado_por, prioridad,
                       laboratorio_externo, indicacion_clinica)
                values (:id, :codigo, :sede, :p, :c, :u, :prio, :ext, :ind)
                """).param("id", id).param("codigo", codigo).param("sede", sedeId).param("p", pacienteId)
                .param("c", consultaId).param("u", solicitadoPor).param("prio", prioridad).param("ext", laboratorioExterno)
                .param("ind", indicacion).update();
    }

    public long insertarItem(Long ordenId, Long examenId) {
        return jdbc.sql("insert into orden_examen_item (orden_id, examen_id) values (:o, :e) returning id")
                .param("o", ordenId).param("e", examenId).query(Long.class).single();
    }

    public Optional<OrdenRow> orden(Long id) {
        return jdbc.sql(ORDEN_SQL + " where o.id = :id").param("id", id).query(ORDEN).optional();
    }

    public Optional<OrdenRow> ordenPorCodigo(String codigo) {
        return jdbc.sql(ORDEN_SQL + " where upper(o.codigo) = upper(:c)").param("c", codigo).query(ORDEN).optional();
    }

    /** Bloquea la orden hasta el fin de la transaccion (serializa cambios de estado concurrentes). */
    public void bloquear(Long id) {
        jdbc.sql("select id from orden_examen where id = :id for update").param("id", id).query(Long.class).optional();
    }

    public List<ItemRow> items(Long ordenId) {
        return jdbc.sql(ITEM_SQL + " where i.orden_id = :o order by i.id").param("o", ordenId).query(ITEM).list();
    }

    public Optional<ItemRow> item(Long ordenId, Long itemId) {
        return jdbc.sql(ITEM_SQL + " where i.orden_id = :o and i.id = :i").param("o", ordenId).param("i", itemId)
                .query(ITEM).optional();
    }

    public List<Long> ordenesAbiertasDePaciente(Long pacienteId) {
        return jdbc.sql("""
                select id from orden_examen where paciente_id = :p and estado in ('SOLICITADA','MUESTRA_TOMADA','EN_PROCESO')
                 order by solicitada_en desc
                """).param("p", pacienteId).query(Long.class).list();
    }

    public Optional<Long> pacientePorCodigo(String codigo) {
        return jdbc.sql("select id from paciente where upper(codigo) = upper(:c)").param("c", codigo)
                .query(Long.class).optional();
    }

    public void cambiarEstado(Long id, String estado) {
        jdbc.sql("update orden_examen set estado = :e where id = :id").param("id", id).param("e", estado).update();
    }

    public void registrarMuestra(Long id, Long usuarioId, String observaciones) {
        jdbc.sql("""
                update orden_examen set estado = 'MUESTRA_TOMADA', muestra_tomada_en = now(), muestra_tomada_por = :u,
                       muestra_observaciones = :o where id = :id
                """).param("id", id).param("u", usuarioId).param("o", observaciones).update();
    }

    public void completar(Long id) {
        jdbc.sql("update orden_examen set estado = 'COMPLETADA', completada_en = now() where id = :id").param("id", id).update();
    }

    public void cancelar(Long id, Long usuarioId, String motivo) {
        jdbc.sql("""
                update orden_examen set estado = 'CANCELADA', cancelada_en = now(), cancelada_por = :u, motivo_cancelacion = :m
                 where id = :id
                """).param("id", id).param("u", usuarioId).param("m", motivo).update();
    }

    public void cancelarItemsPendientes(Long ordenId, String motivo) {
        jdbc.sql("""
                update orden_examen_item set estado = 'CANCELADO', motivo_cancelacion = :m
                 where orden_id = :o and estado <> 'VALIDADO'
                """).param("o", ordenId).param("m", motivo).update();
    }

    // ------------------------------------------------------------------ items

    public void marcarCapturado(Long itemId, Long usuarioId) {
        jdbc.sql("""
                update orden_examen_item set estado = 'RESULTADO', capturado_por = coalesce(:u, capturado_por), capturado_en = now()
                 where id = :id and estado in ('SOLICITADO','RESULTADO')
                """).param("id", itemId).param("u", usuarioId).update();
    }

    public int nuevaVersion(Long itemId) {
        return jdbc.sql("select version_actual + 1 from orden_examen_item where id = :id").param("id", itemId)
                .query(Integer.class).single();
    }

    public long insertarValidacion(Long itemId, int version, String tipo, String interpretacion, String motivo, Long usuarioId) {
        return jdbc.sql("""
                insert into orden_item_validacion (orden_item_id, version, tipo, interpretacion, motivo, validado_por)
                values (:i, :v, :t, :int, :m, :u) returning id
                """).param("i", itemId).param("v", version).param("t", tipo).param("int", interpretacion)
                .param("m", motivo).param("u", usuarioId).query(Long.class).single();
    }

    public void marcarValidado(Long itemId, int version, String interpretacion, Long usuarioId) {
        jdbc.sql("""
                update orden_examen_item set estado = 'VALIDADO', interpretacion = :int, validado_por = :u,
                       validado_en = now(), version_actual = :v where id = :id
                """).param("id", itemId).param("int", interpretacion).param("u", usuarioId).param("v", version).update();
    }

    public int itemsPendientesDeValidar(Long ordenId) {
        return jdbc.sql("select count(*) from orden_examen_item where orden_id = :o and estado not in ('VALIDADO','CANCELADO')")
                .param("o", ordenId).query(Integer.class).single();
    }

    public int itemsValidados(Long ordenId) {
        return jdbc.sql("select count(*) from orden_examen_item where orden_id = :o and estado = 'VALIDADO'")
                .param("o", ordenId).query(Integer.class).single();
    }

    // ------------------------------------------------------------------ bitacora

    public void evento(Long ordenId, Long itemId, String tipo, String antes, String despues, String detalle,
            Long usuarioId, String origen) {
        jdbc.sql("""
                insert into orden_examen_evento (orden_id, item_id, tipo, estado_anterior, estado_nuevo, detalle, usuario_id, origen)
                values (:o, :i, :t, :a, :d, :det, :u, :org)
                """).param("o", ordenId).param("i", itemId).param("t", tipo).param("a", antes).param("d", despues)
                .param("det", detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500))
                .param("u", usuarioId).param("org", origen).update();
    }

    public List<EventoOrden> eventos(Long ordenId) {
        return jdbc.sql("""
                select e.id, e.tipo, e.estado_anterior, e.estado_nuevo, e.detalle, e.usuario_id, u.nombre, e.origen, e.creado_en
                  from orden_examen_evento e left join usuario u on u.id = e.usuario_id
                 where e.orden_id = :o order by e.creado_en, e.id
                """).param("o", ordenId).query((rs, n) -> new EventoOrden(rs.getLong("id"), rs.getString("tipo"),
                rs.getString("estado_anterior"), rs.getString("estado_nuevo"), rs.getString("detalle"),
                Sql.persona(rs, "usuario_id", "nombre"), rs.getString("origen"), Sql.instante(rs, "creado_en"))).list();
    }

    // ------------------------------------------------------------------ bandeja

    private static final String FILTROS = """
              from orden_examen o
              join paciente p on p.id = o.paciente_id
              join especie esp on esp.id = p.especie_id
              left join usuario us on us.id = o.solicitado_por
             where (cast(:estados as text) is null or o.estado = any(string_to_array(cast(:estados as text), ',')))
               and (cast(:prioridad as text) is null or o.prioridad = cast(:prioridad as text))
               and (cast(:paciente as bigint) is null or o.paciente_id = cast(:paciente as bigint))
               and (cast(:consulta as bigint) is null or o.consulta_id = cast(:consulta as bigint))
               and (cast(:tipo as text) is null or exists (select 1 from orden_examen_item i join examen_catalogo ec on ec.id = i.examen_id
                                                            where i.orden_id = o.id and ec.tipo = cast(:tipo as text)))
               and (cast(:desde as timestamptz) is null or o.solicitada_en >= cast(:desde as timestamptz))
               and (cast(:hasta as timestamptz) is null or o.solicitada_en < cast(:hasta as timestamptz))
               and (cast(:q as text) is null or lower(p.nombre) like lower(cast(:q as text))
                    or lower(p.codigo) like lower(cast(:q as text)) or lower(o.codigo) like lower(cast(:q as text)))
            """;

    public record Filtro(String estados, String prioridad, Long pacienteId, Long consultaId, String tipo,
            java.time.OffsetDateTime desde, java.time.OffsetDateTime hasta, String q) {
    }

    public long contar(Filtro f) {
        return aplicar(jdbc.sql("select count(*) " + FILTROS), f).query(Long.class).single();
    }

    public List<OrdenResumen> pagina(Filtro f, int size, long offset) {
        return aplicar(jdbc.sql("""
                select o.id, o.codigo, o.paciente_id, p.nombre as paciente_nombre, p.codigo as paciente_codigo,
                       esp.nombre as especie, o.consulta_id, o.prioridad, o.estado, o.solicitada_en, o.solicitado_por,
                       us.nombre as solicitado_nombre, o.laboratorio_externo,
                       (select count(*) from orden_examen_item i where i.orden_id = o.id and i.estado <> 'CANCELADO') as items_total,
                       (select count(*) from orden_examen_item i where i.orden_id = o.id and i.estado = 'VALIDADO') as items_validados,
                       exists (select 1 from resultado_examen r join orden_examen_item i on i.id = r.orden_item_id
                                where i.orden_id = o.id and r.critico and r.estado <> 'CORREGIDO') as hay_criticos,
                       array(select ec.nombre from orden_examen_item i join examen_catalogo ec on ec.id = i.examen_id
                              where i.orden_id = o.id order by i.id) as examenes
                """ + FILTROS + """
                 order by (o.estado in ('COMPLETADA','CANCELADA')),
                          case when o.estado in ('COMPLETADA','CANCELADA') then 0
                               else case o.prioridad when 'STAT' then 0 when 'URGENTE' then 1 else 2 end end,
                          case when o.estado in ('COMPLETADA','CANCELADA') then null else o.solicitada_en end asc,
                          o.solicitada_en desc, o.id desc
                 limit :size offset :offset
                """), f).param("size", size).param("offset", offset).query((rs, n) -> new OrdenResumen(rs.getLong("id"),
                rs.getString("codigo"), rs.getLong("paciente_id"), rs.getString("paciente_nombre"),
                rs.getString("paciente_codigo"), rs.getString("especie"), Sql.largo(rs, "consulta_id"),
                rs.getString("prioridad"), rs.getString("estado"), Sql.instante(rs, "solicitada_en"),
                Sql.persona(rs, "solicitado_por", "solicitado_nombre"), rs.getString("laboratorio_externo"),
                rs.getInt("items_total"), rs.getInt("items_validados"), rs.getBoolean("hay_criticos"),
                List.of((String[]) rs.getArray("examenes").getArray()))).list();
    }

    private static JdbcClient.StatementSpec aplicar(JdbcClient.StatementSpec spec, Filtro f) {
        return spec.param("estados", f.estados()).param("prioridad", f.prioridad()).param("paciente", f.pacienteId())
                .param("consulta", f.consultaId()).param("tipo", f.tipo()).param("desde", f.desde())
                .param("hasta", f.hasta()).param("q", f.q());
    }

    // ------------------------------------------------------------------ consulta vinculada

    public record ConsultaRef(Long pacienteId, String estado) {
    }

    public Optional<ConsultaRef> consulta(Long id) {
        return jdbc.sql("select paciente_id, estado from consulta where id = :id").param("id", id)
                .query((rs, n) -> new ConsultaRef(rs.getLong("paciente_id"), rs.getString("estado"))).optional();
    }

    /** Nota posterior al cierre de la consulta (consulta_addendum es append-only y admite insertar tras el cierre). */
    public void addendum(Long consultaId, Long autorId, String tipo, String texto) {
        jdbc.sql("insert into consulta_addendum (consulta_id, autor_id, tipo, texto) values (:c, :a, :t, :x)")
                .param("c", consultaId).param("a", autorId).param("t", tipo).param("x", texto).update();
    }

    public Optional<Long> usuarioActivoConRol(Long usuarioId, List<String> roles) {
        return jdbc.sql("select id from usuario where id = :id and activo and rol in (:roles)").param("id", usuarioId)
                .param("roles", roles).query(Long.class).optional();
    }
}
