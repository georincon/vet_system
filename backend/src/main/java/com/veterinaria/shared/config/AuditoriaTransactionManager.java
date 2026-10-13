package com.veterinaria.shared.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.veterinaria.shared.security.UsuarioActual;

import jakarta.persistence.EntityManagerFactory;

/**
 * Transaction manager JPA que, al abrir una transaccion de escritura con un usuario autenticado,
 * fija {@code app.usuario_id} y {@code app.ip} con {@code set_config(..., true)} (equivale a
 * {@code SET LOCAL}: vive solo durante la transaccion). El trigger {@code fn_auditar_cambio}
 * (V12) los usa para registrar quien hizo cada cambio.
 */
public class AuditoriaTransactionManager extends JpaTransactionManager {

    private static final String SQL = "select set_config('app.usuario_id', ?, true), set_config('app.ip', ?, true)";

    public AuditoriaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (definition.isReadOnly() || getDataSource() == null) {
            return;
        }
        var usuarioId = UsuarioActual.id();
        if (usuarioId.isEmpty()) {
            return;
        }
        Connection con = DataSourceUtils.getConnection(getDataSource());
        try (PreparedStatement ps = con.prepareStatement(SQL)) {
            ps.setString(1, String.valueOf(usuarioId.get()));
            ps.setString(2, ipCliente());
            ps.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("No se pudo fijar el contexto de auditoria", e);
        } finally {
            DataSourceUtils.releaseConnection(con, getDataSource());
        }
    }

    private static String ipCliente() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            String ip = servlet.getRequest().getRemoteAddr();
            return ip == null ? "" : ip;
        }
        return "";
    }
}
