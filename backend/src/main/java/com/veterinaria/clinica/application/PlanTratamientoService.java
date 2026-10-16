package com.veterinaria.clinica.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.ControlSeguimientoCreado;
import com.veterinaria.clinica.api.dto.AddendumRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.CitaSugerida;
import com.veterinaria.clinica.api.dto.PlanDtos.ControlRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.ControlResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanItemRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanItemResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanRequest;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.RecordatorioControl;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.text.Textos;

/**
 * Plan de tratamiento (1 por consulta, con fases/items) y controles de seguimiento.
 *
 * <p>Inmutabilidad: el plan y sus items se editan solo mientras la consulta esta ABIERTA (409 despues; un trigger lo
 * garantiza tambien en BD, V31). Los controles de seguimiento si pueden crearse y actualizarse tras el cierre: cada control
 * creado en una consulta CERRADA deja un addendum SEGUIMIENTO (append-only) que documenta el cambio.
 */
@Service
public class PlanTratamientoService {

    private final JdbcClient jdbc;
    private final ConsultaDetalleService consultas;
    private final ApplicationEventPublisher eventos;

    public PlanTratamientoService(JdbcClient jdbc, ConsultaDetalleService consultas, ApplicationEventPublisher eventos) {
        this.jdbc = jdbc;
        this.consultas = consultas;
        this.eventos = eventos;
    }

    private record ConsultaRef(Long id, String codigo, Long pacienteId, Long veterinarioId, String estado, LocalDate fecha) {
        boolean cerrada() {
            return "CERRADA".equals(estado);
        }
    }

    // ------------------------------------------------------------------ plan

    @Transactional(readOnly = true)
    public PlanResponse obtener(Long consultaId) {
        return buscar(consultaId).orElseThrow(() -> new ResourceNotFoundException("Plan de tratamiento de la consulta", consultaId));
    }

    /** Plan de la consulta (con items y controles) si existe. */
    @Transactional(readOnly = true)
    public Optional<PlanResponse> buscar(Long consultaId) {
        ConsultaRef c = consulta(consultaId);
        return jdbc.sql("""
                select id, paciente_id, objetivo, recomendaciones, advertencias, estado, creado_en, actualizado_en
                  from plan_tratamiento where consulta_id = :c
                """).param("c", consultaId).query((rs, n) -> new PlanResponse(rs.getLong("id"), consultaId,
                rs.getLong("paciente_id"), c.cerrada(), rs.getString("objetivo"), rs.getString("recomendaciones"),
                rs.getString("advertencias"), rs.getString("estado"), items(rs.getLong("id")), controles(consultaId),
                rs.getTimestamp("creado_en").toInstant(), rs.getTimestamp("actualizado_en").toInstant())).optional();
    }

    @Transactional
    public PlanResponse crear(Long consultaId, PlanRequest r) {
        ConsultaRef c = abierta(consultaId);
        if (existe(consultaId)) {
            throw new ConflictException("La consulta " + c.codigo() + " ya tiene plan de tratamiento; edite el existente");
        }
        validarItems(r.items());
        Long id = jdbc.sql("""
                insert into plan_tratamiento (consulta_id, paciente_id, objetivo, recomendaciones, advertencias, estado, creado_por)
                values (:c, :p, :o, :r, :a, :e, :u) returning id
                """).param("c", consultaId).param("p", c.pacienteId()).param("o", Textos.limpiar(r.objetivo()))
                .param("r", Textos.limpiar(r.recomendaciones())).param("a", Textos.limpiar(r.advertencias()))
                .param("e", r.estado() == null ? "ACTIVO" : r.estado()).param("u", UsuarioActual.id().orElse(null))
                .query(Long.class).single();
        reemplazarItems(id, consultaId, r.items());
        // controles creados antes del plan quedan enlazados
        jdbc.sql("update control_seguimiento set plan_id = :p where consulta_id = :c and plan_id is null")
                .param("p", id).param("c", consultaId).update();
        return obtener(consultaId);
    }

    @Transactional
    public PlanResponse actualizar(Long consultaId, PlanRequest r) {
        abierta(consultaId);
        var actual = obtener(consultaId);
        validarItems(r.items());
        jdbc.sql("""
                update plan_tratamiento set objetivo = :o, recomendaciones = :r, advertencias = :a, estado = :e,
                       actualizado_en = now() where id = :id
                """).param("o", Textos.actualizar(actual.objetivo(), r.objetivo()))
                .param("r", Textos.actualizar(actual.recomendaciones(), r.recomendaciones()))
                .param("a", Textos.actualizar(actual.advertencias(), r.advertencias()))
                .param("e", r.estado() == null ? actual.estado() : r.estado()).param("id", actual.id()).update();
        if (r.items() != null) {
            jdbc.sql("delete from plan_tratamiento_item where plan_id = :p").param("p", actual.id()).update();
            reemplazarItems(actual.id(), consultaId, r.items());
        }
        return obtener(consultaId);
    }

    @Transactional
    public void eliminar(Long consultaId) {
        abierta(consultaId);
        var actual = obtener(consultaId);
        jdbc.sql("delete from plan_tratamiento where id = :p").param("p", actual.id()).update();
    }

    private void reemplazarItems(Long planId, Long consultaId, List<PlanItemRequest> items) {
        if (items == null) {
            return;
        }
        int orden = 1;
        for (PlanItemRequest i : items) {
            jdbc.sql("""
                    insert into plan_tratamiento_item (plan_id, consulta_id, orden, fase, tipo, descripcion, fecha_inicio, fecha_fin)
                    values (:p, :c, :o, :f, :t, :d, :fi, :ff)
                    """).param("p", planId).param("c", consultaId).param("o", i.orden() == null ? orden : i.orden())
                    .param("f", Textos.limpiar(i.fase())).param("t", i.tipo() == null ? "OTRO" : i.tipo())
                    .param("d", i.descripcion().trim()).param("fi", i.fechaInicio()).param("ff", i.fechaFin()).update();
            orden++;
        }
    }

    private static void validarItems(List<PlanItemRequest> items) {
        if (items == null) {
            return;
        }
        for (PlanItemRequest i : items) {
            if (i.fechaInicio() != null && i.fechaFin() != null && i.fechaFin().isBefore(i.fechaInicio())) {
                throw new BusinessException("La fecha fin del item '" + i.descripcion() + "' es anterior a la fecha de inicio");
            }
        }
    }

    private List<PlanItemResponse> items(Long planId) {
        return jdbc.sql("""
                select id, orden, fase, tipo, descripcion, fecha_inicio, fecha_fin from plan_tratamiento_item
                 where plan_id = :p order by orden, id
                """).param("p", planId).query((rs, n) -> new PlanItemResponse(rs.getLong("id"), rs.getInt("orden"),
                rs.getString("fase"), rs.getString("tipo"), rs.getString("descripcion"),
                rs.getObject("fecha_inicio", LocalDate.class), rs.getObject("fecha_fin", LocalDate.class))).list();
    }

    private boolean existe(Long consultaId) {
        return jdbc.sql("select count(*) from plan_tratamiento where consulta_id = :c").param("c", consultaId)
                .query(Long.class).single() > 0;
    }

    // ------------------------------------------------------------------ controles

    @Transactional(readOnly = true)
    public List<ControlResponse> controles(Long consultaId) {
        ConsultaRef c = consulta(consultaId);
        return jdbc.sql(CONTROL_SQL + " where consulta_id = :c order by fecha_sugerida, id").param("c", consultaId)
                .query((rs, n) -> mapear(rs, c, null)).list();
    }

    @Transactional
    public ControlResponse crearControl(Long consultaId, ControlRequest r) {
        ConsultaRef c = consulta(consultaId);
        if (r.fechaSugerida() == null) {
            throw new BusinessException("La fecha sugerida del control es obligatoria");
        }
        String motivo = Textos.limpiar(r.motivo());
        if (motivo == null) {
            throw new BusinessException("El motivo del control es obligatorio");
        }
        validarFecha(c, r.fechaSugerida());
        validarCita(r.citaId(), c.pacienteId());
        Long planId = jdbc.sql("select id from plan_tratamiento where consulta_id = :c").param("c", consultaId)
                .query(Long.class).optional().orElse(null);
        Long id = jdbc.sql("""
                insert into control_seguimiento (consulta_id, plan_id, paciente_id, fecha_sugerida, motivo, estado, cita_id, creado_por)
                values (:c, :plan, :p, :f, :m, :e, :cita, :u) returning id
                """).param("c", consultaId).param("plan", planId).param("p", c.pacienteId()).param("f", r.fechaSugerida())
                .param("m", motivo).param("e", r.estado() == null ? "PENDIENTE" : r.estado()).param("cita", r.citaId())
                .param("u", UsuarioActual.id().orElse(null)).query(Long.class).single();
        if (c.cerrada()) {
            consultas.agregarAddendum(consultaId, new AddendumRequest("SEGUIMIENTO",
                    "Control de seguimiento sugerido para el " + r.fechaSugerida() + ": " + motivo));
        }
        RecordatorioControl rec = new RecordatorioControl(false, false, null, null);
        if (Boolean.TRUE.equals(r.generarRecordatorio())) {
            ControlSeguimientoCreado evento = new ControlSeguimientoCreado(id, consultaId, c.pacienteId(), r.fechaSugerida());
            eventos.publishEvent(evento);
            if (evento.recordatorioId() != null) {
                jdbc.sql("update control_seguimiento set recordatorio_id = :r where id = :id")
                        .param("r", evento.recordatorioId()).param("id", id).update();
            }
            rec = new RecordatorioControl(true, evento.recordatorioId() != null, evento.recordatorioId(),
                    evento.detalle() == null ? "Sin respuesta del modulo de comunicaciones" : evento.detalle());
        }
        RecordatorioControl recFinal = rec;
        return jdbc.sql(CONTROL_SQL + " where id = :id").param("id", id).query((rs, n) -> mapear(rs, c, recFinal)).single();
    }

    @Transactional
    public ControlResponse actualizarControl(Long consultaId, Long controlId, ControlRequest r) {
        ConsultaRef c = consulta(consultaId);
        ControlResponse actual = control(consultaId, controlId, c);
        if (List.of("REALIZADO", "CANCELADO").contains(actual.estado())
                && (r.estado() != null && !r.estado().equals(actual.estado()))) {
            throw new ConflictException("El control ya esta " + actual.estado() + " y no admite cambios de estado");
        }
        LocalDate fecha = r.fechaSugerida() == null ? actual.fechaSugerida() : r.fechaSugerida();
        if (r.fechaSugerida() != null) {
            validarFecha(c, fecha);
        }
        Long cita = r.citaId() == null ? actual.citaId() : r.citaId();
        if (r.citaId() != null) {
            validarCita(r.citaId(), c.pacienteId());
        }
        String estado = r.estado() == null ? actual.estado() : r.estado();
        if (r.citaId() != null && "PENDIENTE".equals(estado)) {
            estado = "AGENDADO";
        }
        jdbc.sql("""
                update control_seguimiento set fecha_sugerida = :f, motivo = :m, estado = :e, cita_id = :cita,
                       actualizado_en = now() where id = :id
                """).param("f", fecha).param("m", r.motivo() == null ? actual.motivo() : Textos.limpiar(r.motivo()) == null
                        ? actual.motivo() : Textos.limpiar(r.motivo()))
                .param("e", estado).param("cita", cita).param("id", controlId).update();
        return control(consultaId, controlId, c);
    }

    /** Cancela el control (no se borra). */
    @Transactional
    public ControlResponse cancelarControl(Long consultaId, Long controlId) {
        ConsultaRef c = consulta(consultaId);
        ControlResponse actual = control(consultaId, controlId, c);
        if ("REALIZADO".equals(actual.estado())) {
            throw new ConflictException("El control ya fue REALIZADO y no se puede cancelar");
        }
        jdbc.sql("update control_seguimiento set estado = 'CANCELADO', actualizado_en = now() where id = :id")
                .param("id", controlId).update();
        return control(consultaId, controlId, c);
    }

    private ControlResponse control(Long consultaId, Long controlId, ConsultaRef c) {
        return jdbc.sql(CONTROL_SQL + " where id = :id and consulta_id = :c").param("id", controlId).param("c", consultaId)
                .query((rs, n) -> mapear(rs, c, null)).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Control de seguimiento", controlId));
    }

    private static void validarFecha(ConsultaRef c, LocalDate fecha) {
        if (fecha.isBefore(c.fecha())) {
            throw new BusinessException("La fecha sugerida del control no puede ser anterior a la fecha de la consulta ("
                    + c.fecha() + ")");
        }
    }

    private void validarCita(Long citaId, Long pacienteId) {
        if (citaId == null) {
            return;
        }
        Long paciente = jdbc.sql("select paciente_id from cita where id = :id").param("id", citaId).query(Long.class)
                .optional().orElseThrow(() -> new BusinessException("La cita " + citaId + " no existe"));
        if (!paciente.equals(pacienteId)) {
            throw new BusinessException("La cita " + citaId + " es de otro paciente");
        }
    }

    private static final String CONTROL_SQL = """
            select id, consulta_id, plan_id, paciente_id, fecha_sugerida, motivo, estado, cita_id, creado_en from control_seguimiento
            """;

    private ControlResponse mapear(ResultSet rs, ConsultaRef c, RecordatorioControl rec) throws SQLException {
        String estado = rs.getString("estado");
        LocalDate fecha = rs.getObject("fecha_sugerida", LocalDate.class);
        CitaSugerida sugerida = "PENDIENTE".equals(estado)
                ? new CitaSugerida(rs.getLong("paciente_id"), c.veterinarioId(), fecha, rs.getString("motivo")) : null;
        return new ControlResponse(rs.getLong("id"), rs.getLong("consulta_id"),
                rs.getObject("plan_id") == null ? null : rs.getLong("plan_id"), rs.getLong("paciente_id"), fecha,
                rs.getString("motivo"), estado, rs.getObject("cita_id") == null ? null : rs.getLong("cita_id"),
                rs.getTimestamp("creado_en").toInstant(), sugerida, rec);
    }

    // ------------------------------------------------------------------ comunes

    private ConsultaRef consulta(Long id) {
        return jdbc.sql("select id, codigo, paciente_id, veterinario_id, estado, fecha from consulta where id = :id")
                .param("id", id).query((rs, n) -> new ConsultaRef(rs.getLong("id"), rs.getString("codigo"),
                        rs.getLong("paciente_id"), rs.getLong("veterinario_id"), rs.getString("estado"),
                        rs.getObject("fecha", LocalDate.class))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Consulta", id));
    }

    private ConsultaRef abierta(Long id) {
        ConsultaRef c = consulta(id);
        if (c.cerrada()) {
            throw new ConflictException("La consulta " + c.codigo()
                    + " esta CERRADA y es inmutable; el plan no se edita (registre un addendum; los controles de seguimiento si se pueden agregar)");
        }
        return c;
    }
}
