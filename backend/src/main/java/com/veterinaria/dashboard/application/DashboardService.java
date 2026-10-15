package com.veterinaria.dashboard.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.dashboard.api.dto.DashboardResumen;
import com.veterinaria.dashboard.api.dto.DashboardResumen.Persona;
import com.veterinaria.dashboard.api.dto.DashboardResumen.ProximaCita;
import com.veterinaria.facturacion.VentasApi;
import com.veterinaria.inmunizacion.application.PendientesService;
import com.veterinaria.inventario.StockApi;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.time.ZonaClinica;

/** Indicadores agregados calculados con SQL directo (solo lectura). */
@Service
public class DashboardService {

    /** Los tutores activos excluyen al generico "Consumidor final" (V19, NIT 222222222222). */
    private static final int DIAS_VACUNAS = 30;
    private static final int MAX_PROXIMAS = 5;

    private final JdbcClient jdbc;
    private final PendientesService pendientes;
    private final VentasApi ventas;
    private final StockApi stock;

    public DashboardService(JdbcClient jdbc, PendientesService pendientes, VentasApi ventas, StockApi stock) {
        this.jdbc = jdbc;
        this.pendientes = pendientes;
        this.ventas = ventas;
        this.stock = stock;
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
        // Proximas dosis reales (ultima aplicacion vigente por paciente/producto, modulo inmunizacion) mas las
        // vacunas anotadas en consultas todavia ABIERTAS (al cerrar pasan a inmunizacion y no se cuentan dos veces).
        long vacunas = pendientes.contarPorVencer(DIAS_VACUNAS) + jdbc.sql("""
                select count(*) from consulta_vacuna cv join consulta c on c.id = cv.consulta_id
                 where c.activo and c.estado = 'ABIERTA' and cv.proxima_fecha between :hoy and :limite
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

        // Ventas solo para quien opera caja/facturacion; el inventario es de lectura para todos los roles.
        String rol = UsuarioActual.rol().orElse("");
        boolean veVentas = rol.equals("ADMIN") || rol.equals("RECEPCION");
        VentasApi.ResumenVentas v = veVentas ? ventas.resumen() : null;
        return new DashboardResumen(citasHoy, citasPendientes, pacientes, tutores, abiertas, consultasMes, vacunas,
                proximas, v == null ? null : v.hoy(), v == null ? null : v.mes(), stock.contarItemsBajoMinimo(),
                stock.contarLotesPorVencer(DIAS_VACUNAS));
    }

    private long contar(String sql, String param, Object valor) {
        return jdbc.sql(sql).param(param, valor).query(Long.class).single();
    }
}
