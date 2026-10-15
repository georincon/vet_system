package com.veterinaria.facturacion.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.facturacion.domain.Importes;
import com.veterinaria.inventario.CatalogoApi;
import com.veterinaria.inventario.CatalogoApi.ItemVenta;
import com.veterinaria.inventario.ProductoClinicoResolver;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Cargos automaticos al cerrar una consulta: crea (una sola vez, indice unico por consulta) o completa el presupuesto
 * BORRADOR de origen CONSULTA con el servicio de la consulta, procedimientos con item, medicamentos de la receta con
 * item de inventario y vacunas aplicadas (mas el acto de vacunacion). Es idempotente: cada renglon se identifica por
 * (origen_tabla, origen_id) y un segundo evento no duplica nada ni pisa precios/descuentos editados. Si el
 * presupuesto ya salio de BORRADOR no se toca. No descuenta stock (eso ocurre al facturar).
 */
@Service
public class CargoConsultaService {

    private static final Logger log = LoggerFactory.getLogger(CargoConsultaService.class);

    /** Servicio de consulta por tipo (V15) cuando la cita no define su item (tipo_cita.item_id). */
    private static final Map<String, String> SERVICIO_POR_TIPO = Map.of(
            "GENERAL", "SRV-CONS-GEN", "CONTROL", "SRV-CONS-CTL", "URGENCIA", "SRV-CONS-URG",
            "TELEMEDICINA", "SRV-CONS-TEL", "DOMICILIO", "SRV-CONS-DOM", "POSTQUIRURGICA", "SRV-CONS-CTL",
            "PREQUIRURGICA", "SRV-CONS-GEN", "HOSPITALIZACION", "SRV-CONS-GEN");
    private static final String ACTO_VACUNA = "SRV-PRC-VAC";

    private final JdbcClient jdbc;
    private final CatalogoApi catalogo;
    private final ObjectProvider<ProductoClinicoResolver> resolver;
    private final PresupuestoService presupuestos;
    private final SedePrincipal sede;

    public CargoConsultaService(JdbcClient jdbc, CatalogoApi catalogo, ObjectProvider<ProductoClinicoResolver> resolver,
            PresupuestoService presupuestos, SedePrincipal sede) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
        this.resolver = resolver;
        this.presupuestos = presupuestos;
        this.sede = sede;
    }

    /** @return renglones nuevos agregados al presupuesto de la consulta (0 si ya estaba todo) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int generar(Long consultaId) {
        record C(Long pacienteId, Long veterinarioId, String tipo, Long citaId) {
        }
        Optional<C> consulta = jdbc.sql("select paciente_id, veterinario_id, tipo_consulta, cita_id from consulta where id = :id")
                .param("id", consultaId).query((rs, n) -> new C(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        (Long) rs.getObject(4))).optional();
        if (consulta.isEmpty()) {
            return 0;
        }
        C c = consulta.get();
        Optional<Long> tutor = jdbc.sql("select tutor_id from paciente_tutor where paciente_id = :p order by es_principal desc, tutor_id limit 1")
                .param("p", c.pacienteId()).query(Long.class).optional();
        if (tutor.isEmpty()) {
            log.warn("Consulta {} sin tutor: no se genera cargo", consultaId);
            return 0;
        }
        Long presupuestoId = jdbc.sql("""
                insert into presupuesto (codigo, tutor_id, paciente_id, consulta_id, estado, sede_id, vigente_hasta, origen, notas)
                values ('PRE-' || lpad(nextval('seq_presupuesto')::text, 6, '0'), :t, :p, :c, 'BORRADOR', :s, :v, 'CONSULTA',
                        'Generado al cerrar la consulta')
                on conflict (consulta_id) where origen = 'CONSULTA' do nothing returning id
                """).param("t", tutor.get()).param("p", c.pacienteId()).param("c", consultaId).param("s", sede.id())
                .param("v", ZonaClinica.hoy().plusDays(30)).query(Long.class).optional()
                .orElseGet(() -> jdbc.sql("select id from presupuesto where consulta_id = :c and origen = 'CONSULTA'")
                        .param("c", consultaId).query(Long.class).single());
        String estado = jdbc.sql("select estado from presupuesto where id = :id for update").param("id", presupuestoId)
                .query(String.class).single();
        if (!estado.equals("BORRADOR")) {
            log.info("Presupuesto {} de la consulta {} esta {}: no se agregan cargos", presupuestoId, consultaId, estado);
            return 0;
        }
        int nuevos = 0;
        for (LineaResuelta l : cargos(consultaId, c.tipo(), c.citaId(), c.pacienteId())) {
            if (presupuestos.insertarLinea(presupuestoId, l)) {
                nuevos++;
            }
        }
        presupuestos.recalcularTotal(presupuestoId);
        log.info("Consulta {} cerrada: presupuesto {} con {} renglon(es) nuevo(s)", consultaId, presupuestoId, nuevos);
        return nuevos;
    }

    private List<LineaResuelta> cargos(Long consultaId, String tipoConsulta, Long citaId, Long pacienteId) {
        List<LineaResuelta> res = new ArrayList<>();
        // 1) servicio de la consulta
        Long itemCita = citaId == null ? null : jdbc.sql("select tc.item_id from cita c join tipo_cita tc on tc.id = c.tipo_cita_id where c.id = :c")
                .param("c", citaId).query(Long.class).optional().orElse(null);
        Optional<ItemVenta> servicio = itemCita != null ? catalogo.item(itemCita)
                : (SERVICIO_POR_TIPO.containsKey(tipoConsulta) ? catalogo.itemPorCodigo(SERVICIO_POR_TIPO.get(tipoConsulta))
                        : Optional.empty());
        servicio.filter(ItemVenta::activo).ifPresent(i -> res.add(linea(i, BigDecimal.ONE, null, "consulta", consultaId,
                pacienteId, consultaId)));
        // 2) procedimientos
        jdbc.sql("select id, item_id, descripcion, cantidad, precio_unitario from consulta_procedimiento where consulta_id = :c order by id")
                .param("c", consultaId).query((rs, n) -> {
                    Long itemId = (Long) rs.getObject("item_id");
                    BigDecimal cantidad = rs.getBigDecimal("cantidad");
                    BigDecimal precio = rs.getBigDecimal("precio_unitario");
                    long id = rs.getLong("id");
                    Optional<ItemVenta> item = itemId == null ? Optional.empty() : catalogo.item(itemId).filter(ItemVenta::activo);
                    if (item.isPresent()) {
                        res.add(linea(item.get(), cantidad, precio, "consulta_procedimiento", id, pacienteId, consultaId));
                    } else if (precio != null && precio.signum() > 0) {
                        res.add(libre(rs.getString("descripcion"), cantidad, precio, "consulta_procedimiento", id, pacienteId,
                                consultaId));
                    }
                    return 0;
                }).list();
        // 3) receta: solo renglones con item de inventario
        jdbc.sql("""
                select ri.id, ri.item_id, coalesce(ri.cantidad, 1) as cantidad from receta_item ri join receta r on r.id = ri.receta_id
                where r.consulta_id = :c and r.estado <> 'CANCELADA' and ri.item_id is not null order by ri.id
                """).param("c", consultaId).query((rs, n) -> {
                    long recetaItemId = rs.getLong("id");
                    BigDecimal cantidad = BigDecimal.valueOf(Math.max(1, rs.getInt("cantidad")));
                    catalogo.item(rs.getLong("item_id")).filter(ItemVenta::activo).ifPresent(i -> res.add(linea(i,
                            cantidad, null, "receta_item", recetaItemId, pacienteId, consultaId)));
                    return 0;
                }).list();
        // 4) vacunas aplicadas + acto de vacunacion
        List<Object[]> vacunas = jdbc.sql("select id, nombre from consulta_vacuna where consulta_id = :c order by id")
                .param("c", consultaId).query((rs, n) -> new Object[] {rs.getLong(1), rs.getString(2)}).list();
        for (Object[] v : vacunas) {
            String nombre = (String) v[1];
            Optional<ItemVenta> item = Optional.ofNullable(resolver.getIfAvailable())
                    .flatMap(r -> r.itemIdDeVacunaPorNombre(nombre)).flatMap(catalogo::item)
                    .or(() -> catalogo.itemPorNombreOCodigo(nombre)).filter(ItemVenta::activo);
            if (item.isPresent()) {
                res.add(linea(item.get(), BigDecimal.ONE, null, "consulta_vacuna", (Long) v[0], pacienteId, consultaId));
            } else {
                log.warn("Vacuna '{}' de la consulta {} sin item en inventario: no se cobra automaticamente", nombre, consultaId);
            }
        }
        if (!vacunas.isEmpty()) {
            catalogo.itemPorCodigo(ACTO_VACUNA).filter(ItemVenta::activo).ifPresent(i -> res.add(linea(i, BigDecimal.ONE,
                    null, "consulta_vacuna_acto", consultaId, pacienteId, consultaId)));
        }
        return res;
    }

    private LineaResuelta linea(ItemVenta item, BigDecimal cantidad, BigDecimal precioOverride, String origenTabla,
            Long origenId, Long pacienteId, Long consultaId) {
        BigDecimal precio = precioOverride != null && precioOverride.signum() > 0 ? precioOverride
                : catalogo.precioVigente(item.id(), sede.id());
        Importes.Linea calc = Importes.calcular(cantidad, precio, BigDecimal.ZERO, item.impuestoPct());
        return new LineaResuelta(item.id(), item.codigo(), item.nombre(), cantidad, precio, item.impuestoPct(),
                BigDecimal.ZERO, calc, pacienteId, null, consultaId, item.controlaStock(), item.controlaStock(),
                origenTabla, origenId);
    }

    private LineaResuelta libre(String descripcion, BigDecimal cantidad, BigDecimal precio, String origenTabla,
            Long origenId, Long pacienteId, Long consultaId) {
        Importes.Linea calc = Importes.calcular(cantidad, precio, BigDecimal.ZERO, BigDecimal.ZERO);
        return new LineaResuelta(null, null, descripcion, cantidad, precio, BigDecimal.ZERO, BigDecimal.ZERO, calc,
                pacienteId, null, consultaId, false, false, origenTabla, origenId);
    }
}
