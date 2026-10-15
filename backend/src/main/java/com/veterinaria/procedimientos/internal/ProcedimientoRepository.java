package com.veterinaria.procedimientos.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.InsumoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.InsumoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.Persona;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResumen;

/** SQL de cirugia (procedimientos y cirugias), monitoreo anestesico, insumos y consentimiento. */
@Repository
public class ProcedimientoRepository {

    /** Fila completa de {@code cirugia} con nombres resueltos (sin listas hijas). */
    public record Fila(Long id, String codigo, String tipo, String estado, Long pacienteId, Long consultaId, Long itemId,
            String procedimiento, Long cirujanoId, String cirujanoNombre, Long ayudanteId, String ayudanteNombre,
            Long anestesiologoId, String anestesiologoNombre, Long consentimientoId, Instant programadaPara, Instant inicioEn,
            Instant finEn, Short asa, boolean urgencia, Integer ayunoHoras, java.math.BigDecimal pesoKg, String anestesiaTipo,
            String premedicacion, String induccion, String mantenimiento, String analgesia, String diagnosticoPreoperatorio,
            String descripcionTecnica, String hallazgos, String complicaciones, String materialSutura,
            boolean muestrasHistopatologia, String diagnosticoPostoperatorio, String resultado,
            String indicacionesPostoperatorias, String controles, LocalDate proximoControl, String motivoCancelacion,
            Instant creadoEn) {
    }

    private static final String SELECT = """
            select c.id, c.codigo, c.tipo, c.estado, c.paciente_id, c.consulta_id, c.item_id, c.procedimiento,
                   c.cirujano_id, uc.nombre as cirujano_nombre, c.ayudante_id, ua.nombre as ayudante_nombre,
                   c.anestesiologo_id, un.nombre as anestesiologo_nombre, c.consentimiento_id, c.programada_para,
                   c.inicio_en, c.fin_en, c.asa, c.urgencia, c.ayuno_horas, c.peso_kg, c.anestesia_tipo, c.premedicacion,
                   c.induccion, c.mantenimiento, c.analgesia, c.diagnostico_preoperatorio, c.descripcion_tecnica,
                   c.hallazgos, c.complicaciones, c.material_sutura, c.muestras_histopatologia,
                   c.diagnostico_postoperatorio, c.resultado, c.indicaciones_postoperatorias, c.controles,
                   c.proximo_control, c.motivo_cancelacion, c.creado_en
              from cirugia c
              left join usuario uc on uc.id = c.cirujano_id
              left join usuario ua on ua.id = c.ayudante_id
              left join usuario un on un.id = c.anestesiologo_id
            """;

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static Short corto(ResultSet rs, String col) throws SQLException {
        Number n = (Number) rs.getObject(col);
        return n == null ? null : n.shortValue();
    }

    private static final RowMapper<Fila> FILA = (rs, n) -> new Fila(rs.getLong("id"), rs.getString("codigo"), rs.getString("tipo"),
            rs.getString("estado"), rs.getLong("paciente_id"), (Long) rs.getObject("consulta_id"), (Long) rs.getObject("item_id"),
            rs.getString("procedimiento"), rs.getLong("cirujano_id"), rs.getString("cirujano_nombre"),
            (Long) rs.getObject("ayudante_id"), rs.getString("ayudante_nombre"), (Long) rs.getObject("anestesiologo_id"),
            rs.getString("anestesiologo_nombre"), (Long) rs.getObject("consentimiento_id"), ts(rs, "programada_para"),
            ts(rs, "inicio_en"), ts(rs, "fin_en"), corto(rs, "asa"), rs.getBoolean("urgencia"),
            (Integer) rs.getObject("ayuno_horas"), rs.getBigDecimal("peso_kg"), rs.getString("anestesia_tipo"),
            rs.getString("premedicacion"), rs.getString("induccion"), rs.getString("mantenimiento"), rs.getString("analgesia"),
            rs.getString("diagnostico_preoperatorio"), rs.getString("descripcion_tecnica"), rs.getString("hallazgos"),
            rs.getString("complicaciones"), rs.getString("material_sutura"), rs.getBoolean("muestras_histopatologia"),
            rs.getString("diagnostico_postoperatorio"), rs.getString("resultado"), rs.getString("indicaciones_postoperatorias"),
            rs.getString("controles"), rs.getObject("proximo_control", LocalDate.class), rs.getString("motivo_cancelacion"),
            ts(rs, "creado_en"));

    private final JdbcClient jdbc;

    public ProcedimientoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ lectura

    public Optional<Fila> buscar(Long id) {
        return jdbc.sql(SELECT + " where c.id = :id").param("id", id).query(FILA).optional();
    }

    public long contar(Long pacienteId, Long consultaId, String tipo, String estado) {
        return jdbc.sql("""
                select count(*) from cirugia c
                 where (cast(:p as bigint) is null or c.paciente_id = cast(:p as bigint))
                   and (cast(:c as bigint) is null or c.consulta_id = cast(:c as bigint))
                   and (cast(:t as text) is null or c.tipo = cast(:t as text))
                   and (cast(:e as text) is null or c.estado = cast(:e as text))
                """).param("p", pacienteId).param("c", consultaId).param("t", tipo).param("e", estado).query(Long.class).single();
    }

    public List<ProcedimientoResumen> pagina(Long pacienteId, Long consultaId, String tipo, String estado, int size, long offset) {
        return jdbc.sql("""
                select c.id, c.codigo, c.tipo, c.procedimiento, c.estado, c.paciente_id, p.nombre as paciente_nombre,
                       c.consulta_id, c.cirujano_id, uc.nombre as cirujano_nombre, c.programada_para, c.inicio_en, c.fin_en,
                       c.urgencia, (select k.estado from consentimiento k where k.id = c.consentimiento_id) as consentimiento_estado
                  from cirugia c
                  join paciente p on p.id = c.paciente_id
                  left join usuario uc on uc.id = c.cirujano_id
                 where (cast(:p as bigint) is null or c.paciente_id = cast(:p as bigint))
                   and (cast(:c as bigint) is null or c.consulta_id = cast(:c as bigint))
                   and (cast(:t as text) is null or c.tipo = cast(:t as text))
                   and (cast(:e as text) is null or c.estado = cast(:e as text))
                 order by coalesce(c.inicio_en, c.programada_para, c.creado_en) desc, c.id desc
                 limit :size offset :offset
                """).param("p", pacienteId).param("c", consultaId).param("t", tipo).param("e", estado).param("size", size)
                .param("offset", offset).query((rs, n) -> new ProcedimientoResumen(rs.getLong("id"), rs.getString("codigo"),
                        rs.getString("tipo"), rs.getString("procedimiento"), rs.getString("estado"), rs.getLong("paciente_id"),
                        rs.getString("paciente_nombre"), (Long) rs.getObject("consulta_id"),
                        new Persona(rs.getLong("cirujano_id"), rs.getString("cirujano_nombre")), ts(rs, "programada_para"),
                        ts(rs, "inicio_en"), ts(rs, "fin_en"), rs.getBoolean("urgencia"), rs.getString("consentimiento_estado"))).list();
    }

    public List<InsumoResponse> insumos(Long id) {
        return jdbc.sql("select id, item_id, descripcion, cantidad, lote, notas from cirugia_insumo where cirugia_id = :id order by id")
                .param("id", id).query((rs, n) -> new InsumoResponse(rs.getLong("id"), (Long) rs.getObject("item_id"),
                        rs.getString("descripcion"), rs.getBigDecimal("cantidad"), rs.getString("lote"), rs.getString("notas"))).list();
    }

    public List<MonitoreoResponse> monitoreo(Long id) {
        return jdbc.sql("""
                select m.id, m.registrado_en, m.frecuencia_cardiaca, m.frecuencia_respiratoria, m.spo2, m.etco2,
                       m.presion_sistolica, m.temperatura, m.agente_inhalatorio_pct, m.fluido_ml_h, m.notas, m.registrado_por,
                       u.nombre as registrado_nombre
                  from cirugia_monitoreo_anestesico m left join usuario u on u.id = m.registrado_por
                 where m.cirugia_id = :id order by m.registrado_en, m.id
                """).param("id", id).query((rs, n) -> new MonitoreoResponse(rs.getLong("id"), ts(rs, "registrado_en"),
                (Integer) rs.getObject("frecuencia_cardiaca"), (Integer) rs.getObject("frecuencia_respiratoria"),
                rs.getBigDecimal("spo2"), rs.getBigDecimal("etco2"), (Integer) rs.getObject("presion_sistolica"),
                rs.getBigDecimal("temperatura"), rs.getBigDecimal("agente_inhalatorio_pct"), rs.getBigDecimal("fluido_ml_h"),
                rs.getString("notas"), (Long) rs.getObject("registrado_por") == null ? null
                        : new Persona(rs.getLong("registrado_por"), rs.getString("registrado_nombre")))).list();
    }

    public Optional<ConsentimientoResponse> consentimiento(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select k.id, k.tipo, k.estado, k.tutor_id, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as tutor_nombre,
                       k.texto_firmado, k.firmante_nombre, k.firmante_documento, k.metodo_firma, k.firmado_en, k.firma_ruta,
                       k.registrado_por, u.nombre as registrado_nombre
                  from consentimiento k join tutor t on t.id = k.tutor_id left join usuario u on u.id = k.registrado_por
                 where k.id = :id
                """).param("id", id).query((rs, n) -> {
                    String ruta = rs.getString("firma_ruta");
                    Long archivo = ruta != null && ruta.startsWith("adjunto:") ? Long.valueOf(ruta.substring(8)) : null;
                    return new ConsentimientoResponse(rs.getLong("id"), rs.getString("tipo"), rs.getString("estado"),
                            new Persona(rs.getLong("tutor_id"), rs.getString("tutor_nombre")), rs.getString("texto_firmado"),
                            rs.getString("firmante_nombre"), rs.getString("firmante_documento"), rs.getString("metodo_firma"),
                            ts(rs, "firmado_en"), archivo, (Long) rs.getObject("registrado_por") == null ? null
                                    : new Persona(rs.getLong("registrado_por"), rs.getString("registrado_nombre")));
                }).optional();
    }

    public Optional<String> estadoConsentimiento(Long id) {
        return id == null ? Optional.empty()
                : jdbc.sql("select estado from consentimiento where id = :id").param("id", id).query(String.class).optional();
    }

    public Optional<Persona> usuarioActivo(Long id, List<String> roles) {
        return jdbc.sql("select id, nombre from usuario where id = :id and activo and (cast(:todos as boolean) or rol in (:roles))")
                .param("id", id).param("todos", roles.isEmpty()).param("roles", roles.isEmpty() ? List.of("-") : roles)
                .query((rs, n) -> new Persona(rs.getLong("id"), rs.getString("nombre"))).optional();
    }

    public Optional<String> itemNombre(Long itemId) {
        return jdbc.sql("select nombre from item where id = :id").param("id", itemId).query(String.class).optional();
    }

    public Optional<Long> consultaDePaciente(Long consultaId, Long pacienteId) {
        return jdbc.sql("select id from consulta where id = :c and paciente_id = :p").param("c", consultaId).param("p", pacienteId)
                .query(Long.class).optional();
    }

    public Optional<Long> tutorPrincipal(Long pacienteId) {
        return jdbc.sql("select tutor_id from paciente_tutor where paciente_id = :p order by es_principal desc, tutor_id limit 1")
                .param("p", pacienteId).query(Long.class).optional();
    }

    public boolean esTutorDe(Long tutorId, Long pacienteId) {
        return jdbc.sql("select count(*) from paciente_tutor where paciente_id = :p and tutor_id = :t").param("p", pacienteId)
                .param("t", tutorId).query(Long.class).single() > 0;
    }

    public Optional<String> plantillaConsentimiento(String tipo) {
        return jdbc.sql("select texto from consentimiento_plantilla where tipo = :t and activo order by version desc, id desc limit 1")
                .param("t", tipo).query(String.class).optional();
    }

    // ------------------------------------------------------------------ escritura

    public long siguienteId() {
        return jdbc.sql("select nextval(pg_get_serial_sequence('cirugia', 'id'))").query(Long.class).single();
    }

    public void insertar(long id, String codigo, Long sedeId, Map<String, Object> campos) {
        Map<String, Object> p = new HashMap<>(campos);
        p.put("id", id);
        p.put("codigo", codigo);
        p.put("sede", sedeId);
        jdbc.sql("""
                insert into cirugia (id, codigo, sede_id, paciente_id, consulta_id, item_id, tipo, procedimiento, cirujano_id,
                       ayudante_id, anestesiologo_id, programada_para, inicio_en, fin_en, asa, urgencia, ayuno_horas, peso_kg,
                       anestesia_tipo, premedicacion, mantenimiento, induccion, analgesia, diagnostico_preoperatorio,
                       descripcion_tecnica, hallazgos, complicaciones, material_sutura, muestras_histopatologia,
                       diagnostico_postoperatorio, resultado, indicaciones_postoperatorias, controles, proximo_control, estado,
                       creado_por)
                values (:id, :codigo, :sede, :paciente, :consulta, :item, :tipo, :procedimiento, :cirujano, :ayudante,
                        :anestesiologo, :programada, :inicio, :fin, :asa, :urgencia, :ayuno, :peso, :anestesiaTipo, :premedicacion,
                        :mantenimiento, :induccion, :analgesia, :dxPre, :tecnica, :hallazgos, :complicaciones, :sutura,
                        :histo, :dxPost, :resultado, :cuidados, :controles, :proximoControl, :estado, :creadoPor)
                """).params(p).update();
    }

    /** Actualiza solo las columnas con valor (null = sin cambio). */
    public void actualizar(Long id, Map<String, Object> p) {
        Map<String, Object> params = new HashMap<>(p);
        params.put("id", id);
        jdbc.sql("""
                update cirugia set item_id = coalesce(:item, item_id), procedimiento = coalesce(:procedimiento, procedimiento),
                       cirujano_id = coalesce(:cirujano, cirujano_id), ayudante_id = coalesce(:ayudante, ayudante_id),
                       anestesiologo_id = coalesce(:anestesiologo, anestesiologo_id),
                       programada_para = coalesce(:programada, programada_para), inicio_en = coalesce(:inicio, inicio_en),
                       fin_en = coalesce(:fin, fin_en), asa = coalesce(:asa, asa), urgencia = coalesce(:urgencia, urgencia),
                       ayuno_horas = coalesce(:ayuno, ayuno_horas), peso_kg = coalesce(:peso, peso_kg),
                       anestesia_tipo = coalesce(:anestesiaTipo, anestesia_tipo), premedicacion = coalesce(:premedicacion, premedicacion),
                       induccion = coalesce(:induccion, induccion), mantenimiento = coalesce(:mantenimiento, mantenimiento),
                       analgesia = coalesce(:analgesia, analgesia),
                       diagnostico_preoperatorio = coalesce(:dxPre, diagnostico_preoperatorio),
                       descripcion_tecnica = coalesce(:tecnica, descripcion_tecnica), hallazgos = coalesce(:hallazgos, hallazgos),
                       complicaciones = coalesce(:complicaciones, complicaciones), material_sutura = coalesce(:sutura, material_sutura),
                       muestras_histopatologia = coalesce(:histo, muestras_histopatologia),
                       diagnostico_postoperatorio = coalesce(:dxPost, diagnostico_postoperatorio),
                       resultado = coalesce(:resultado, resultado),
                       indicaciones_postoperatorias = coalesce(:cuidados, indicaciones_postoperatorias),
                       controles = coalesce(:controles, controles), proximo_control = coalesce(:proximoControl, proximo_control),
                       actualizado_en = now()
                 where id = :id
                """).params(params).update();
    }

    public void cambiarEstado(Long id, String estado, Instant inicio, Instant fin, String motivoCancelacion) {
        jdbc.sql("""
                update cirugia set estado = :e, inicio_en = coalesce(:i, inicio_en), fin_en = coalesce(:f, fin_en),
                       motivo_cancelacion = coalesce(:m, motivo_cancelacion), actualizado_en = now()
                 where id = :id
                """).param("id", id).param("e", estado).param("i", inicio == null ? null : inicio.atOffset(java.time.ZoneOffset.UTC))
                .param("f", fin == null ? null : fin.atOffset(java.time.ZoneOffset.UTC)).param("m", motivoCancelacion).update();
    }

    public void reemplazarInsumos(Long cirugiaId, List<InsumoRequest> insumos) {
        jdbc.sql("delete from cirugia_insumo where cirugia_id = :id").param("id", cirugiaId).update();
        for (InsumoRequest i : insumos) {
            jdbc.sql("insert into cirugia_insumo (cirugia_id, item_id, descripcion, cantidad, lote, notas) values (:c, :i, :d, :q, :l, :n)")
                    .param("c", cirugiaId).param("i", i.itemId()).param("d", i.descripcion().trim()).param("q", i.cantidad())
                    .param("l", i.lote()).param("n", i.notas()).update();
        }
    }

    public long insertarMonitoreo(Long cirugiaId, MonitoreoRequest m, Instant cuando, Long usuarioId) {
        return jdbc.sql("""
                insert into cirugia_monitoreo_anestesico (cirugia_id, registrado_en, frecuencia_cardiaca, frecuencia_respiratoria,
                       spo2, etco2, presion_sistolica, temperatura, agente_inhalatorio_pct, fluido_ml_h, notas, registrado_por)
                values (:c, :t, :fc, :fr, :spo2, :etco2, :pas, :temp, :agente, :fluido, :notas, :u) returning id
                """).param("c", cirugiaId).param("t", java.sql.Timestamp.from(cuando)).param("fc", m.frecuenciaCardiaca())
                .param("fr", m.frecuenciaRespiratoria()).param("spo2", m.spo2()).param("etco2", m.etco2())
                .param("pas", m.presionSistolica()).param("temp", m.temperatura()).param("agente", m.agenteInhalatorioPct())
                .param("fluido", m.fluidoMlH()).param("notas", m.notas()).param("u", usuarioId).query(Long.class).single();
    }

    public long insertarConsentimiento(String tipo, Long tutorId, Long pacienteId, Long consultaId, Long sedeId, String estado,
            String texto, String firmante, String documento, String firmaRuta, String firmaHash, String metodo, Instant firmadoEn,
            Long usuarioId) {
        return jdbc.sql("""
                insert into consentimiento (tipo, tutor_id, paciente_id, consulta_id, sede_id, estado, texto_firmado, firmante_nombre,
                       firmante_documento, firma_ruta, firma_hash, metodo_firma, firmado_en, registrado_por)
                values (:tipo, :tutor, :paciente, :consulta, :sede, :estado, :texto, :firmante, :doc, :ruta, :hash, :metodo, :firmado, :u)
                returning id
                """).param("tipo", tipo).param("tutor", tutorId).param("paciente", pacienteId).param("consulta", consultaId)
                .param("sede", sedeId).param("estado", estado).param("texto", texto).param("firmante", firmante)
                .param("doc", documento).param("ruta", firmaRuta).param("hash", firmaHash).param("metodo", metodo)
                .param("firmado", firmadoEn == null ? null : java.sql.Timestamp.from(firmadoEn)).param("u", usuarioId)
                .query(Long.class).single();
    }

    public void vincularConsentimiento(Long cirugiaId, Long consentimientoId) {
        jdbc.sql("update cirugia set consentimiento_id = :k, actualizado_en = now() where id = :id")
                .param("id", cirugiaId).param("k", consentimientoId).update();
    }
}
