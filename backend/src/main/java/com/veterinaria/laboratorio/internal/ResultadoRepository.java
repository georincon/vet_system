package com.veterinaria.laboratorio.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.laboratorio.api.dto.LabDtos.ResultadoResponse;
import com.veterinaria.laboratorio.domain.Evaluado;

/** SQL de resultados de examen (fila vigente + historial de correcciones). */
@Repository
public class ResultadoRepository {

    public record Vigente(Long id, String estado) {
    }

    private final JdbcClient jdbc;

    public ResultadoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ResultadoResponse> porItem(Long itemId) {
        return jdbc.sql("""
                select r.id, r.parametro_id, p.codigo as parametro_codigo, p.nombre as parametro_nombre, p.codigo_loinc,
                       r.valor_numerico, r.valor_texto, r.unidad, r.ref_min, r.ref_max, r.ref_critico_min, r.ref_critico_max,
                       r.bandera, r.critico, r.estado, r.origen, r.observacion, r.registrado_en, r.registrado_por,
                       u.nombre as registrado_nombre, r.validacion_id, r.reemplaza_a, r.reemplazado_por, r.motivo_correccion
                  from resultado_examen r
                  join examen_parametro p on p.id = r.parametro_id
                  left join usuario u on u.id = r.registrado_por
                 where r.orden_item_id = :i order by p.orden, p.id, r.id
                """).param("i", itemId).query((rs, n) -> new ResultadoResponse(rs.getLong("id"), rs.getLong("parametro_id"),
                rs.getString("parametro_codigo"), rs.getString("parametro_nombre"), rs.getString("codigo_loinc"),
                rs.getBigDecimal("valor_numerico"), rs.getString("valor_texto"), rs.getString("unidad"),
                rs.getBigDecimal("ref_min"), rs.getBigDecimal("ref_max"), rs.getBigDecimal("ref_critico_min"),
                rs.getBigDecimal("ref_critico_max"), rs.getString("bandera"), rs.getBoolean("critico"),
                rs.getString("estado"), rs.getString("origen"), rs.getString("observacion"),
                Sql.instante(rs, "registrado_en"), Sql.persona(rs, "registrado_por", "registrado_nombre"),
                Sql.largo(rs, "validacion_id"), Sql.largo(rs, "reemplaza_a"), Sql.largo(rs, "reemplazado_por"),
                rs.getString("motivo_correccion"))).list();
    }

    public Optional<Vigente> vigente(Long itemId, Long parametroId) {
        return jdbc.sql("select id, estado from resultado_examen where orden_item_id = :i and parametro_id = :p and estado <> 'CORREGIDO'")
                .param("i", itemId).param("p", parametroId)
                .query((rs, n) -> new Vigente(rs.getLong("id"), rs.getString("estado"))).optional();
    }

    public long insertar(Long itemId, Long parametroId, Evaluado e, String estado, String origen, Long usuarioId,
            String observacion, Long validacionId, Long reemplazaA, String motivoCorreccion, Long importacionId) {
        return jdbc.sql("""
                insert into resultado_examen (orden_item_id, parametro_id, valor_numerico, valor_texto, unidad, ref_min, ref_max,
                       ref_critico_min, ref_critico_max, rango_id, bandera, critico, estado, origen, registrado_por, observacion,
                       validacion_id, reemplaza_a, motivo_correccion, importacion_id)
                values (:i, :p, :vn, :vt, :u, :rmin, :rmax, :cmin, :cmax, :rango, :b, :c, :estado, :origen, :usuario, :obs,
                        :val, :rep, :motivo, :imp)
                returning id
                """).param("i", itemId).param("p", parametroId).param("vn", e.valorNumerico()).param("vt", e.valorTexto())
                .param("u", e.unidad()).param("rmin", e.refMin()).param("rmax", e.refMax()).param("cmin", e.refCriticoMin())
                .param("cmax", e.refCriticoMax()).param("rango", e.rangoId()).param("b", e.bandera())
                .param("c", e.critico()).param("estado", estado).param("origen", origen).param("usuario", usuarioId)
                .param("obs", observacion).param("val", validacionId).param("rep", reemplazaA).param("motivo", motivoCorreccion)
                .param("imp", importacionId).query(Long.class).single();
    }

    /** Reescribe un borrador (la bandera y el snapshot se recalculan con el rango vigente). */
    public void actualizarBorrador(Long id, Evaluado e, String origen, Long usuarioId, String observacion, Long importacionId) {
        jdbc.sql("""
                update resultado_examen set valor_numerico = :vn, valor_texto = :vt, unidad = :u, ref_min = :rmin, ref_max = :rmax,
                       ref_critico_min = :cmin, ref_critico_max = :cmax, rango_id = :rango, bandera = :b, critico = :c,
                       origen = :origen, registrado_por = :usuario, registrado_en = now(), observacion = :obs,
                       importacion_id = :imp
                 where id = :id
                """).param("id", id).param("vn", e.valorNumerico()).param("vt", e.valorTexto()).param("u", e.unidad())
                .param("rmin", e.refMin()).param("rmax", e.refMax()).param("cmin", e.refCriticoMin())
                .param("cmax", e.refCriticoMax()).param("rango", e.rangoId()).param("b", e.bandera())
                .param("c", e.critico()).param("origen", origen).param("usuario", usuarioId).param("obs", observacion)
                .param("imp", importacionId).update();
    }

    public void marcarCorregido(Long id) {
        jdbc.sql("update resultado_examen set estado = 'CORREGIDO' where id = :id").param("id", id).update();
    }

    public void enlazarReemplazo(Long viejoId, Long nuevoId) {
        jdbc.sql("update resultado_examen set reemplazado_por = :n where id = :id").param("id", viejoId).param("n", nuevoId).update();
    }

    public int validarBorradores(Long itemId, Long validacionId) {
        return jdbc.sql("update resultado_examen set estado = 'VALIDADO', validacion_id = :v where orden_item_id = :i and estado = 'BORRADOR'")
                .param("i", itemId).param("v", validacionId).update();
    }

    public int contarBorradores(Long itemId) {
        return jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i and estado = 'BORRADOR'")
                .param("i", itemId).query(Integer.class).single();
    }

    public int contarVigentes(Long itemId) {
        return jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i and estado <> 'CORREGIDO'")
                .param("i", itemId).query(Integer.class).single();
    }

    public int contarCriticosBorrador(Long itemId) {
        return jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i and estado = 'BORRADOR' and critico")
                .param("i", itemId).query(Integer.class).single();
    }

    public int contarAnormalesBorrador(Long itemId) {
        return jdbc.sql("select count(*) from resultado_examen where orden_item_id = :i and estado = 'BORRADOR' and bandera is not null and bandera <> 'N'")
                .param("i", itemId).query(Integer.class).single();
    }
}
