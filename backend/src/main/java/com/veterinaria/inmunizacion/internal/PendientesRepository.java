package com.veterinaria.inmunizacion.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.inmunizacion.api.dto.PendienteResponse;

/** Consultas SQL de proximas dosis (vacunas y antiparasitarios) vigentes. */
@Repository
public class PendientesRepository {

    /** Ultima aplicacion no anulada por (paciente, producto): solo esa define si hay algo pendiente. */
    private static final String BASE = """
            with ultimas as (
              select * from (
                select distinct on (i.paciente_id, i.vacuna_id)
                       'VACUNA' as tipo, i.id as aplicacion_id, i.paciente_id, i.vacuna_id as producto_id,
                       v.nombre as producto, i.fecha_aplicacion, i.fecha_proxima_dosis as proxima
                  from inmunizacion i join vacuna v on v.id = i.vacuna_id
                 where i.estado = 'APLICADA'
                 order by i.paciente_id, i.vacuna_id, i.fecha_aplicacion desc, i.id desc) a
              union all
              select * from (
                select distinct on (d.paciente_id, d.antiparasitario_id)
                       'ANTIPARASITARIO', d.id, d.paciente_id, d.antiparasitario_id, d.producto,
                       d.fecha_aplicacion, d.proxima_fecha
                  from desparasitacion d
                 where d.estado = 'APLICADA'
                 order by d.paciente_id, d.antiparasitario_id, d.fecha_aplicacion desc, d.id desc) b
            ),
            pend as (
              select u.*, p.nombre as paciente, e.nombre as especie, e.id as especie_id
                from ultimas u
                join paciente p on p.id = u.paciente_id and p.activo and not p.fallecido
                join especie e on e.id = p.especie_id
               where u.proxima is not null
                 and (cast(:desde as date) is null or u.proxima >= cast(:desde as date))
                 and (cast(:hasta as date) is null or u.proxima <= cast(:hasta as date))
                 and (cast(:especie as bigint) is null or e.id = cast(:especie as bigint))
                 and (cast(:tipo as text) is null or u.tipo = cast(:tipo as text))
                 and (cast(:estado as text) is null
                      or (cast(:estado as text) = 'VENCIDA' and u.proxima < cast(:hoy as date))
                      or (cast(:estado as text) = 'POR_VENCER' and u.proxima >= cast(:hoy as date)))
            )
            """;

    private final JdbcClient jdbc;

    public PendientesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long total(LocalDate hoy, LocalDate desde, LocalDate hasta, Long especieId, String tipo, String estado) {
        return jdbc.sql(BASE + "select count(*) from pend")
                .param("hoy", hoy).param("desde", desde).param("hasta", hasta).param("especie", especieId)
                .param("tipo", tipo).param("estado", estado).query(Long.class).single();
    }

    public List<PendienteResponse> pagina(LocalDate hoy, LocalDate desde, LocalDate hasta, Long especieId,
            String tipo, String estado, int offset, int size) {
        return jdbc.sql(BASE + """
                select pend.*, t.id as tutor_id, trim(t.nombres || ' ' || coalesce(t.apellidos, '')) as tutor,
                       t.telefono, t.email, t.acepta_email, t.acepta_whatsapp
                  from pend
                  left join lateral (
                    select t.* from paciente_tutor pt join tutor t on t.id = pt.tutor_id
                     where pt.paciente_id = pend.paciente_id order by pt.es_principal desc, t.id limit 1) t on true
                 order by pend.proxima, pend.paciente, pend.aplicacion_id
                 limit :size offset :offset
                """)
                .param("hoy", hoy).param("desde", desde).param("hasta", hasta).param("especie", especieId)
                .param("tipo", tipo).param("estado", estado).param("size", size).param("offset", offset)
                .query((rs, n) -> {
                    LocalDate proxima = rs.getDate("proxima").toLocalDate();
                    Long tutorId = rs.getObject("tutor_id") == null ? null : rs.getLong("tutor_id");
                    return new PendienteResponse(rs.getString("tipo"), rs.getLong("aplicacion_id"),
                            rs.getLong("paciente_id"), rs.getString("paciente"), rs.getString("especie"),
                            rs.getObject("producto_id") == null ? null : rs.getLong("producto_id"),
                            rs.getString("producto"), rs.getDate("fecha_aplicacion").toLocalDate(), proxima,
                            ChronoUnit.DAYS.between(hoy, proxima), proxima.isBefore(hoy) ? "VENCIDA" : "POR_VENCER",
                            tutorId == null ? null : new PendienteResponse.Tutor(tutorId, rs.getString("tutor"),
                                    rs.getString("telefono"), rs.getString("email"), rs.getBoolean("acepta_email"),
                                    rs.getBoolean("acepta_whatsapp")));
                }).list();
    }
}
