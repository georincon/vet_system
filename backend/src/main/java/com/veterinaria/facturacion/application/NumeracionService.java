package com.veterinaria.facturacion.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.CajaDtos.ResolucionRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.ResolucionResponse;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Numeracion por resoluciones ({@code resolucion_facturacion}): el consecutivo se toma con bloqueo de fila dentro de
 * la transaccion que emite el documento (sin saltos si esta se revierte) y respeta rango y vigencia. La Fase 5
 * registra aqui las resoluciones reales de la DIAN; las sembradas (FI, NC, POS) son internas.
 */
@Service
public class NumeracionService {

    public record Numero(Long resolucionId, String prefijo, String numeroResolucion, long consecutivo, String numero) {
    }

    private final JdbcClient jdbc;
    private final SedePrincipal sede;

    public NumeracionService(JdbcClient jdbc, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.sede = sede;
    }

    /** Siguiente numero de la resolucion vigente del tipo; 409 si no hay una con consecutivos disponibles. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Numero siguiente(String tipoDocumento) {
        LocalDate hoy = ZonaClinica.hoy();
        record Res(Long id, String prefijo, String numero) {
        }
        Res r = jdbc.sql("""
                select id, prefijo, numero_resolucion from resolucion_facturacion
                where tipo_documento = :tipo and activo and (sede_id is null or sede_id = :sede)
                  and (vigente_desde is null or vigente_desde <= :hoy) and (vigente_hasta is null or vigente_hasta >= :hoy)
                  and consecutivo_actual < rango_hasta
                order by id desc limit 1 for update
                """).param("tipo", tipoDocumento).param("sede", sede.id()).param("hoy", hoy)
                .query((rs, n) -> new Res(rs.getLong(1), rs.getString(2), rs.getString(3))).optional()
                .orElseThrow(() -> new ConflictException("No hay una resolucion de numeracion vigente con consecutivos "
                        + "disponibles para " + tipoDocumento + ": configure una en /api/facturacion/resoluciones"));
        long consecutivo = jdbc.sql("update resolucion_facturacion set consecutivo_actual = consecutivo_actual + 1 where id = :id returning consecutivo_actual")
                .param("id", r.id()).query(Long.class).single();
        return new Numero(r.id(), r.prefijo(), r.numero(), consecutivo, formato(r.prefijo(), consecutivo));
    }

    static String formato(String prefijo, long consecutivo) {
        return prefijo + "-" + String.format("%06d", consecutivo);
    }

    // ------------------------------------------------------------------ administracion (ADMIN)

    @Transactional(readOnly = true)
    public List<ResolucionResponse> resoluciones(String tipo, Boolean activo) {
        return jdbc.sql("""
                select * from resolucion_facturacion
                where (cast(:tipo as varchar) is null or tipo_documento = :tipo) and (cast(:activo as boolean) is null or activo = :activo)
                order by tipo_documento, id
                """).param("tipo", tipo).param("activo", activo).query((rs, n) -> map(rs)).list();
    }

    @Transactional
    public ResolucionResponse crear(ResolucionRequest r) {
        if (r.rangoHasta() < r.rangoDesde()) {
            throw new BusinessException("rangoHasta debe ser mayor o igual a rangoDesde");
        }
        if (r.vigenteDesde() != null && r.vigenteHasta() != null && r.vigenteHasta().isBefore(r.vigenteDesde())) {
            throw new BusinessException("vigenteHasta no puede ser anterior a vigenteDesde");
        }
        if (jdbc.sql("select count(*) from resolucion_facturacion where numero_resolucion = :n and prefijo = :p")
                .param("n", r.numeroResolucion().trim()).param("p", r.prefijo().trim()).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe la resolucion " + r.numeroResolucion() + " con prefijo " + r.prefijo());
        }
        Long id = jdbc.sql("""
                insert into resolucion_facturacion (sede_id, tipo_documento, prefijo, numero_resolucion, fecha_resolucion,
                       rango_desde, rango_hasta, consecutivo_actual, vigente_desde, vigente_hasta, ambiente, activo)
                values (:sede, :tipo, :pre, :num, :fecha, :desde, :hasta, :actual, :vd, :vh, :amb, :activo) returning id
                """).param("sede", sede.id()).param("tipo", r.tipoDocumento()).param("pre", r.prefijo().trim())
                .param("num", r.numeroResolucion().trim()).param("fecha", r.fechaResolucion()).param("desde", r.rangoDesde())
                .param("hasta", r.rangoHasta()).param("actual", r.rangoDesde() - 1).param("vd", r.vigenteDesde())
                .param("vh", r.vigenteHasta()).param("amb", r.ambiente() == null ? "HABILITACION" : r.ambiente())
                .param("activo", r.activo() == null || r.activo()).query(Long.class).single();
        return obtener(id);
    }

    /** Edicion acotada: vigencia, ambiente, activo, fecha y ampliar rangoHasta; prefijo, tipo y rangoDesde no cambian. */
    @Transactional
    public ResolucionResponse actualizar(Long id, ResolucionRequest r) {
        ResolucionResponse a = obtener(id);
        long hasta = r.rangoHasta() != null ? r.rangoHasta() : a.rangoHasta();
        if (hasta < a.consecutivoActual()) {
            throw new BusinessException("rangoHasta no puede ser menor al consecutivo ya usado (" + a.consecutivoActual() + ")");
        }
        jdbc.sql("""
                update resolucion_facturacion set fecha_resolucion = :fecha, rango_hasta = :hasta, vigente_desde = :vd,
                       vigente_hasta = :vh, ambiente = :amb, activo = :activo where id = :id
                """).param("id", id).param("fecha", r.fechaResolucion() != null ? r.fechaResolucion() : a.fechaResolucion())
                .param("hasta", hasta).param("vd", r.vigenteDesde() != null ? r.vigenteDesde() : a.vigenteDesde())
                .param("vh", r.vigenteHasta() != null ? r.vigenteHasta() : a.vigenteHasta())
                .param("amb", r.ambiente() != null ? r.ambiente() : a.ambiente())
                .param("activo", r.activo() != null ? r.activo() : a.activo()).update();
        return obtener(id);
    }

    @Transactional(readOnly = true)
    public ResolucionResponse obtener(Long id) {
        return jdbc.sql("select * from resolucion_facturacion where id = :id").param("id", id).query((rs, n) -> map(rs))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Resolucion", id));
    }

    private static ResolucionResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long actual = rs.getLong("consecutivo_actual");
        long hasta = rs.getLong("rango_hasta");
        String prefijo = rs.getString("prefijo");
        return new ResolucionResponse(rs.getLong("id"), rs.getString("tipo_documento"), prefijo,
                rs.getString("numero_resolucion"),
                rs.getDate("fecha_resolucion") == null ? null : rs.getDate("fecha_resolucion").toLocalDate(),
                rs.getLong("rango_desde"), hasta, actual, hasta - actual,
                rs.getDate("vigente_desde") == null ? null : rs.getDate("vigente_desde").toLocalDate(),
                rs.getDate("vigente_hasta") == null ? null : rs.getDate("vigente_hasta").toLocalDate(),
                rs.getString("ambiente"), rs.getBoolean("activo"),
                actual < hasta ? formato(prefijo, actual + 1) : null);
    }
}
