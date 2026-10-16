package com.veterinaria.comunicaciones.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.comunicaciones.api.dto.ReglaRequest;
import com.veterinaria.comunicaciones.api.dto.ReglaResponse;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;

/** CRUD de reglas de recordatorio (cuando y por que canal avisar). */
@Service
public class ReglaService {

    private final JdbcClient jdbc;
    private final Map<String, CanalEnvio> canales;

    public ReglaService(JdbcClient jdbc, List<CanalEnvio> adaptadores) {
        this.jdbc = jdbc;
        this.canales = adaptadores.stream().filter(CanalEnvio::admiteRecordatorios).collect(Collectors.toMap(CanalEnvio::canal, Function.identity()));
    }

    private static final String SELECT = """
            select r.id, r.codigo, r.evento, r.dias_offset, r.canal, r.plantilla_id, p.codigo as plantilla_codigo, r.activo
              from regla_recordatorio r left join plantilla_mensaje p on p.id = r.plantilla_id
            """;

    @Transactional(readOnly = true)
    public List<ReglaResponse> listar(String evento, Boolean activo) {
        return jdbc.sql(SELECT + """
                 where (cast(:evento as text) is null or r.evento = cast(:evento as text))
                   and (cast(:activo as boolean) is null or r.activo = cast(:activo as boolean))
                 order by r.evento, r.dias_offset, r.codigo
                """).param("evento", evento).param("activo", activo).query(this::mapear).list();
    }

    @Transactional(readOnly = true)
    public ReglaResponse obtener(Long id) {
        return jdbc.sql(SELECT + " where r.id = :id").param("id", id).query(this::mapear).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Regla", id));
    }

    @Transactional
    public ReglaResponse crear(ReglaRequest r) {
        if (jdbc.sql("select count(*) from regla_recordatorio where codigo = :c").param("c", r.codigo().trim())
                .query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una regla con el codigo '%s'.".formatted(r.codigo().trim()));
        }
        validarPlantilla(r.plantillaId(), r.canal());
        Long id = jdbc.sql("""
                insert into regla_recordatorio (codigo, evento, dias_offset, canal, plantilla_id, sede_id, activo)
                values (:codigo, :evento, :dias, :canal, :plantilla, null, :activo) returning id
                """).param("codigo", r.codigo().trim()).param("evento", r.evento()).param("dias", r.diasOffset())
                .param("canal", r.canal()).param("plantilla", r.plantillaId())
                .param("activo", r.activo() == null || r.activo()).query(Long.class).single();
        return obtener(id);
    }

    @Transactional
    public ReglaResponse actualizar(Long id, ReglaRequest r) {
        ReglaResponse a = obtener(id);
        String codigo = r.codigo() == null ? a.codigo() : r.codigo().trim();
        if (jdbc.sql("select count(*) from regla_recordatorio where codigo = :c and id <> :id").param("c", codigo)
                .param("id", id).query(Long.class).single() > 0) {
            throw new ConflictException("Ya existe una regla con el codigo '%s'.".formatted(codigo));
        }
        String canal = r.canal() == null ? a.canal() : r.canal();
        Long plantilla = r.plantillaId() == null ? a.plantillaId() : r.plantillaId();
        validarPlantilla(plantilla, canal);
        jdbc.sql("""
                update regla_recordatorio set codigo = :codigo, evento = :evento, dias_offset = :dias, canal = :canal,
                       plantilla_id = :plantilla, activo = :activo where id = :id
                """).param("codigo", codigo).param("evento", r.evento() == null ? a.evento() : r.evento())
                .param("dias", r.diasOffset() == null ? a.diasOffset() : r.diasOffset()).param("canal", canal)
                .param("plantilla", plantilla).param("activo", r.activo() == null ? a.activo() : r.activo())
                .param("id", id).update();
        return obtener(id);
    }

    private void validarPlantilla(Long plantillaId, String canal) {
        String canalPlantilla = jdbc.sql("select canal from plantilla_mensaje where id = :id").param("id", plantillaId)
                .query(String.class).optional().orElseThrow(() -> new ResourceNotFoundException("Plantilla", plantillaId));
        if (!canalPlantilla.equals(canal)) {
            throw new BusinessException("La plantilla es del canal " + canalPlantilla + " y la regla del canal " + canal);
        }
    }

    private ReglaResponse mapear(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String canal = rs.getString("canal");
        return new ReglaResponse(rs.getLong("id"), rs.getString("codigo"), rs.getString("evento"),
                rs.getInt("dias_offset"), canal, rs.getObject("plantilla_id") == null ? null : rs.getLong("plantilla_id"),
                rs.getString("plantilla_codigo"), rs.getBoolean("activo"), canales.containsKey(canal));
    }
}
