package com.veterinaria.laboratorio.internal;

import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaResponse;

/** SQL de los informes estructurados de imagen y patologia (fila vigente: BORRADOR o VALIDADO). */
@Repository
public class InformeRepository {

    private static final RowMapper<InformeImagenResponse> IMAGEN = (rs, n) -> new InformeImagenResponse(rs.getLong("id"),
            rs.getInt("version"), rs.getString("estado"), rs.getString("region"), rs.getString("tecnica"),
            rs.getString("hallazgos"), rs.getString("impresion_diagnostica"), rs.getString("recomendaciones"),
            rs.getString("equipo"), Sql.fecha(rs, "fecha_estudio"), Sql.persona(rs, "informado_por", "informado_nombre"),
            Sql.largo(rs, "reemplaza_a"), rs.getString("motivo_correccion"), Sql.instante(rs, "creado_en"));

    private static final RowMapper<InformePatologiaResponse> PATOLOGIA = (rs, n) -> new InformePatologiaResponse(
            rs.getLong("id"), rs.getInt("version"), rs.getString("estado"), rs.getString("tipo_muestra"),
            rs.getString("sitio_anatomico"), Sql.fecha(rs, "fecha_toma"), rs.getString("descripcion_macroscopica"),
            rs.getString("descripcion_microscopica"), rs.getString("diagnostico_morfologico"), rs.getString("margenes"),
            rs.getString("comentarios"), rs.getString("laboratorio_externo"), rs.getString("numero_caso_externo"),
            rs.getString("patologo"), Sql.fecha(rs, "fecha_informe"), Sql.persona(rs, "informado_por", "informado_nombre"),
            Sql.largo(rs, "reemplaza_a"), rs.getString("motivo_correccion"), Sql.instante(rs, "creado_en"));

    private final JdbcClient jdbc;

    public InformeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ imagen

    public Optional<InformeImagenResponse> imagenVigente(Long itemId) {
        return jdbc.sql("""
                select i.*, u.nombre as informado_nombre from informe_imagen i left join usuario u on u.id = i.informado_por
                 where i.orden_item_id = :i and i.estado <> 'CORREGIDO'
                """).param("i", itemId).query(IMAGEN).optional();
    }

    public long insertarImagen(Long itemId, InformeImagenRequest r, String estado, int version, Long informadoPor,
            Long usuarioId, Long reemplazaA, String motivo, Long validacionId) {
        return jdbc.sql("""
                insert into informe_imagen (orden_item_id, version, estado, region, tecnica, hallazgos, impresion_diagnostica,
                       recomendaciones, equipo, fecha_estudio, informado_por, creado_por, reemplaza_a, motivo_correccion, validacion_id)
                values (:i, :v, :e, :region, :tec, :hall, :imp, :rec, :eq, :fe, :inf, :cp, :rep, :mot, :val) returning id
                """).param("i", itemId).param("v", version).param("e", estado).param("region", r.region().trim())
                .param("tec", r.tecnica()).param("hall", r.hallazgos()).param("imp", r.impresionDiagnostica())
                .param("rec", r.recomendaciones()).param("eq", r.equipo()).param("fe", r.fechaEstudio())
                .param("inf", informadoPor).param("cp", usuarioId).param("rep", reemplazaA).param("mot", motivo)
                .param("val", validacionId).query(Long.class).single();
    }

    public void actualizarImagenBorrador(Long id, InformeImagenRequest r, Long informadoPor) {
        jdbc.sql("""
                update informe_imagen set region = :region, tecnica = :tec, hallazgos = :hall, impresion_diagnostica = :imp,
                       recomendaciones = :rec, equipo = :eq, fecha_estudio = :fe, informado_por = :inf, actualizado_en = now()
                 where id = :id
                """).param("id", id).param("region", r.region().trim()).param("tec", r.tecnica()).param("hall", r.hallazgos())
                .param("imp", r.impresionDiagnostica()).param("rec", r.recomendaciones()).param("eq", r.equipo())
                .param("fe", r.fechaEstudio()).param("inf", informadoPor).update();
    }

    public Optional<Long> imagenId(Long itemId, String estado) {
        return jdbc.sql("select id from informe_imagen where orden_item_id = :i and estado = :e").param("i", itemId)
                .param("e", estado).query(Long.class).optional();
    }

    public void validarImagen(Long id, Long validacionId) {
        jdbc.sql("update informe_imagen set estado = 'VALIDADO', validacion_id = :v, actualizado_en = now() where id = :id")
                .param("id", id).param("v", validacionId).update();
    }

    public void corregirImagen(Long id) {
        jdbc.sql("update informe_imagen set estado = 'CORREGIDO' where id = :id").param("id", id).update();
    }

    public void enlazarImagen(Long viejo, Long nuevo) {
        jdbc.sql("update informe_imagen set reemplazado_por = :n where id = :id").param("id", viejo).param("n", nuevo).update();
    }

    // ------------------------------------------------------------------ patologia

    public Optional<InformePatologiaResponse> patologiaVigente(Long itemId) {
        return jdbc.sql("""
                select i.*, u.nombre as informado_nombre from informe_patologia i left join usuario u on u.id = i.informado_por
                 where i.orden_item_id = :i and i.estado <> 'CORREGIDO'
                """).param("i", itemId).query(PATOLOGIA).optional();
    }

    public long insertarPatologia(Long itemId, InformePatologiaRequest r, String estado, int version, Long informadoPor,
            Long usuarioId, Long reemplazaA, String motivo, Long validacionId) {
        return jdbc.sql("""
                insert into informe_patologia (orden_item_id, version, estado, tipo_muestra, sitio_anatomico, fecha_toma,
                       descripcion_macroscopica, descripcion_microscopica, diagnostico_morfologico, margenes, comentarios,
                       laboratorio_externo, numero_caso_externo, patologo, fecha_informe, informado_por, creado_por, reemplaza_a,
                       motivo_correccion, validacion_id)
                values (:i, :v, :e, :tm, :sa, :ft, :mac, :mic, :dx, :mar, :com, :lab, :nc, :pat, :fi, :inf, :cp, :rep, :mot, :val)
                returning id
                """).param("i", itemId).param("v", version).param("e", estado).param("tm", r.tipoMuestra().trim())
                .param("sa", r.sitioAnatomico()).param("ft", r.fechaToma()).param("mac", r.descripcionMacroscopica())
                .param("mic", r.descripcionMicroscopica()).param("dx", r.diagnosticoMorfologico()).param("mar", r.margenes())
                .param("com", r.comentarios()).param("lab", r.laboratorioExterno()).param("nc", r.numeroCasoExterno())
                .param("pat", r.patologo()).param("fi", r.fechaInforme()).param("inf", informadoPor).param("cp", usuarioId)
                .param("rep", reemplazaA).param("mot", motivo).param("val", validacionId).query(Long.class).single();
    }

    public void actualizarPatologiaBorrador(Long id, InformePatologiaRequest r, Long informadoPor) {
        jdbc.sql("""
                update informe_patologia set tipo_muestra = :tm, sitio_anatomico = :sa, fecha_toma = :ft,
                       descripcion_macroscopica = :mac, descripcion_microscopica = :mic, diagnostico_morfologico = :dx,
                       margenes = :mar, comentarios = :com, laboratorio_externo = :lab, numero_caso_externo = :nc,
                       patologo = :pat, fecha_informe = :fi, informado_por = :inf, actualizado_en = now()
                 where id = :id
                """).param("id", id).param("tm", r.tipoMuestra().trim()).param("sa", r.sitioAnatomico())
                .param("ft", r.fechaToma()).param("mac", r.descripcionMacroscopica()).param("mic", r.descripcionMicroscopica())
                .param("dx", r.diagnosticoMorfologico()).param("mar", r.margenes()).param("com", r.comentarios())
                .param("lab", r.laboratorioExterno()).param("nc", r.numeroCasoExterno()).param("pat", r.patologo())
                .param("fi", r.fechaInforme()).param("inf", informadoPor).update();
    }

    public Optional<Long> patologiaId(Long itemId, String estado) {
        return jdbc.sql("select id from informe_patologia where orden_item_id = :i and estado = :e").param("i", itemId)
                .param("e", estado).query(Long.class).optional();
    }

    public void validarPatologia(Long id, Long validacionId) {
        jdbc.sql("update informe_patologia set estado = 'VALIDADO', validacion_id = :v, actualizado_en = now() where id = :id")
                .param("id", id).param("v", validacionId).update();
    }

    public void corregirPatologia(Long id) {
        jdbc.sql("update informe_patologia set estado = 'CORREGIDO' where id = :id").param("id", id).update();
    }

    public void enlazarPatologia(Long viejo, Long nuevo) {
        jdbc.sql("update informe_patologia set reemplazado_por = :n where id = :id").param("id", viejo).param("n", nuevo).update();
    }
}
