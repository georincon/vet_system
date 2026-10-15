package com.veterinaria.laboratorio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResumen;
import com.veterinaria.laboratorio.api.dto.LabDtos.ParametroTendencia;
import com.veterinaria.laboratorio.api.dto.LabDtos.PuntoTendencia;
import com.veterinaria.laboratorio.api.dto.LabDtos.ResumenTendencia;
import com.veterinaria.laboratorio.api.dto.LabDtos.TendenciaResponse;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository.Filtro;
import com.veterinaria.laboratorio.internal.Sql;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.web.PageResponse;

/** Historial de ordenes del paciente y tendencia de un analito a lo largo del tiempo (solo resultados VALIDADOS). */
@Service
public class HistorialService {

    private static final BigDecimal ESTABLE_PCT = BigDecimal.valueOf(5);

    private final OrdenRepository ordenes;
    private final CatalogoRepository catalogo;
    private final PacienteLector pacientes;
    private final JdbcClient jdbc;

    public HistorialService(OrdenRepository ordenes, CatalogoRepository catalogo, PacienteLector pacientes, JdbcClient jdbc) {
        this.ordenes = ordenes;
        this.catalogo = catalogo;
        this.pacientes = pacientes;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrdenResumen> historial(Long pacienteId, String tipo, Pageable pageable) {
        pacientes.obtener(pacienteId);
        Filtro f = new Filtro(null, null, pacienteId, null, tipo, null, null, null);
        long total = ordenes.contar(f);
        List<OrdenResumen> items = ordenes.pagina(f, pageable.getPageSize(), pageable.getOffset());
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    /**
     * Serie del analito identificado por {@code parametroId}: incluye los resultados validados de cualquier panel cuyo
     * parametro tenga el mismo codigo (p. ej. ALT del perfil bioquimico y del perfil hepatico), de la fecha de toma de
     * muestra mas antigua a la mas reciente.
     */
    @Transactional(readOnly = true)
    public TendenciaResponse tendencia(Long pacienteId, Long parametroId) {
        pacientes.obtener(pacienteId);
        var p = catalogo.parametroInfo(parametroId).orElseThrow(() -> new ResourceNotFoundException("Parametro", parametroId));
        List<PuntoTendencia> puntos = jdbc.sql("""
                select r.id, coalesce(o.muestra_tomada_en, v.validado_en, r.registrado_en) as fecha, o.id as orden_id, o.codigo,
                       r.valor_numerico, r.valor_texto, r.unidad, r.bandera, r.critico, r.ref_min, r.ref_max
                  from resultado_examen r
                  join orden_examen_item i on i.id = r.orden_item_id
                  join orden_examen o on o.id = i.orden_id
                  join examen_parametro ep on ep.id = r.parametro_id
                  left join orden_item_validacion v on v.id = r.validacion_id
                 where o.paciente_id = :pac and r.estado = 'VALIDADO' and upper(ep.codigo) = upper(:codigo)
                 order by fecha, r.id
                """).param("pac", pacienteId).param("codigo", p.codigo())
                .query((rs, n) -> new PuntoTendencia(rs.getLong("id"), Sql.instante(rs, "fecha"), rs.getLong("orden_id"),
                        rs.getString("codigo"), rs.getBigDecimal("valor_numerico"), rs.getString("valor_texto"),
                        rs.getString("unidad"), rs.getString("bandera"), rs.getBoolean("critico"),
                        rs.getBigDecimal("ref_min"), rs.getBigDecimal("ref_max"))).list();
        return new TendenciaResponse(pacienteId, new ParametroTendencia(p.id(), p.codigo(), p.nombre(), p.unidad(), p.tipoValor()),
                puntos, resumen(puntos));
    }

    static ResumenTendencia resumen(List<PuntoTendencia> puntos) {
        List<BigDecimal> valores = puntos.stream().map(PuntoTendencia::valorNumerico).filter(v -> v != null).toList();
        if (valores.isEmpty()) {
            return new ResumenTendencia(puntos.size(), null, null, null, null, "SIN_DATOS", null, null);
        }
        BigDecimal ultimo = valores.get(valores.size() - 1);
        BigDecimal min = valores.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = valores.stream().max(BigDecimal::compareTo).orElseThrow();
        if (valores.size() < 2) {
            return new ResumenTendencia(puntos.size(), ultimo, null, null, null, "SIN_DATOS", min, max);
        }
        BigDecimal anterior = valores.get(valores.size() - 2);
        BigDecimal delta = ultimo.subtract(anterior);
        BigDecimal pct = anterior.signum() == 0 ? null : delta.multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 1, RoundingMode.HALF_UP);
        String direccion = pct == null ? (delta.signum() == 0 ? "ESTABLE" : delta.signum() > 0 ? "SUBE" : "BAJA")
                : pct.abs().compareTo(ESTABLE_PCT) < 0 ? "ESTABLE" : pct.signum() > 0 ? "SUBE" : "BAJA";
        return new ResumenTendencia(puntos.size(), ultimo, anterior, delta, pct, direccion, min, max);
    }
}
