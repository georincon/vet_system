package com.veterinaria.compartir.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Descarga publica de documentos por enlace seguro (token aleatorio; solo se guarda su hash SHA-256). */
@Service
public class EnlaceService {

    public enum Estado { OK, NO_ENCONTRADO, REVOCADO, EXPIRADO, LIMITE }

    public record Descarga(Estado estado, String nombre, byte[] pdf) {
    }

    private record Fila(Long id, Instant expira, int max, int descargas, boolean revocado, String nombre, byte[] pdf) {
    }

    private final JdbcClient jdbc;

    public EnlaceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Valida y, si procede, cuenta la descarga. Todo intento (incluso rechazado) queda en {@code compartir_acceso}. */
    @Transactional
    public Descarga descargar(String token, String ip, String userAgent) {
        String hash = CompartirService.sha256((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
        Fila f = jdbc.sql("""
                select l.id, l.expira_en, l.max_descargas, l.descargas, l.revocado_en is not null as revocado,
                       d.nombre_archivo, d.contenido
                  from compartir_enlace l join compartir_envio_documento d on d.id = l.envio_documento_id
                 where l.token_hash = :h for update of l
                """).param("h", hash).query((rs, n) -> new Fila(rs.getLong("id"), rs.getTimestamp("expira_en").toInstant(),
                rs.getInt("max_descargas"), rs.getInt("descargas"), rs.getBoolean("revocado"), rs.getString("nombre_archivo"),
                rs.getBytes("contenido"))).optional().orElse(null);
        if (f == null) {
            registrar(null, Estado.NO_ENCONTRADO, ip, userAgent);
            return new Descarga(Estado.NO_ENCONTRADO, null, null);
        }
        Estado e = f.revocado() ? Estado.REVOCADO : f.expira().isBefore(Instant.now()) ? Estado.EXPIRADO
                : f.descargas() >= f.max() ? Estado.LIMITE : Estado.OK;
        registrar(f.id(), e, ip, userAgent);
        if (e != Estado.OK) {
            return new Descarga(e, null, null);
        }
        jdbc.sql("update compartir_enlace set descargas = descargas + 1, ultimo_acceso = now() where id = :id")
                .param("id", f.id()).update();
        jdbc.sql("""
                insert into auditoria (usuario_id, accion, entidad, entidad_id, tipo_evento, ip)
                values (null, 'DESCARGA_PUBLICA_DOCUMENTO', 'compartir_enlace', :id, 'LECTURA', :ip)
                """).param("id", f.id()).param("ip", ip == null ? null : ip.length() > 45 ? ip.substring(0, 45) : ip).update();
        return new Descarga(Estado.OK, f.nombre(), f.pdf());
    }

    private void registrar(Long enlaceId, Estado estado, String ip, String ua) {
        jdbc.sql("insert into compartir_acceso (enlace_id, resultado, ip, user_agent) values (:e, :r, :ip, :ua)")
                .param("e", enlaceId).param("r", estado.name())
                .param("ip", ip == null ? null : ip.length() > 45 ? ip.substring(0, 45) : ip)
                .param("ua", ua == null ? null : ua.length() > 255 ? ua.substring(0, 255) : ua).update();
    }
}
