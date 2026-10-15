package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.CajaDtos.AperturaRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.CajaResponse;
import com.veterinaria.facturacion.api.dto.CajaDtos.CierreRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.MovimientoCajaRequest;
import com.veterinaria.facturacion.api.dto.CajaDtos.MovimientoCajaResponse;
import com.veterinaria.facturacion.api.dto.CajaDtos.PorMetodo;
import com.veterinaria.facturacion.api.dto.CajaDtos.ResumenTurno;
import com.veterinaria.facturacion.api.dto.CajaDtos.TurnoResponse;
import com.veterinaria.shared.error.AccesoDenegadoException;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Turnos de caja. Un usuario tiene como maximo un turno ABIERTO y una caja un solo turno abierto. Los pagos se
 * enlazan al turno abierto de quien los registra. Efectivo esperado = apertura + pagos en EFECTIVO + ingresos manuales
 * - egresos - retiros; al cerrar se reporta la diferencia contra el efectivo contado (no bloquea, exige explicacion).
 */
@Service
public class CajaService {

    private final JdbcClient jdbc;
    private final SedePrincipal sede;

    public CajaService(JdbcClient jdbc, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.sede = sede;
    }

    @Transactional(readOnly = true)
    public List<CajaResponse> cajas() {
        return jdbc.sql("""
                select c.id, c.nombre, c.activo, t.id as turno_id, u.nombre as usuario
                from caja c left join caja_turno t on t.caja_id = c.id and t.estado = 'ABIERTO'
                left join usuario u on u.id = t.usuario_id order by c.id
                """).query((rs, n) -> new CajaResponse(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("activo"),
                        (Long) rs.getObject("turno_id"), rs.getString("usuario"))).list();
    }

    /** Turno ABIERTO del usuario actual; 422 si no tiene (exigido para registrar pagos). */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Long turnoAbiertoDelUsuario() {
        Long uid = UsuarioActual.id().orElseThrow(() -> new BusinessException("Usuario no identificado"));
        return jdbc.sql("select id from caja_turno where usuario_id = :u and estado = 'ABIERTO' order by id desc limit 1")
                .param("u", uid).query(Long.class).optional()
                .orElseThrow(() -> new BusinessException("No tiene un turno de caja abierto: abra turno en "
                        + "POST /api/facturacion/caja/turnos/abrir antes de registrar pagos"));
    }

    @Transactional
    public TurnoResponse abrir(AperturaRequest r) {
        Long uid = UsuarioActual.id().orElseThrow(() -> new BusinessException("Usuario no identificado"));
        Long cajaId = r.cajaId() != null ? r.cajaId()
                : jdbc.sql("select id from caja where activo and sede_id = :s order by id limit 1").param("s", sede.id())
                        .query(Long.class).optional().orElseThrow(() -> new BusinessException("No hay cajas activas"));
        if (jdbc.sql("select count(*) from caja where id = :id and activo").param("id", cajaId).query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Caja", cajaId);
        }
        if (jdbc.sql("select count(*) from caja_turno where caja_id = :c and estado = 'ABIERTO'").param("c", cajaId)
                .query(Long.class).single() > 0) {
            throw new ConflictException("La caja ya tiene un turno abierto");
        }
        if (jdbc.sql("select count(*) from caja_turno where usuario_id = :u and estado = 'ABIERTO'").param("u", uid)
                .query(Long.class).single() > 0) {
            throw new ConflictException("Ya tiene un turno de caja abierto: cierrelo antes de abrir otro");
        }
        Long id = jdbc.sql("insert into caja_turno (caja_id, usuario_id, monto_apertura, observaciones) values (:c, :u, :m, :o) returning id")
                .param("c", cajaId).param("u", uid).param("m", r.montoApertura()).param("o", Textos.limpiar(r.observaciones()))
                .query(Long.class).single();
        return turno(id);
    }

    @Transactional(readOnly = true)
    public TurnoResponse actual() {
        Long uid = UsuarioActual.id().orElseThrow(() -> new BusinessException("Usuario no identificado"));
        Long id = jdbc.sql("select id from caja_turno where usuario_id = :u and estado = 'ABIERTO' order by id desc limit 1")
                .param("u", uid).query(Long.class).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caja abierto del usuario", uid));
        return turno(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<TurnoResponse> turnos(Long cajaId, String estado, LocalDate desde, LocalDate hasta, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> p = new java.util.LinkedHashMap<>();
        if (cajaId != null) {
            where.append(" and caja_id = :caja");
            p.put("caja", cajaId);
        }
        if (estado != null) {
            where.append(" and estado = :estado");
            p.put("estado", estado);
        }
        if (desde != null) {
            where.append(" and abierto_en >= :desde");
            p.put("desde", java.sql.Timestamp.from(ZonaClinica.instante(desde, LocalTime.MIN)));
        }
        if (hasta != null) {
            where.append(" and abierto_en < :hasta");
            p.put("hasta", java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN)));
        }
        long total = jdbc.sql("select count(*) from caja_turno" + where).params(p).query(Long.class).single();
        List<Long> ids = jdbc.sql("select id from caja_turno" + where + " order by id desc limit :lim offset :off").params(p)
                .param("lim", pageable.getPageSize()).param("off", pageable.getOffset()).query(Long.class).list();
        return PageResponse.from(new PageImpl<>(ids.stream().map(this::turno).toList(), pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public TurnoResponse turno(Long id) {
        record Cab(Long id, Long cajaId, String caja, Long usuarioId, String usuario, Instant abierto, Instant cerrado,
                String estado, BigDecimal apertura, BigDecimal esperado, BigDecimal contado, BigDecimal diferencia, String obs) {
        }
        Cab c = jdbc.sql("""
                select t.*, c.nombre as caja, u.nombre as usuario from caja_turno t join caja c on c.id = t.caja_id
                join usuario u on u.id = t.usuario_id where t.id = :id
                """).param("id", id).query((rs, n) -> new Cab(rs.getLong("id"), rs.getLong("caja_id"), rs.getString("caja"),
                        rs.getLong("usuario_id"), rs.getString("usuario"), rs.getTimestamp("abierto_en").toInstant(),
                        rs.getTimestamp("cerrado_en") == null ? null : rs.getTimestamp("cerrado_en").toInstant(),
                        rs.getString("estado"), rs.getBigDecimal("monto_apertura"), rs.getBigDecimal("efectivo_esperado"),
                        rs.getBigDecimal("efectivo_contado"), rs.getBigDecimal("diferencia"), rs.getString("observaciones")))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Turno de caja", id));
        ResumenTurno resumen = resumen(id, c.apertura());
        List<MovimientoCajaResponse> movs = jdbc.sql("select * from caja_movimiento where turno_id = :id order by id")
                .param("id", id).query((rs, n) -> new MovimientoCajaResponse(rs.getLong("id"), rs.getString("tipo"),
                        rs.getBigDecimal("monto"), rs.getString("concepto"), (Long) rs.getObject("pago_id"),
                        rs.getTimestamp("ocurrido_en").toInstant(), (Long) rs.getObject("usuario_id"))).list();
        return new TurnoResponse(c.id(), c.cajaId(), c.caja(), c.usuarioId(), c.usuario(), c.abierto(), c.cerrado(),
                c.estado(), c.apertura(), c.esperado() != null ? c.esperado() : resumen.efectivoEsperado(), c.contado(),
                c.diferencia(), c.obs(), resumen, movs);
    }

    private ResumenTurno resumen(Long turnoId, BigDecimal apertura) {
        List<PorMetodo> porMetodo = jdbc.sql("select metodo, sum(monto) as monto, count(*) as n from pago where turno_id = :t and estado = 'APLICADO' group by metodo order by metodo")
                .param("t", turnoId).query((rs, n) -> new PorMetodo(rs.getString("metodo"), rs.getBigDecimal("monto"),
                        rs.getLong("n"))).list();
        BigDecimal efectivo = porMetodo.stream().filter(m -> m.metodo().equals("EFECTIVO")).map(PorMetodo::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cobrado = porMetodo.stream().map(PorMetodo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> manual = new java.util.HashMap<>();
        jdbc.sql("select tipo, sum(monto) as monto from caja_movimiento where turno_id = :t group by tipo").param("t", turnoId)
                .query((rs, n) -> manual.put(rs.getString("tipo"), rs.getBigDecimal("monto"))).list();
        BigDecimal ingresos = manual.getOrDefault("INGRESO", BigDecimal.ZERO);
        BigDecimal egresos = manual.getOrDefault("EGRESO", BigDecimal.ZERO);
        BigDecimal retiros = manual.getOrDefault("RETIRO", BigDecimal.ZERO);
        BigDecimal esperado = apertura.add(efectivo).add(ingresos).subtract(egresos).subtract(retiros);
        return new ResumenTurno(apertura, efectivo, porMetodo, cobrado, ingresos, egresos, retiros, esperado);
    }

    @Transactional
    public TurnoResponse movimiento(Long turnoId, MovimientoCajaRequest r) {
        registrarMovimiento(turnoId, r.tipo(), r.monto(), r.concepto().trim(), null);
        return turno(turnoId);
    }

    /** Egreso de efectivo por devolucion al cliente (nota credito); usa el turno abierto del usuario. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void egresoPorDevolucion(BigDecimal monto, String concepto) {
        registrarMovimiento(turnoAbiertoDelUsuario(), "EGRESO", monto, concepto, null);
    }

    private void registrarMovimiento(Long turnoId, String tipo, BigDecimal monto, String concepto, Long pagoId) {
        Long uid = UsuarioActual.id().orElse(null);
        Map<String, Object> t = jdbc.sql("select estado, usuario_id, monto_apertura from caja_turno where id = :id for update")
                .param("id", turnoId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caja", turnoId));
        if (!"ABIERTO".equals(t.get("estado"))) {
            throw new ConflictException("El turno de caja esta cerrado");
        }
        verificarDueno(((Number) t.get("usuario_id")).longValue());
        if (!tipo.equals("INGRESO")) {
            BigDecimal disponible = resumen(turnoId, (BigDecimal) t.get("monto_apertura")).efectivoEsperado();
            if (monto.compareTo(disponible) > 0) {
                throw new BusinessException("El " + tipo.toLowerCase() + " (" + monto + ") excede el efectivo en caja ("
                        + disponible + ")");
            }
        }
        jdbc.sql("insert into caja_movimiento (turno_id, tipo, monto, concepto, pago_id, usuario_id) values (:t, :tipo, :m, :c, :p, :u)")
                .param("t", turnoId).param("tipo", tipo).param("m", monto).param("c", concepto).param("p", pagoId)
                .param("u", uid).update();
    }

    @Transactional
    public TurnoResponse cerrar(Long turnoId, CierreRequest r) {
        Map<String, Object> t = jdbc.sql("select estado, usuario_id, monto_apertura from caja_turno where id = :id for update")
                .param("id", turnoId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caja", turnoId));
        if (!"ABIERTO".equals(t.get("estado"))) {
            throw new ConflictException("El turno de caja ya esta cerrado");
        }
        verificarDueno(((Number) t.get("usuario_id")).longValue());
        BigDecimal esperado = resumen(turnoId, (BigDecimal) t.get("monto_apertura")).efectivoEsperado();
        BigDecimal diferencia = r.efectivoContado().subtract(esperado);
        String obs = Textos.limpiar(r.observaciones());
        if (diferencia.signum() != 0 && obs == null) {
            throw new BusinessException("El efectivo contado (" + r.efectivoContado() + ") difiere del esperado (" + esperado
                    + "): explique la diferencia en observaciones");
        }
        jdbc.sql("""
                update caja_turno set estado = 'CERRADO', cerrado_en = now(), efectivo_esperado = :e, efectivo_contado = :c,
                       diferencia = :d, observaciones = coalesce(:o, observaciones) where id = :id
                """).param("id", turnoId).param("e", esperado).param("c", r.efectivoContado()).param("d", diferencia)
                .param("o", obs).update();
        return turno(turnoId);
    }

    private void verificarDueno(long duenoId) {
        Long uid = UsuarioActual.id().orElse(null);
        boolean admin = "ADMIN".equals(UsuarioActual.rol().orElse(""));
        if (!admin && (uid == null || uid != duenoId)) {
            throw new AccesoDenegadoException("El turno de caja pertenece a otro usuario");
        }
    }
}
