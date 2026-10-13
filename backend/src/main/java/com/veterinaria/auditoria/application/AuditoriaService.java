package com.veterinaria.auditoria.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.auditoria.api.dto.AuditoriaResponse;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/** Consulta de la bitacora {@code auditoria} (solo lectura). */
@Service
public class AuditoriaService {

    private static final String FILTROS = """
            from auditoria a left join usuario u on u.id = a.usuario_id
            where (cast(:entidad as text) is null or lower(a.entidad) = lower(cast(:entidad as text)))
              and (cast(:entidadId as bigint) is null or a.entidad_id = cast(:entidadId as bigint))
              and (cast(:usuarioId as bigint) is null or a.usuario_id = cast(:usuarioId as bigint))
              and (cast(:desde as timestamptz) is null or a.ocurrido_en >= cast(:desde as timestamptz))
              and (cast(:hasta as timestamptz) is null or a.ocurrido_en < cast(:hasta as timestamptz))
            """;

    private final JdbcClient jdbc;

    public AuditoriaService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code hasta} es inclusivo (dia completo en hora de la clinica). */
    @Transactional(readOnly = true)
    public PageResponse<AuditoriaResponse> buscar(String entidad, Long entidadId, Long usuarioId, LocalDate desde,
            LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new BusinessException("'hasta' no puede ser anterior a 'desde'");
        }
        OffsetDateTime d = desde == null ? null
                : ZonaClinica.instante(desde, LocalTime.MIN).atOffset(ZoneOffset.UTC);
        OffsetDateTime h = hasta == null ? null
                : ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN).atOffset(ZoneOffset.UTC);
        String e = entidad == null || entidad.isBlank() ? null : entidad.trim();

        long total = jdbc.sql("select count(*) " + FILTROS)
                .param("entidad", e).param("entidadId", entidadId).param("usuarioId", usuarioId)
                .param("desde", d).param("hasta", h).query(Long.class).single();
        List<AuditoriaResponse> items = jdbc.sql("""
                select a.id, a.usuario_id, u.username, a.accion, a.entidad, a.entidad_id, a.detalle::text as detalle,
                       a.ip, a.ocurrido_en
                """ + FILTROS + " order by a.ocurrido_en desc, a.id desc limit :size offset :offset")
                .param("entidad", e).param("entidadId", entidadId).param("usuarioId", usuarioId)
                .param("desde", d).param("hasta", h)
                .param("size", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, n) -> new AuditoriaResponse(rs.getLong("id"), (Long) rs.getObject("usuario_id"),
                        rs.getString("username"), rs.getString("accion"), rs.getString("entidad"),
                        (Long) rs.getObject("entidad_id"), rs.getString("detalle"), rs.getString("ip"),
                        rs.getObject("ocurrido_en", OffsetDateTime.class).toInstant()))
                .list();
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }
}
