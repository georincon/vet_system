package com.veterinaria.clinica.internal;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.clinica.api.dto.HistoriaItem;

/** Consulta unificada (UNION ALL) de consultas, vacunas, pesos, alergias, laboratorio, imagen, patologia, procedimientos y archivos de un paciente. */
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
              union all
              -- Etapa A: examenes de laboratorio validados (un renglon por examen; referencia = id de la orden)
              select 'LABORATORIO', 2, (i.validado_en at time zone 'America/Bogota')::date, o.id, ec.nombre,
                     'Orden ' || o.codigo || ': '
                       || (select count(*) from resultado_examen r where r.orden_item_id = i.id and r.estado = 'VALIDADO'
                              and r.bandera is not null and r.bandera <> 'N') || ' fuera de rango'
                       || case when exists (select 1 from resultado_examen r where r.orden_item_id = i.id
                                               and r.estado = 'VALIDADO' and r.critico) then ' (valores criticos)' else '' end,
                     'VALIDADO'
                from orden_examen_item i
                join orden_examen o on o.id = i.orden_id
                join examen_catalogo ec on ec.id = i.examen_id
               where o.paciente_id = :paciente and i.estado = 'VALIDADO' and ec.tipo = 'LAB'
              union all
              -- informes de imagen/ecografia/ECG validados
              select 'IMAGEN', 2, coalesce(ii.fecha_estudio, (i.validado_en at time zone 'America/Bogota')::date), o.id,
                     ec.nombre || ' - ' || ii.region, left(ii.impresion_diagnostica, 300), 'VALIDADO'
                from informe_imagen ii
                join orden_examen_item i on i.id = ii.orden_item_id
                join orden_examen o on o.id = i.orden_id
                join examen_catalogo ec on ec.id = i.examen_id
               where o.paciente_id = :paciente and ii.estado = 'VALIDADO'
              union all
              -- informes de patologia/citologia validados
              select 'PATOLOGIA', 2, coalesce(ip.fecha_informe, (i.validado_en at time zone 'America/Bogota')::date), o.id,
                     ec.nombre || ' - ' || ip.tipo_muestra, left(ip.diagnostico_morfologico, 300), 'VALIDADO'
                from informe_patologia ip
                join orden_examen_item i on i.id = ip.orden_item_id
                join orden_examen o on o.id = i.orden_id
                join examen_catalogo ec on ec.id = i.examen_id
               where o.paciente_id = :paciente and ip.estado = 'VALIDADO'
              union all
              -- procedimientos ambulatorios y cirugias realizadas o en curso (referencia = id del registro)
              select cx.tipo, 2, (coalesce(cx.inicio_en, cx.programada_para, cx.creado_en) at time zone 'America/Bogota')::date,
                     cx.id, cx.procedimiento,
                     cx.codigo || coalesce(' - ' || left(cx.resultado, 200), ''), cx.estado
                from cirugia cx
               where cx.paciente_id = :paciente and cx.estado in ('REALIZADA', 'EN_CURSO')
              union all
              -- archivos clinicos sueltos (los de una orden o cirugia se ven dentro de ese evento)
              select 'ARCHIVO', 3, (ad.subido_en at time zone 'America/Bogota')::date, ad.id,
                     coalesce(ad.descripcion, ad.nombre_original), ad.tipo, null
                from adjunto ad
               where ad.paciente_id = :paciente and ad.activo and ad.orden_examen_id is null and ad.cirugia_id is null
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
                where (cast(:tipo as text) is null or tipo = any(string_to_array(cast(:tipo as text), ',')))
                order by fecha desc, orden, ref_id desc
                limit :size offset :offset
                """)
                .param("paciente", pacienteId).param("tipo", tipo).param("size", size).param("offset", offset)
                .query((rs, n) -> new HistoriaItem(rs.getString("tipo"), rs.getDate("fecha").toLocalDate(),
                        rs.getLong("ref_id"), rs.getString("titulo"), rs.getString("detalle"), rs.getString("estado")))
                .list();
    }

    public long total(Long pacienteId, String tipo) {
        return jdbc.sql(EVENTOS + "select count(*) from eventos where (cast(:tipo as text) is null or tipo = any(string_to_array(cast(:tipo as text), ',')))")
                .param("paciente", pacienteId).param("tipo", tipo)
                .query(Long.class).single();
    }
}
