package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.api.dto.StockDtos.AlertaLote;
import com.veterinaria.inventario.api.dto.StockDtos.AlertaStock;
import com.veterinaria.inventario.api.dto.StockDtos.AlertasResponse;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.time.ZonaClinica;

/** Alertas: items bajo el minimo, lotes por vencer en N dias y lotes vencidos con existencias. */
@Service
public class AlertaService {

    private static final int DIAS_POR_DEFECTO = 30;
    private static final int DIAS_MAX = 730;

    private final JdbcClient jdbc;

    public AlertaService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public AlertasResponse alertas(Integer diasParam) {
        int dias = diasParam == null ? DIAS_POR_DEFECTO : diasParam;
        if (dias < 0 || dias > DIAS_MAX) {
            throw new BusinessException("dias debe estar entre 0 y " + DIAS_MAX);
        }
        LocalDate hoy = ZonaClinica.hoy();
        List<AlertaStock> bajo = jdbc.sql("""
                select * from (
                  select i.id, i.codigo, i.nombre, i.stock_minimo,
                         coalesce((select sum(l.cantidad_actual) from lote l where l.item_id = i.id and l.estado = 'DISPONIBLE'
                                   and (l.fecha_vencimiento is null or l.fecha_vencimiento >= :hoy)), 0) as stock
                  from item i where i.activo and i.controla_stock and i.stock_minimo > 0) t
                where t.stock < t.stock_minimo order by (t.stock_minimo - t.stock) desc, t.nombre
                """).param("hoy", hoy)
                .query((rs, n) -> new AlertaStock(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getBigDecimal("stock"), rs.getBigDecimal("stock_minimo"),
                        rs.getBigDecimal("stock_minimo").subtract(rs.getBigDecimal("stock")))).list();
        String base = """
                select l.id, l.item_id, i.codigo, i.nombre, b.nombre as bodega, l.numero_lote, l.fecha_vencimiento,
                       l.cantidad_actual, l.estado
                from lote l join item i on i.id = l.item_id join bodega b on b.id = l.bodega_id
                where l.cantidad_actual > 0 and l.fecha_vencimiento is not null
                """;
        List<AlertaLote> porVencer = jdbc.sql(base + " and l.estado = 'DISPONIBLE' and l.fecha_vencimiento between :hoy and :limite order by l.fecha_vencimiento, l.id")
                .param("hoy", hoy).param("limite", hoy.plusDays(dias)).query((rs, n) -> aLote(rs, hoy)).list();
        List<AlertaLote> vencidos = jdbc.sql(base + " and l.fecha_vencimiento < :hoy order by l.fecha_vencimiento, l.id")
                .param("hoy", hoy).query((rs, n) -> aLote(rs, hoy)).list();
        return new AlertasResponse(dias, new AlertasResponse.Resumen(bajo.size(), porVencer.size(), vencidos.size()),
                bajo, porVencer, vencidos);
    }

    private static AlertaLote aLote(java.sql.ResultSet rs, LocalDate hoy) throws java.sql.SQLException {
        LocalDate venc = rs.getDate("fecha_vencimiento").toLocalDate();
        BigDecimal cantidad = rs.getBigDecimal("cantidad_actual");
        return new AlertaLote(rs.getLong("id"), rs.getLong("item_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("bodega"), rs.getString("numero_lote"), venc, ChronoUnit.DAYS.between(hoy, venc), cantidad,
                rs.getString("estado"));
    }
}
