package com.veterinaria.facturacion.application;

import java.math.BigDecimal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.api.dto.FacturacionDtos.PagoRequest;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;

/**
 * Pagos y saldo. Saldo = max(0, (total - notas credito) - pagos aplicados). Estado: PAGADA si el saldo es 0,
 * PARCIAL si hubo pagos o notas credito, EMITIDA si no; ANULADA solo la fija la nota credito total.
 */
@Service
public class PagoService {

    private final JdbcClient jdbc;
    private final CajaService caja;
    private final SedePrincipal sede;

    public PagoService(JdbcClient jdbc, CajaService caja, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.caja = caja;
        this.sede = sede;
    }

    /** Registra un pago contra una factura/POS y recalcula saldo y estado. Requiere turno de caja abierto. */
    @Transactional
    public Long registrar(Long facturaId, PagoRequest p) {
        Long turno = caja.turnoAbiertoDelUsuario();
        return registrar(facturaId, p, turno);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long registrar(Long facturaId, PagoRequest p, Long turnoId) {
        record F(String tipo, String estado, BigDecimal saldo, String numero) {
        }
        F f = jdbc.sql("select tipo_documento, estado, saldo, numero from factura where id = :id for update")
                .param("id", facturaId).query((rs, n) -> new F(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4)))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Factura", facturaId));
        if (!f.tipo().equals("FACTURA") && !f.tipo().equals("POS")) {
            throw new BusinessException("Solo se registran pagos sobre facturas o ventas POS");
        }
        if (f.estado().equals("ANULADA")) {
            throw new ConflictException("La factura " + f.numero() + " esta anulada");
        }
        if (f.saldo().signum() == 0) {
            throw new ConflictException("La factura " + f.numero() + " no tiene saldo pendiente");
        }
        if (p.monto().compareTo(f.saldo()) > 0) {
            throw new BusinessException("El pago (" + p.monto() + ") excede el saldo de la factura " + f.numero() + " ("
                    + f.saldo() + ")");
        }
        Long id = jdbc.sql("""
                insert into pago (factura_id, monto, metodo, referencia, usuario_id, turno_id, sede_id, estado)
                values (:f, :m, :metodo, :ref, :u, :t, :s, 'APLICADO') returning id
                """).param("f", facturaId).param("m", p.monto()).param("metodo", p.metodo())
                .param("ref", Textos.limpiar(p.referencia())).param("u", UsuarioActual.id().orElse(null))
                .param("t", turnoId).param("s", sede.id()).query(Long.class).single();
        recalcular(facturaId);
        return id;
    }

    /** Recalcula saldo y estado de una factura (bloqueada por el llamador). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalcular(Long facturaId) {
        record R(BigDecimal total, String estado, BigDecimal pagado, BigDecimal acreditado) {
        }
        R r = jdbc.sql("""
                select f.total, f.estado,
                       coalesce((select sum(monto) from pago where factura_id = f.id and estado = 'APLICADO'), 0),
                       coalesce((select sum(n.total) from factura n where n.factura_origen_id = f.id and n.tipo_documento = 'NOTA_CREDITO'), 0)
                from factura f where f.id = :id
                """).param("id", facturaId).query((rs, n) -> new R(rs.getBigDecimal(1), rs.getString(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4))).single();
        BigDecimal neto = r.total().subtract(r.acreditado());
        BigDecimal saldo = neto.subtract(r.pagado()).max(BigDecimal.ZERO);
        String estado;
        if (r.estado().equals("ANULADA")) {
            estado = "ANULADA";
            saldo = BigDecimal.ZERO;
        } else if (saldo.signum() == 0) {
            estado = "PAGADA";
        } else if (r.pagado().signum() > 0 || r.acreditado().signum() > 0) {
            estado = "PARCIAL";
        } else {
            estado = "EMITIDA";
        }
        jdbc.sql("update factura set saldo = :s, estado = :e, actualizado_en = now() where id = :id")
                .param("s", saldo).param("e", estado).param("id", facturaId).update();
    }
}
