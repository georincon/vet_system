package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.AbonoAplicado;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.AbonoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.AbonoResponse;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.FacturaResumen;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.PagoRequest;
import com.veterinaria.facturacion.api.dto.FacturacionDtos.TutorResumen;
import com.veterinaria.facturacion.api.dto.ReporteDtos.CarteraDetalle;
import com.veterinaria.facturacion.api.dto.ReporteDtos.CarteraTutor;
import com.veterinaria.facturacion.api.dto.ReporteDtos.EdadCartera;
import com.veterinaria.facturacion.api.dto.ReporteDtos.FacturaCartera;
import com.veterinaria.facturacion.api.dto.ReporteDtos.ReporteCartera;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Cartera por tutor: facturas/POS con saldo (EMITIDA o PARCIAL). Vencimiento = fecha_vencimiento o, si no hay (contado),
 * el dia de emision. Los abonos del tutor se aplican a las facturas mas antiguas primero (o solo a las indicadas).
 */
@Service
public class CarteraService {

    private static final String SALDOS = """
            select f.id, f.numero, f.tipo_documento, f.estado, f.estado_dian, f.fecha, f.total, f.saldo, f.condicion_pago,
                   f.fecha_vencimiento, f.tutor_id, t.nombres || coalesce(' ' || t.apellidos, '') as tutor,
                   t.tipo_documento || ' ' || t.numero_documento as documento,
                   coalesce(f.fecha_vencimiento, (f.fecha at time zone 'America/Bogota')::date) as vence
            from factura f join tutor t on t.id = f.tutor_id
            where f.saldo > 0 and f.tipo_documento in ('FACTURA', 'POS') and f.estado in ('EMITIDA', 'PARCIAL')
            """;

    private final JdbcClient jdbc;
    private final PagoService pagos;
    private final CajaService caja;
    private final FacturaService facturas;

    public CarteraService(JdbcClient jdbc, PagoService pagos, CajaService caja, FacturaService facturas) {
        this.jdbc = jdbc;
        this.pagos = pagos;
        this.caja = caja;
        this.facturas = facturas;
    }

    @Transactional(readOnly = true)
    public PageResponse<CarteraTutor> porTutor(Boolean soloVencidas, Pageable pageable) {
        LocalDate hoy = ZonaClinica.hoy();
        String agrupado = """
                select s.tutor_id, s.tutor, s.documento, count(*) as facturas, sum(s.saldo) as saldo,
                       sum(case when s.vence < :hoy then s.saldo else 0 end) as vencido, min(s.fecha) as mas_antigua,
                       max(case when s.vence < :hoy then cast(:hoy as date) - s.vence else 0 end) as mora
                from (""" + SALDOS + ") s group by s.tutor_id, s.tutor, s.documento"
                + (Boolean.TRUE.equals(soloVencidas) ? " having sum(case when s.vence < :hoy then s.saldo else 0 end) > 0" : "");
        long total = jdbc.sql("select count(*) from (" + agrupado + ") x").param("hoy", hoy).query(Long.class).single();
        List<CarteraTutor> items = jdbc.sql(agrupado + " order by saldo desc, s.tutor_id limit :lim offset :off")
                .param("hoy", hoy).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> new CarteraTutor(rs.getLong("tutor_id"), rs.getString("tutor"), rs.getString("documento"),
                        rs.getLong("facturas"), rs.getBigDecimal("saldo"), rs.getBigDecimal("vencido"),
                        rs.getTimestamp("mas_antigua").toInstant(), rs.getLong("mora"))).list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public CarteraDetalle detalleTutor(Long tutorId) {
        LocalDate hoy = ZonaClinica.hoy();
        TutorResumen tutor = jdbc.sql("select id, nombres || coalesce(' ' || apellidos, '') as nombre, tipo_documento || ' ' || numero_documento as doc from tutor where id = :id")
                .param("id", tutorId).query((rs, n) -> new TutorResumen(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Tutor", tutorId));
        List<FacturaCartera> lista = jdbc.sql(SALDOS + " and f.tutor_id = :t order by f.fecha, f.id").param("t", tutorId)
                .query((rs, n) -> {
                    LocalDate vence = rs.getDate("vence").toLocalDate();
                    FacturaResumen r = new FacturaResumen(rs.getLong("id"), rs.getString("numero"), rs.getString("tipo_documento"),
                            rs.getString("estado"), rs.getString("estado_dian"), tutor, rs.getTimestamp("fecha").toInstant(),
                            rs.getBigDecimal("total"), rs.getBigDecimal("saldo"), rs.getString("condicion_pago"),
                            rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate());
                    return new FacturaCartera(r, vence, vence.isBefore(hoy) ? java.time.temporal.ChronoUnit.DAYS.between(vence, hoy) : 0);
                }).list();
        BigDecimal saldo = lista.stream().map(f -> f.factura().saldo()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vencido = lista.stream().filter(f -> f.diasMora() > 0).map(f -> f.factura().saldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CarteraDetalle(tutor, saldo, vencido, lista);
    }

    /** Reporte de cartera con edades (0, 1-30, 31-60, 61-90, >90 dias de mora) a la fecha de corte (hoy). */
    @Transactional(readOnly = true)
    public ReporteCartera reporte() {
        LocalDate hoy = ZonaClinica.hoy();
        record Fila(BigDecimal saldo, long mora) {
        }
        List<Fila> filas = jdbc.sql("select s.saldo, cast(:hoy as date) - s.vence as mora from (" + SALDOS + ") s")
                .param("hoy", hoy).query((rs, n) -> new Fila(rs.getBigDecimal(1), rs.getLong(2))).list();
        BigDecimal[] e = new BigDecimal[5];
        java.util.Arrays.fill(e, BigDecimal.ZERO);
        for (Fila f : filas) {
            int i = f.mora() <= 0 ? 0 : f.mora() <= 30 ? 1 : f.mora() <= 60 ? 2 : f.mora() <= 90 ? 3 : 4;
            e[i] = e[i].add(f.saldo());
        }
        BigDecimal total = filas.stream().map(Fila::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CarteraTutor> tutores = porTutor(false, org.springframework.data.domain.PageRequest.of(0, 20)).items();
        return new ReporteCartera(hoy, total, filas.size(), new EdadCartera(e[0], e[1], e[2], e[3], e[4]), tutores);
    }

    /** Abono del tutor: aplica cada pago (mixto) a sus facturas con saldo por antiguedad. Exige turno de caja abierto. */
    @Transactional
    public AbonoResponse abonar(Long tutorId, AbonoRequest r) {
        if (jdbc.sql("select count(*) from tutor where id = :id").param("id", tutorId).query(Long.class).single() == 0) {
            throw new ResourceNotFoundException("Tutor", tutorId);
        }
        Long turno = caja.turnoAbiertoDelUsuario();
        List<Long> ids = facturas.idsConSaldo(tutorId, r.facturaIds());
        if (ids.isEmpty()) {
            throw new BusinessException("El tutor no tiene facturas con saldo" + (r.facturaIds() == null ? "" : " entre las indicadas"));
        }
        BigDecimal disponible = ids.stream().map(id -> saldo(id)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = r.pagos().stream().map(PagoRequest::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(disponible) > 0) {
            throw new BusinessException("El abono (" + total + ") excede el saldo pendiente (" + disponible + ")");
        }
        List<AbonoAplicado> aplicado = new ArrayList<>();
        int idx = 0;
        for (PagoRequest p : r.pagos()) {
            BigDecimal restante = p.monto();
            while (restante.signum() > 0) {
                Long facturaId = ids.get(idx);
                BigDecimal saldo = saldo(facturaId);
                if (saldo.signum() == 0) {
                    idx++;
                    continue;
                }
                BigDecimal parte = restante.min(saldo);
                pagos.registrar(facturaId, new PagoRequest(p.metodo(), parte, p.referencia()), turno);
                aplicado.add(new AbonoAplicado(facturaId, numero(facturaId), parte, saldo(facturaId)));
                restante = restante.subtract(parte);
            }
        }
        BigDecimal saldoTutor = jdbc.sql("select coalesce(sum(saldo), 0) from factura where tutor_id = :t and tipo_documento in ('FACTURA', 'POS') and estado in ('EMITIDA', 'PARCIAL')")
                .param("t", tutorId).query(BigDecimal.class).single();
        return new AbonoResponse(tutorId, total, aplicado, saldoTutor);
    }

    private BigDecimal saldo(Long facturaId) {
        return jdbc.sql("select saldo from factura where id = :id").param("id", facturaId).query(BigDecimal.class).single();
    }

    private String numero(Long facturaId) {
        return jdbc.sql("select numero from factura where id = :id").param("id", facturaId).query(String.class).single();
    }
}
