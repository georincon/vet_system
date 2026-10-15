package com.veterinaria.comunicaciones.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.comunicaciones.api.dto.OptOutRequest;
import com.veterinaria.comunicaciones.api.dto.OptOutResponse;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;

/** Bajas de comunicaciones: manuales (recepcion) y por enlace firmado (HMAC) incluido en los correos. */
@Service
public class OptOutService {

    private final JdbcClient jdbc;
    private final SecretKey clave;

    public OptOutService(JdbcClient jdbc, SecretKey jwtSecretKey) {
        this.jdbc = jdbc;
        this.clave = new SecretKeySpec(jwtSecretKey.getEncoded(), "HmacSHA256");
    }

    /** Token sin vencimiento: {@code base64url(tutorId:canal).base64url(hmac)}. */
    public String token(Long tutorId, String canal) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((tutorId + ":" + canal).getBytes(StandardCharsets.UTF_8));
        return payload + "." + firmar(payload);
    }

    /** Registra la baja del token (idempotente) y omite los recordatorios pendientes; vacio si el token es invalido. */
    @Transactional
    public Optional<Long> darDeBajaPorToken(String token) {
        if (token == null || !token.contains(".")) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.", 2);
        if (!MessageDigest.isEqual(firmar(partes[0]).getBytes(StandardCharsets.UTF_8),
                partes[1].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String[] datos = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8).split(":", 2);
            Long tutorId = Long.valueOf(datos[0]);
            String canal = datos[1];
            Integer existe = jdbc.sql("select count(*)::int from tutor where id = :id").param("id", tutorId)
                    .query(Integer.class).single();
            if (existe == 0) {
                return Optional.empty();
            }
            insertar(tutorId, canal, "TODAS", "Baja por enlace del correo");
            omitirPendientes(tutorId, canal, "TODAS");
            return Optional.of(tutorId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<OptOutResponse> listar(Long tutorId) {
        return jdbc.sql("""
                select id, tutor_id, canal, categoria, desde, motivo from comunicacion_optout
                where (cast(:tutor as bigint) is null or tutor_id = cast(:tutor as bigint)) order by id desc
                """).param("tutor", tutorId).query(OptOutService::mapear).list();
    }

    @Transactional
    public OptOutResponse registrar(OptOutRequest r) {
        Integer existe = jdbc.sql("select count(*)::int from tutor where id = :id").param("id", r.tutorId())
                .query(Integer.class).single();
        if (existe == 0) {
            throw new ResourceNotFoundException("Tutor", r.tutorId());
        }
        String canal = r.canal() == null ? "TODOS" : r.canal();
        String categoria = r.categoria() == null ? "TODAS" : r.categoria();
        insertar(r.tutorId(), canal, categoria, Textos.limpiar(r.motivo()));
        omitirPendientes(r.tutorId(), canal, categoria);
        return jdbc.sql("select id, tutor_id, canal, categoria, desde, motivo from comunicacion_optout "
                + "where tutor_id = :t and canal = :c and categoria = :k")
                .param("t", r.tutorId()).param("c", canal).param("k", categoria)
                .query(OptOutService::mapear).single();
    }

    /** Levanta la baja (el tutor vuelve a poder recibir mensajes). */
    @Transactional
    public void eliminar(Long id) {
        if (jdbc.sql("delete from comunicacion_optout where id = :id").param("id", id).update() == 0) {
            throw new ResourceNotFoundException("Baja de comunicaciones", id);
        }
    }

    private void insertar(Long tutorId, String canal, String categoria, String motivo) {
        jdbc.sql("""
                insert into comunicacion_optout (tutor_id, canal, categoria, motivo)
                values (:t, :c, :k, :m) on conflict (tutor_id, canal, categoria) do nothing
                """).param("t", tutorId).param("c", canal).param("k", categoria).param("m", motivo).update();
    }

    /** Los recordatorios son de categoria SERVICIO: una baja solo de MARKETING no los afecta. */
    private void omitirPendientes(Long tutorId, String canal, String categoria) {
        if ("MARKETING".equals(categoria)) {
            return;
        }
        jdbc.sql("""
                update recordatorio set estado = 'OMITIDO_OPTOUT'
                 where tutor_id = :t and estado = 'PENDIENTE' and (:c = 'TODOS' or canal = :c)
                """).param("t", tutorId).param("c", canal).update();
    }

    private static OptOutResponse mapear(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new OptOutResponse(rs.getLong("id"), rs.getLong("tutor_id"), rs.getString("canal"),
                rs.getString("categoria"), rs.getTimestamp("desde").toInstant(), rs.getString("motivo"));
    }

    private String firmar(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("optout:" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC no disponible", e);
        }
    }
}
