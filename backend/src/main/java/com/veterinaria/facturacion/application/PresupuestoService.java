package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.LineaResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PresupuestoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PresupuestoResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.TutorResumen;
import com.veterinaria.facturacion.domain.Importes;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.web.PageResponse;

/**
 * Presupuestos: BORRADOR -> ENVIADO -> ACEPTADO | RECHAZADO | VENCIDO; al facturarlos pasan a FACTURADO. Los generados
 * al cerrar una consulta tienen origen CONSULTA. Un VETERINARIO solo ve los de sus propias consultas (404 si no).
 */
@Service
public class PresupuestoService {

    private final JdbcClient jdbc;
    private final LineaResolver resolver;
    private final SedePrincipal sede;

    public PresupuestoService(JdbcClient jdbc, LineaResolver resolver, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.resolver = resolver;
        this.sede = sede;
    }

    @Transactional
    public PresupuestoResponse crear(PresupuestoRequest r) {
        validarContexto(r.tutorId(), r.pacienteId(), r.consultaId());
        List<LineaResuelta> lineas = resolver.resolver(r.items(), r.pacienteId(), r.consultaId());
        Long id = jdbc.sql("""
                insert into presupuesto (codigo, tutor_id, paciente_id, consulta_id, estado, notas, sede_id, vigente_hasta,
                                         origen, creado_por)
                values ('PRE-' || lpad(nextval('seq_presupuesto')::text, 6, '0'), :tutor, :pac, :consulta, 'BORRADOR', :notas,
                        :sede, :vigente, 'MANUAL', :uid) returning id
                """).param("tutor", r.tutorId()).param("pac", r.pacienteId()).param("consulta", r.consultaId())
                .param("notas", Textos.limpiar(r.notas())).param("sede", sede.id()).param("vigente", r.vigenteHasta())
                .param("uid", UsuarioActual.id().orElse(null)).query(Long.class).single();
        guardarLineas(id, lineas);
        return obtener(id);
    }

    @Transactional
    public PresupuestoResponse actualizar(Long id, PresupuestoRequest r) {
        PresupuestoResponse a = obtenerSinFiltro(id);
        if (!a.estado().equals("BORRADOR")) {
            throw new ConflictException("Solo se edita un presupuesto en BORRADOR (estado actual: " + a.estado() + ")");
        }
        Long tutor = r.tutorId() != null ? r.tutorId() : a.tutor().id();
        Long paciente = r.pacienteId() != null ? r.pacienteId() : a.pacienteId();
        Long consulta = r.consultaId() != null ? r.consultaId() : a.consultaId();
        validarContexto(tutor, paciente, consulta);
        jdbc.sql("update presupuesto set tutor_id = :t, paciente_id = :p, consulta_id = :c, notas = :n, vigente_hasta = :v, actualizado_en = now() where id = :id")
                .param("id", id).param("t", tutor).param("p", paciente).param("c", consulta)
                .param("n", Textos.actualizar(a.notas(), r.notas()))
                .param("v", r.vigenteHasta() != null ? r.vigenteHasta() : a.vigenteHasta()).update();
        if (r.items() != null) {
            List<LineaResuelta> lineas = resolver.resolver(r.items(), paciente, consulta);
            jdbc.sql("delete from presupuesto_item where presupuesto_id = :id").param("id", id).update();
            guardarLineas(id, lineas);
        }
        return obtener(id);
    }

    @Transactional
    public PresupuestoResponse cambiarEstado(Long id, String estado) {
        PresupuestoResponse a = obtenerSinFiltro(id);
        boolean valido = switch (estado) {
            case "ENVIADO" -> a.estado().equals("BORRADOR");
            case "ACEPTADO", "RECHAZADO" -> a.estado().equals("BORRADOR") || a.estado().equals("ENVIADO");
            default -> false;
        };
        if (!valido) {
            throw new ConflictException("No se puede pasar el presupuesto de " + a.estado() + " a " + estado);
        }
        if (a.items().isEmpty()) {
            throw new BusinessException("El presupuesto no tiene renglones");
        }
        jdbc.sql("update presupuesto set estado = :e, actualizado_en = now() where id = :id").param("e", estado)
                .param("id", id).update();
        return obtener(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PresupuestoResponse> listar(Long tutorId, Long pacienteId, Long consultaId, String estado,
            Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> p = new java.util.LinkedHashMap<>();
        if (tutorId != null) {
            where.append(" and p.tutor_id = :tutor");
            p.put("tutor", tutorId);
        }
        if (pacienteId != null) {
            where.append(" and p.paciente_id = :pac");
            p.put("pac", pacienteId);
        }
        if (consultaId != null) {
            where.append(" and p.consulta_id = :consulta");
            p.put("consulta", consultaId);
        }
        if (estado != null) {
            where.append(" and p.estado = :estado");
            p.put("estado", estado);
        }
        if (soloPropiosDelVeterinario()) {
            where.append(" and exists (select 1 from consulta c where c.id = p.consulta_id and c.veterinario_id = :vet)");
            p.put("vet", UsuarioActual.id().orElse(-1L));
        }
        long total = jdbc.sql("select count(*) from presupuesto p" + where).params(p).query(Long.class).single();
        List<Long> ids = jdbc.sql("select p.id from presupuesto p" + where + " order by p.id desc limit :lim offset :off")
                .params(p).param("lim", pageable.getPageSize()).param("off", pageable.getOffset()).query(Long.class).list();
        return PageResponse.from(new PageImpl<>(ids.stream().map(this::obtenerSinFiltro).toList(), pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public PresupuestoResponse obtener(Long id) {
        PresupuestoResponse p = obtenerSinFiltro(id);
        if (soloPropiosDelVeterinario()) {
            boolean propio = p.consultaId() != null && jdbc.sql("select count(*) from consulta where id = :c and veterinario_id = :v")
                    .param("c", p.consultaId()).param("v", UsuarioActual.id().orElse(-1L)).query(Long.class).single() > 0;
            if (!propio) {
                throw new ResourceNotFoundException("Presupuesto", id);
            }
        }
        return p;
    }

    private static boolean soloPropiosDelVeterinario() {
        return "VETERINARIO".equals(UsuarioActual.rol().orElse(""));
    }

    private PresupuestoResponse obtenerSinFiltro(Long id) {
        record Cab(Long id, String codigo, Long tutorId, String tutor, String doc, Long pacienteId, String paciente,
                Long consultaId, Instant fecha, String estado, String origen, LocalDate vigente, String notas) {
        }
        Cab c = jdbc.sql("""
                select p.*, t.nombres || coalesce(' ' || t.apellidos, '') as tutor, t.tipo_documento || ' ' || t.numero_documento as doc,
                       pa.nombre as paciente
                from presupuesto p join tutor t on t.id = p.tutor_id left join paciente pa on pa.id = p.paciente_id
                where p.id = :id
                """).param("id", id).query((rs, n) -> new Cab(rs.getLong("id"), rs.getString("codigo"), rs.getLong("tutor_id"),
                        rs.getString("tutor"), rs.getString("doc"), (Long) rs.getObject("paciente_id"), rs.getString("paciente"),
                        (Long) rs.getObject("consulta_id"), rs.getTimestamp("fecha").toInstant(), rs.getString("estado"),
                        rs.getString("origen"),
                        rs.getDate("vigente_hasta") == null ? null : rs.getDate("vigente_hasta").toLocalDate(),
                        rs.getString("notas"))).optional().orElseThrow(() -> new ResourceNotFoundException("Presupuesto", id));
        List<LineaResponse> items = jdbc.sql("""
                select pi.*, i.codigo from presupuesto_item pi left join item i on i.id = pi.item_id
                where pi.presupuesto_id = :id order by pi.id
                """).param("id", id).query((rs, n) -> {
                    BigDecimal bruto = Importes.dinero(rs.getBigDecimal("cantidad").multiply(rs.getBigDecimal("precio_unitario")));
                    BigDecimal base = bruto.subtract(rs.getBigDecimal("descuento"));
                    return new LineaResponse(rs.getLong("id"), (Long) rs.getObject("item_id"), rs.getString("codigo"),
                            rs.getString("descripcion"), rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario"),
                            rs.getBigDecimal("impuesto_pct"), rs.getBigDecimal("descuento"), base,
                            rs.getBigDecimal("total").subtract(base), rs.getBigDecimal("total"), c.consultaId(),
                            c.pacienteId(), null, null, null, null);
                }).list();
        BigDecimal subtotal = items.stream().map(l -> l.base().add(l.descuento())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = items.stream().map(LineaResponse::descuento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impuestos = items.stream().map(LineaResponse::impuesto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = items.stream().map(LineaResponse::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long facturaId = jdbc.sql("select id from factura where presupuesto_id = :id and tipo_documento in ('FACTURA', 'POS') order by id limit 1")
                .param("id", id).query(Long.class).optional().orElse(null);
        return new PresupuestoResponse(c.id(), c.codigo(), new TutorResumen(c.tutorId(), c.tutor(), c.doc()), c.pacienteId(),
                c.paciente(), c.consultaId(), c.fecha(), c.estado(), c.origen(), c.vigente(), subtotal, descuento, impuestos,
                total, c.notas(), facturaId, items);
    }

    private void validarContexto(Long tutorId, Long pacienteId, Long consultaId) {
        if (tutorId == null || jdbc.sql("select count(*) from tutor where id = :id and activo").param("id", tutorId)
                .query(Long.class).single() == 0) {
            throw new BusinessException("El tutor no existe o esta inactivo");
        }
        if (pacienteId != null && jdbc.sql("select count(*) from paciente where id = :id").param("id", pacienteId)
                .query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Paciente", pacienteId);
        }
        if (consultaId != null && jdbc.sql("select count(*) from consulta where id = :id").param("id", consultaId)
                .query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Consulta", consultaId);
        }
    }

    private void guardarLineas(Long presupuestoId, List<LineaResuelta> lineas) {
        for (LineaResuelta l : lineas) {
            insertarLinea(presupuestoId, l);
        }
        recalcularTotal(presupuestoId);
    }

    /** Inserta un renglon; con origen definido es idempotente (no duplica el mismo origen). @return true si se inserto. */
    boolean insertarLinea(Long presupuestoId, LineaResuelta l) {
        return jdbc.sql("""
                insert into presupuesto_item (presupuesto_id, item_id, descripcion, cantidad, precio_unitario, impuesto_pct,
                                              descuento, total, origen_tabla, origen_id)
                values (:p, :i, :d, :q, :pr, :pct, :desc, :t, :ot, :oi)
                on conflict (presupuesto_id, origen_tabla, origen_id) where origen_tabla is not null do nothing
                """).param("p", presupuestoId).param("i", l.itemId()).param("d", l.descripcion()).param("q", l.cantidad())
                .param("pr", l.precio()).param("pct", l.impuestoPct()).param("desc", l.descuento())
                .param("t", l.calc().total()).param("ot", l.origenTabla()).param("oi", l.origenId()).update() > 0;
    }

    void recalcularTotal(Long presupuestoId) {
        jdbc.sql("update presupuesto set total = coalesce((select sum(total) from presupuesto_item where presupuesto_id = :id), 0), actualizado_en = now() where id = :id")
                .param("id", presupuestoId).update();
    }
}
