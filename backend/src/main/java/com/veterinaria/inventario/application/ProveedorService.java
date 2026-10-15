package com.veterinaria.inventario.application;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inventario.api.dto.CompraDtos.ProveedorRequest;
import com.veterinaria.inventario.api.dto.CompraDtos.ProveedorResponse;
import com.veterinaria.inventario.internal.Filtro;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.web.PageResponse;

/** Proveedores (solo ADMIN). */
@Service
public class ProveedorService {

    private final JdbcClient jdbc;

    public ProveedorService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProveedorResponse> listar(String q, Boolean activo, Pageable pageable) {
        Filtro f = new Filtro().si(activo, "activo = :activo", "activo");
        if (q != null && !q.isBlank()) {
            f.y("(razon_social ilike :q or numero_documento ilike :q or contacto ilike :q)", "q", "%" + q.trim() + "%");
        }
        long total = jdbc.sql("select count(*) from proveedor" + f.sql()).params(f.params()).query(Long.class).single();
        List<ProveedorResponse> items = jdbc.sql("select * from proveedor" + f.sql() + " order by razon_social, id limit :lim offset :off")
                .params(f.params()).param("lim", pageable.getPageSize()).param("off", pageable.getOffset())
                .query((rs, n) -> map(rs)).list();
        return PageResponse.from(new PageImpl<>(items, pageable, total), x -> x);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse obtener(Long id) {
        return jdbc.sql("select * from proveedor where id = :id").param("id", id).query((rs, n) -> map(rs)).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest r) {
        String tipo = r.tipoDocumento() == null ? "NIT" : r.tipoDocumento();
        String numero = Textos.limpiar(r.numeroDocumento());
        existeDocumento(tipo, numero, null);
        Long id = jdbc.sql("""
                insert into proveedor (tipo_documento, numero_documento, razon_social, contacto, telefono, email, direccion,
                                       ciudad, plazo_pago_dias, notas, activo)
                values (:t, :n, :rs, :c, :tel, :e, :d, :ci, :p, :no, :a) returning id
                """).param("t", tipo).param("n", numero).param("rs", Textos.limpiar(r.razonSocial()))
                .param("c", Textos.limpiar(r.contacto())).param("tel", Textos.limpiar(r.telefono()))
                .param("e", Textos.limpiar(r.email())).param("d", Textos.limpiar(r.direccion()))
                .param("ci", Textos.limpiar(r.ciudad())).param("p", r.plazoPagoDias() == null ? 0 : r.plazoPagoDias())
                .param("no", Textos.limpiar(r.notas())).param("a", r.activo() == null || r.activo())
                .query(Long.class).single();
        return obtener(id);
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest r) {
        ProveedorResponse a = obtener(id);
        String tipo = r.tipoDocumento() != null ? r.tipoDocumento() : a.tipoDocumento();
        String numero = r.numeroDocumento() == null ? a.numeroDocumento() : Textos.limpiar(r.numeroDocumento());
        String razon = r.razonSocial() == null ? a.razonSocial() : Textos.limpiar(r.razonSocial());
        if (numero == null || razon == null) {
            throw new BusinessException("El documento y la razon social no pueden quedar vacios");
        }
        existeDocumento(tipo, numero, id);
        jdbc.sql("""
                update proveedor set tipo_documento = :t, numero_documento = :n, razon_social = :rs, contacto = :c,
                       telefono = :tel, email = :e, direccion = :d, ciudad = :ci, plazo_pago_dias = :p, notas = :no,
                       activo = :a, actualizado_en = now() where id = :id
                """).param("id", id).param("t", tipo).param("n", numero).param("rs", razon)
                .param("c", Textos.actualizar(a.contacto(), r.contacto())).param("tel", Textos.actualizar(a.telefono(), r.telefono()))
                .param("e", Textos.actualizar(a.email(), r.email())).param("d", Textos.actualizar(a.direccion(), r.direccion()))
                .param("ci", Textos.actualizar(a.ciudad(), r.ciudad()))
                .param("p", r.plazoPagoDias() != null ? r.plazoPagoDias() : a.plazoPagoDias())
                .param("no", Textos.actualizar(a.notas(), r.notas())).param("a", r.activo() != null ? r.activo() : a.activo())
                .update();
        return obtener(id);
    }

    private void existeDocumento(String tipo, String numero, Long excluir) {
        if (jdbc.sql("select count(*) from proveedor where tipo_documento = :t and numero_documento = :n and (cast(:x as bigint) is null or id <> :x)")
                .param("t", tipo).param("n", numero).param("x", excluir).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe un proveedor con el documento " + tipo + " " + numero);
        }
    }

    private static ProveedorResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProveedorResponse(rs.getLong("id"), rs.getString("tipo_documento"), rs.getString("numero_documento"),
                rs.getString("razon_social"), rs.getString("contacto"), rs.getString("telefono"), rs.getString("email"),
                rs.getString("direccion"), rs.getString("ciudad"), rs.getInt("plazo_pago_dias"), rs.getString("notas"),
                rs.getBoolean("activo"));
    }
}
