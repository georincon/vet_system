package com.veterinaria.archivos.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.archivos.ArchivosApi.ArchivoInfo;

/** Acceso SQL a la tabla {@code adjunto}. */
@Repository
public class ArchivoRepository {

    /** Fila con las rutas internas (no se exponen en la API). */
    public record Fila(ArchivoInfo info, String ruta, String miniaturaRuta) {
    }

    private static final String SELECT = """
            select a.id, a.paciente_id, a.consulta_id, a.orden_examen_id, a.orden_item_id, a.cirugia_id, a.tipo,
                   a.nombre_original, a.content_type, coalesce(a.tamano_bytes, 0) as tamano_bytes, a.hash_sha256,
                   a.descripcion, a.miniatura_ruta, a.ancho_px, a.alto_px, a.subido_por_id, u.nombre as subido_por_nombre,
                   a.subido_en, a.activo, a.eliminado_en, a.motivo_eliminacion, a.ruta
              from adjunto a left join usuario u on u.id = a.subido_por_id
            """;

    private static final RowMapper<Fila> MAPPER = (rs, n) -> {
        long id = rs.getLong("id");
        String miniatura = rs.getString("miniatura_ruta");
        OffsetDateTime eliminado = rs.getObject("eliminado_en", OffsetDateTime.class);
        ArchivoInfo info = new ArchivoInfo(id, rs.getLong("paciente_id"), (Long) rs.getObject("consulta_id"),
                (Long) rs.getObject("orden_examen_id"), (Long) rs.getObject("orden_item_id"),
                (Long) rs.getObject("cirugia_id"), rs.getString("tipo"), rs.getString("nombre_original"),
                rs.getString("content_type"), rs.getLong("tamano_bytes"), rs.getString("hash_sha256"),
                rs.getString("descripcion"), miniatura != null, (Integer) rs.getObject("ancho_px"),
                (Integer) rs.getObject("alto_px"), (Long) rs.getObject("subido_por_id"),
                rs.getString("subido_por_nombre"), rs.getObject("subido_en", OffsetDateTime.class).toInstant(),
                rs.getBoolean("activo"), eliminado == null ? null : eliminado.toInstant(),
                rs.getString("motivo_eliminacion"), "/api/archivos/" + id + "/contenido",
                miniatura == null ? null : "/api/archivos/" + id + "/miniatura");
        return new Fila(info, rs.getString("ruta"), miniatura);
    };

    private final JdbcClient jdbc;

    public ArchivoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Fila> buscar(Long id) {
        return jdbc.sql(SELECT + " where a.id = :id").param("id", id).query(MAPPER).optional();
    }

    public List<Fila> listarPaciente(Long pacienteId, String tipo, boolean incluirEliminados) {
        return jdbc.sql(SELECT + """
                 where a.paciente_id = :p and (:eliminados or a.activo)
                   and (cast(:tipo as text) is null or a.tipo = cast(:tipo as text))
                 order by a.subido_en desc, a.id desc
                """).param("p", pacienteId).param("eliminados", incluirEliminados).param("tipo", tipo)
                .query(MAPPER).list();
    }

    public List<Fila> listarPorColumna(String columna, Long valor) {
        // columna proviene de constantes internas (no de la solicitud)
        return jdbc.sql(SELECT + " where a." + columna + " = :v and a.activo order by a.subido_en, a.id")
                .param("v", valor).query(MAPPER).list();
    }

    public List<Fila> filtrar(Long consultaId, Long ordenId, Long itemId, Long cirugiaId) {
        return jdbc.sql(SELECT + """
                 where a.activo
                   and (cast(:c as bigint) is null or a.consulta_id = cast(:c as bigint))
                   and (cast(:o as bigint) is null or a.orden_examen_id = cast(:o as bigint))
                   and (cast(:i as bigint) is null or a.orden_item_id = cast(:i as bigint))
                   and (cast(:g as bigint) is null or a.cirugia_id = cast(:g as bigint))
                 order by a.subido_en, a.id
                """).param("c", consultaId).param("o", ordenId).param("i", itemId).param("g", cirugiaId)
                .query(MAPPER).list();
    }

    public long insertar(Long pacienteId, Long consultaId, Long ordenId, Long itemId, Long cirugiaId, String tipo,
            String nombreOriginal, String ruta, String almacenamiento, String extension, String contentType,
            long tamano, String hash, String descripcion, String miniaturaRuta, Integer ancho, Integer alto,
            Long usuarioId, Long sedeId) {
        return jdbc.sql("""
                insert into adjunto (paciente_id, consulta_id, orden_examen_id, orden_item_id, cirugia_id, tipo,
                       nombre_original, ruta, almacenamiento_nombre, extension, content_type, tamano_bytes, hash_sha256,
                       descripcion, miniatura_ruta, ancho_px, alto_px, subido_por_id, sede_id)
                values (:p, :c, :o, :i, :g, :tipo, :nombre, :ruta, :alm, :ext, :ct, :tam, :hash, :desc, :min, :an, :al,
                        :u, :sede) returning id
                """).param("p", pacienteId).param("c", consultaId).param("o", ordenId).param("i", itemId)
                .param("g", cirugiaId).param("tipo", tipo).param("nombre", nombreOriginal).param("ruta", ruta)
                .param("alm", almacenamiento).param("ext", extension).param("ct", contentType).param("tam", tamano)
                .param("hash", hash).param("desc", descripcion).param("min", miniaturaRuta).param("an", ancho)
                .param("al", alto).param("u", usuarioId).param("sede", sedeId)
                .query(Long.class).single();
    }

    public int darDeBaja(Long id, Long usuarioId, String motivo) {
        return jdbc.sql("""
                update adjunto set activo = false, eliminado_en = now(), eliminado_por = :u, motivo_eliminacion = :m
                 where id = :id and activo
                """).param("id", id).param("u", usuarioId).param("m", motivo).update();
    }

    public boolean existe(String tabla, Long id, Long pacienteId, String columnaPaciente) {
        // tabla/columna provienen de constantes internas
        return jdbc.sql("select count(*) from " + tabla + " where id = :id and " + columnaPaciente + " = :p")
                .param("id", id).param("p", pacienteId).query(Long.class).single() > 0;
    }

    public boolean itemPerteneceAOrden(Long itemId, Long ordenId) {
        return jdbc.sql("select count(*) from orden_examen_item where id = :i and orden_id = :o")
                .param("i", itemId).param("o", ordenId).query(Long.class).single() > 0;
    }

    public void registrarLectura(Long id, Long usuarioId) {
        jdbc.sql("""
                insert into auditoria (usuario_id, accion, entidad, entidad_id, tipo_evento)
                values (:u, 'LEER_ARCHIVO', 'adjunto', :id, 'LECTURA')
                """).param("u", usuarioId).param("id", id).update();
    }
}
