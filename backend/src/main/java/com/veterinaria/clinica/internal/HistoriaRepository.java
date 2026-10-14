package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.clinica.api.dto.HistoriaItem;

/** Consulta unificada (UNION ALL) de consultas, vacunas, pesos y alergias de un paciente. */
@Repository
public class HistoriaRepository {

    private static final String EVENTOS = """
            with eventos as (
              select 'CONSULTA' as tipo, 1 as orden, c.fecha as fecha, c.id as ref_id,
                     coalesce(c.motivo, 'Consulta') as titulo,
                     coalesce(c.diagnostico, c.evaluacion) as detalle,
                     c.estado as estado
                from consulta c where c.paciente_id = :paciente and c.activo
              union all
              select 'VACUNA', 2, i.fecha_aplicacion, i.id, v.nombre,
                     case when i.lote is not null then 'Lote ' || i.lote end, i.estado
                from inmunizacion i join vacuna v on v.id = i.vacuna_id
               where i.paciente_id = :paciente and i.estado = 'APLICADA'
              union all
              select 'VACUNA', 2, coalesce(cv.fecha_aplicacion, c.fecha), cv.id, cv.nombre,
                     case when cv.lote is not null then 'Lote ' || cv.lote end, 'APLICADA'
                from consulta_vacuna cv join consulta c on c.id = cv.consulta_id
               where c.paciente_id = :paciente and c.activo
                 and not exists (select 1 from inmunizacion i2 where i2.consulta_vacuna_id = cv.id)
              union all
              select 'DESPARASITACION', 2, d.fecha_aplicacion, d.id, d.producto,
                     case when d.lote is not null then 'Lote ' || d.lote end, d.estado
                from desparasitacion d where d.paciente_id = :paciente and d.estado = 'APLICADA'
              union all
              select 'PESO', 3, pr.fecha, pr.id, 'Peso registrado', pr.peso_kg::text || ' kg', null
                from peso_registro pr where pr.paciente_id = :paciente
              union all
              select 'ALERGIA', 4, coalesce(a.registrada_en, current_date), a.id, a.descripcion,
                     a.severidad || coalesce(' - ' || a.reaccion, ''),
                     case when a.activo then 'ACTIVA' else 'INACTIVA' end
                from alergia a where a.paciente_id = :paciente
            )
            """;

    private final JdbcClient jdbc;

    public HistoriaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Pagina de eventos ordenados por fecha descendente (a igual fecha: consulta, vacuna, peso, alergia). */
    public List<HistoriaItem> pagina(Long pacienteId, String tipo, int offset, int size) {
        return jdbc.sql(EVENTOS + """
                select tipo, fecha, ref_id, titulo, detalle, estado from eventos
                where (cast(:tipo as text) is null or tipo = :tipo)
                order by fecha desc, orden, ref_id desc
                limit :size offset :offset
                """)
                .param("paciente", pacienteId).param("tipo", tipo).param("size", size).param("offset", offset)
                .query((rs, n) -> new HistoriaItem(rs.getString("tipo"), rs.getDate("fecha").toLocalDate(),
                        rs.getLong("ref_id"), rs.getString("titulo"), rs.getString("detalle"), rs.getString("estado")))
                .list();
    }

    public long total(Long pacienteId, String tipo) {
        return jdbc.sql(EVENTOS + "select count(*) from eventos where (cast(:tipo as text) is null or tipo = :tipo)")
                .param("paciente", pacienteId).param("tipo", tipo)
                .query(Long.class).single();
    }
}
