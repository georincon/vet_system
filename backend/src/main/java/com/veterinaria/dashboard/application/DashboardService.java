package com.veterinaria.dashboard.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.dashboard.api.dto.DashboardResumen;
import com.veterinaria.dashboard.api.dto.DashboardResumen.Persona;
import com.veterinaria.dashboard.api.dto.DashboardResumen.ProximaCita;
import com.veterinaria.shared.time.ZonaClinica;

/** Indicadores agregados calculados con SQL directo (solo lectura). */
@Service
public class DashboardService {

    /** Los tutores activos excluyen al generico "Consumidor final" (V19, NIT 222222222222). */
    private static final int DIAS_VACUNAS = 30;
    private static final int MAX_PROXIMAS = 5;

    private final JdbcClient jdbc;

    public DashboardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResumen resumen() {
        LocalDate hoy = ZonaClinica.hoy();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate limiteVacunas = hoy.plusDays(DIAS_VACUNAS);

        long citasHoy = contar("""
                select count(*) from cita c join estado_cita e on e.id = c.estado_cita_id
                where c.activo and c.fecha = :hoy and e.codigo not in ('CANCELADA','NO_ASISTIO','REAGENDADA')
                """, "hoy", hoy);
        long citasPendientes = contar("""
                select count(*) from cita c join estado_cita e on e.id = c.estado_cita_id
                where c.activo and c.fecha >= :hoy and e.codigo in ('PROGRAMADA','CONFIRMADA')
                """, "hoy", hoy);
        long pacientes = jdbc.sql("select count(*) from paciente where activo and not fallecido")
                .query(Long.class).single();
        long tutores = jdbc.sql("select count(*) from tutor where activo and numero_documento <> '222222222222'").query(Long.class).single();
        long abiertas = jdbc.sql("select count(*) from consulta where activo and estado = 'ABIERTA'")
                .query(Long.class).single();
        long consultasMes = jdbc.sql("""
                select count(*) from consulta where activo and fecha >= :inicio and fecha < :fin
                """).param("inicio", inicioMes).param("fin", inicioMes.plusMonths(1)).query(Long.class).single();
        long vacunas = jdbc.sql("""
                select (select count(*) from inmunizacion i join paciente p on p.id = i.paciente_id
                         where i.estado = 'APLICADA' and p.activo and not p.fallecido
                           and i.fecha_proxima_dosis between :hoy and :limite)
                     + (select count(*) from consulta_vacuna cv join consulta c on c.id = cv.consulta_id
                         where c.activo and cv.proxima_fecha between :hoy and :limite)
                """).param("hoy", hoy).param("limite", limiteVacunas).query(Long.class).single();

        List<ProximaCita> proximas = jdbc.sql("""
                select c.id, c.codigo, c.fecha, c.hora_inicio, c.hora_fin, c.motivo, e.codigo as estado,
                       p.id as paciente_id, p.nombre as paciente, u.id as vet_id, u.nombre as veterinario
                from cita c
                join estado_cita e on e.id = c.estado_cita_id
                join paciente p on p.id = c.paciente_id
                join usuario u on u.id = c.veterinario_id
                where c.activo and c.fecha is not null and c.hora_inicio is not null and e.codigo in ('PROGRAMADA','CONFIRMADA','EN_SALA')
                  and c.fecha_hora_fin >= now()
                order by c.fecha_hora_inicio, c.id
                limit :max
                """).param("max", MAX_PROXIMAS)
                .query((rs, n) -> new ProximaCita(rs.getLong("id"), rs.getString("codigo"),
                        rs.getDate("fecha").toLocalDate(), rs.getTime("hora_inicio").toLocalTime(),
                        rs.getTime("hora_fin") == null ? null : rs.getTime("hora_fin").toLocalTime(),
                        new Persona(rs.getLong("paciente_id"), rs.getString("paciente")),
                        new Persona(rs.getLong("vet_id"), rs.getString("veterinario")),
                        rs.getString("estado"), rs.getString("motivo")))
                .list();

        return new DashboardResumen(citasHoy, citasPendientes, pacientes, tutores, abiertas, consultasMes, vacunas,
                proximas);
    }

    private long contar(String sql, String param, Object valor) {
        return jdbc.sql(sql).param(param, valor).query(Long.class).single();
    }
}
