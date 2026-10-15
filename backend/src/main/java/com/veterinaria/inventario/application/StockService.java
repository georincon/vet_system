package com.veterinaria.inventario.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.StockApi;
import com.veterinaria.inventario.domain.TipoMovimiento;
import com.veterinaria.inventario.internal.StockRepository;
import com.veterinaria.inventario.internal.StockRepository.LoteRow;
import com.veterinaria.inventario.internal.StockRepository.Mov;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Implementacion de {@link StockApi}: descuento FEFO, salidas/devoluciones de lote y consultas para alertas.
 * Reglas: el stock nunca queda negativo (409), no se usan lotes vencidos ni en CUARENTENA/RETIRADO/VENCIDO.
 */
@Service
public class StockService implements StockApi {

    private final StockRepository stock;
    private final JdbcClient jdbc;

    public StockService(StockRepository stock, JdbcClient jdbc) {
        this.stock = stock;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoteInfo> bloquearLote(Long loteId) {
        return stock.bloquear(loteId).map(l -> new LoteInfo(l.id(), l.itemId(), l.numeroLote(), l.vencimiento(),
                l.cantidad(), l.estado()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void salidaLote(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        stock.mover(new Mov(loteId, TipoMovimiento.SALIDA, cantidad.negate(), motivo, referencia, usuarioId, pacienteId,
                consultaId, null, null, null, null, false));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolucionLote(Long loteId, BigDecimal cantidad, String motivo, String referencia, Long usuarioId,
            Long pacienteId, Long consultaId) {
        stock.mover(new Mov(loteId, TipoMovimiento.DEVOLUCION, cantidad, motivo, referencia, usuarioId, pacienteId,
                consultaId, null, null, null, null, false));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LoteConsumido> descontarFefo(SalidaFefo s) {
        return descontar(s, false);
    }

    /** Variante con {@code desecho} para mermas/danos (libro de controlados). */
    public List<LoteConsumido> descontar(SalidaFefo s, boolean desecho) {
        if (s.cantidad() == null || s.cantidad().signum() <= 0) {
            throw new BusinessException("La cantidad a descontar debe ser mayor a cero");
        }
        LocalDate hoy = ZonaClinica.hoy();
        List<LoteRow> lotes = stock.lotesFefo(s.itemId(), s.bodegaId(), hoy);
        BigDecimal disponible = lotes.stream().map(LoteRow::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (disponible.compareTo(s.cantidad()) < 0) {
            BigDecimal inutil = stock.existenciasNoUtilizables(s.itemId(), s.bodegaId(), hoy);
            String nombre = jdbc.sql("select nombre from item where id = :id").param("id", s.itemId())
                    .query(String.class).optional().orElse("item " + s.itemId());
            throw new ConflictException("Stock insuficiente de " + nombre + ": disponible "
                    + plano(disponible) + ", solicitado " + plano(s.cantidad())
                    + (inutil.signum() > 0 ? " (" + plano(inutil) + " en lotes vencidos o no disponibles)" : ""));
        }
        BigDecimal pendiente = s.cantidad();
        List<LoteConsumido> usados = new ArrayList<>();
        for (LoteRow lote : lotes) {
            if (pendiente.signum() == 0) {
                break;
            }
            BigDecimal toma = lote.cantidad().min(pendiente);
            stock.mover(new Mov(lote.id(), TipoMovimiento.SALIDA, toma.negate(), s.motivo(), s.referencia(),
                    s.usuarioId(), s.pacienteId(), s.consultaId(), s.facturaItemId(), null, null, s.veterinarioId(),
                    desecho));
            usados.add(new LoteConsumido(lote.id(), lote.numeroLote(), lote.vencimiento(), toma));
            pendiente = pendiente.subtract(toma);
        }
        return usados;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal descontadoPorActosClinicos(Long consultaId, Long itemId) {
        return jdbc.sql("""
                select coalesce(sum(case when m.tipo = 'DEVOLUCION' then -m.cantidad else m.cantidad end), 0)
                from movimiento_stock m join lote l on l.id = m.lote_id
                where m.consulta_id = :consulta and l.item_id = :item and m.tipo in ('SALIDA', 'DEVOLUCION')
                  and (m.referencia like 'INMUNIZACION:%' or m.referencia like 'DESPARASITACION:%')
                """).param("consulta", consultaId).param("item", itemId).query(BigDecimal.class).single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reintegrarDeFacturaItem(Long facturaItemId, BigDecimal cantidad, String motivo, Long usuarioId) {
        record Salida(Long loteId, BigDecimal neto, Long pacienteId, Long consultaId) {
        }
        // Neto por lote descontado por este renglon (salidas - devoluciones previas).
        List<Salida> salidas = jdbc.sql("""
                select m.lote_id, sum(case when m.tipo = 'DEVOLUCION' then -m.cantidad else m.cantidad end) as neto,
                       max(m.paciente_id) as paciente_id, max(m.consulta_id) as consulta_id, min(m.id) as primero
                from movimiento_stock m where m.factura_item_id = :fi and m.tipo in ('SALIDA', 'DEVOLUCION')
                group by m.lote_id order by primero desc
                """).param("fi", facturaItemId)
                .query((rs, n) -> new Salida(rs.getLong("lote_id"), rs.getBigDecimal("neto"),
                        (Long) rs.getObject("paciente_id"), (Long) rs.getObject("consulta_id"))).list();
        BigDecimal pendiente = cantidad;
        BigDecimal devuelto = BigDecimal.ZERO;
        for (Salida s : salidas) {
            if (pendiente.signum() <= 0) {
                break;
            }
            BigDecimal toma = s.neto().min(pendiente);
            if (toma.signum() <= 0) {
                continue;
            }
            stock.bloquear(s.loteId());
            stock.mover(new Mov(s.loteId(), TipoMovimiento.DEVOLUCION, toma, motivo, "FACTURA_ITEM:" + facturaItemId,
                    usuarioId, s.pacienteId(), s.consultaId(), facturaItemId, null, null, null, false));
            pendiente = pendiente.subtract(toma);
            devuelto = devuelto.add(toma);
        }
        return devuelto;
    }

    @Override
    @Transactional(readOnly = true)
    public long contarItemsBajoMinimo() {
        return jdbc.sql("""
                select count(*) from item i
                where i.activo and i.controla_stock and i.stock_minimo > 0
                  and coalesce((select sum(l.cantidad_actual) from lote l where l.item_id = i.id and l.estado = 'DISPONIBLE'
                                and (l.fecha_vencimiento is null or l.fecha_vencimiento >= :hoy)), 0) < i.stock_minimo
                """).param("hoy", ZonaClinica.hoy()).query(Long.class).single();
    }

    @Override
    @Transactional(readOnly = true)
    public long contarLotesPorVencer(int dias) {
        LocalDate hoy = ZonaClinica.hoy();
        return jdbc.sql("""
                select count(*) from lote where cantidad_actual > 0 and estado = 'DISPONIBLE'
                  and fecha_vencimiento between :hoy and :limite
                """).param("hoy", hoy).param("limite", hoy.plusDays(dias)).query(Long.class).single();
    }

    private static String plano(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
