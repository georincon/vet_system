package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.VentasApi;
import com.veterinaria.facturacion.api.dto.ReporteDtos.ReporteVentas;
import com.veterinaria.facturacion.api.dto.ReporteDtos.TotalesVentas;
import com.veterinaria.facturacion.api.dto.ReporteDtos.VentaDia;
import com.veterinaria.facturacion.api.dto.ReporteDtos.VentaItem;
import com.veterinaria.facturacion.api.dto.ReporteDtos.VentaMetodo;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Reporte de ventas: neto = facturas y POS - notas credito, por fecha de emision en la zona de la clinica. Una factura
 * anulada por nota credito aparece en facturado y en notasCredito (neto 0). Los pagos por metodo no restan devoluciones.
 */
@Service
public class ReporteService implements VentasApi {

    private static final int MAX_DIAS = 366;

    private final JdbcClient jdbc;

    public ReporteService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReporteVentas ventas(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new BusinessException("desde y hasta son obligatorios (YYYY-MM-DD)");
        }
        if (hasta.isBefore(desde)) {
            throw new BusinessException("hasta no puede ser anterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS) {
            throw new BusinessException("El rango maximo es de " + MAX_DIAS + " dias");
        }
        var ini = java.sql.Timestamp.from(ZonaClinica.instante(desde, LocalTime.MIN));
        var fin = java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN));
        TotalesVentas totales = jdbc.sql("""
                select count(*) filter (where tipo_documento in ('FACTURA', 'POS')) as docs,
                       coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then -subtotal else subtotal end), 0) as bruto,
                       coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then -descuento else descuento end), 0) as descuentos,
                       coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then -impuestos else impuestos end), 0) as imp,
                       coalesce(sum(case when tipo_documento <> 'NOTA_CREDITO' then total else 0 end), 0) as facturado,
                       coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then total else 0 end), 0) as nc
                from factura where fecha >= :ini and fecha < :fin and tipo_documento in ('FACTURA', 'POS', 'NOTA_CREDITO')
                """).param("ini", ini).param("fin", fin).query((rs, n) -> {
                    BigDecimal facturado = rs.getBigDecimal("facturado");
                    BigDecimal nc = rs.getBigDecimal("nc");
                    return new TotalesVentas(rs.getLong("docs"), rs.getBigDecimal("bruto"), rs.getBigDecimal("descuentos"),
                            rs.getBigDecimal("imp"), facturado, nc, facturado.subtract(nc));
                }).single();
        List<VentaDia> dias = jdbc.sql("""
                select (fecha at time zone 'America/Bogota')::date as dia,
                       count(*) filter (where tipo_documento in ('FACTURA', 'POS')) as facturas,
                       coalesce(sum(case when tipo_documento <> 'NOTA_CREDITO' then total else 0 end), 0) as facturado,
                       coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then total else 0 end), 0) as nc
                from factura where fecha >= :ini and fecha < :fin and tipo_documento in ('FACTURA', 'POS', 'NOTA_CREDITO')
                group by 1 order by 1
                """).param("ini", ini).param("fin", fin)
                .query((rs, n) -> new VentaDia(rs.getDate("dia").toLocalDate(), rs.getLong("facturas"),
                        rs.getBigDecimal("facturado"), rs.getBigDecimal("nc"),
                        rs.getBigDecimal("facturado").subtract(rs.getBigDecimal("nc")))).list();
        List<VentaMetodo> metodos = jdbc.sql("""
                select metodo, count(*) as pagos, sum(monto) as monto from pago where estado = 'APLICADO'
                  and fecha >= :ini and fecha < :fin group by metodo order by monto desc
                """).param("ini", ini).param("fin", fin)
                .query((rs, n) -> new VentaMetodo(rs.getString("metodo"), rs.getLong("pagos"), rs.getBigDecimal("monto"))).list();
        List<VentaItem> items = jdbc.sql("""
                select fi.item_id, i.codigo, coalesce(i.nombre, fi.descripcion) as descripcion,
                       sum(case when f.tipo_documento = 'NOTA_CREDITO' then -fi.cantidad else fi.cantidad end) as cantidad,
                       sum(case when f.tipo_documento = 'NOTA_CREDITO' then -fi.total else fi.total end) as total
                from factura_item fi join factura f on f.id = fi.factura_id left join item i on i.id = fi.item_id
                where f.fecha >= :ini and f.fecha < :fin and f.tipo_documento in ('FACTURA', 'POS', 'NOTA_CREDITO')
                group by fi.item_id, i.codigo, coalesce(i.nombre, fi.descripcion)
                order by total desc, descripcion
                """).param("ini", ini).param("fin", fin)
                .query((rs, n) -> new VentaItem((Long) rs.getObject("item_id"), rs.getString("codigo"),
                        rs.getString("descripcion"), rs.getBigDecimal("cantidad"), rs.getBigDecimal("total"))).list();
        return new ReporteVentas(desde, hasta, totales, dias, metodos, items);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenVentas resumen() {
        LocalDate hoy = ZonaClinica.hoy();
        return new ResumenVentas(neto(hoy, hoy), neto(hoy.withDayOfMonth(1), hoy));
    }

    private BigDecimal neto(LocalDate desde, LocalDate hasta) {
        return jdbc.sql("""
                select coalesce(sum(case when tipo_documento = 'NOTA_CREDITO' then -total else total end), 0) from factura
                where fecha >= :ini and fecha < :fin and tipo_documento in ('FACTURA', 'POS', 'NOTA_CREDITO')
                """).param("ini", java.sql.Timestamp.from(ZonaClinica.instante(desde, LocalTime.MIN)))
                .param("fin", java.sql.Timestamp.from(ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN))).query(BigDecimal.class).single();
    }
}
