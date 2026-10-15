package com.veterinaria.laboratorio.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.archivos.ArchivosApi;
import com.veterinaria.laboratorio.api.dto.LabDtos.EstadoOrdenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ItemResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.MuestraRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResumen;
import com.veterinaria.laboratorio.api.dto.LabDtos.Persona;
import com.veterinaria.laboratorio.api.dto.LabDtos.ResultadoResponse;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.laboratorio.internal.InformeRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository;
import com.veterinaria.laboratorio.internal.OrdenRepository.Filtro;
import com.veterinaria.laboratorio.internal.OrdenRepository.ItemRow;
import com.veterinaria.laboratorio.internal.OrdenRepository.OrdenRow;
import com.veterinaria.laboratorio.internal.ResultadoRepository;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.error.UnauthorizedException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/**
 * Ordenes de examen y su flujo de estados: SOLICITADA -> MUESTRA_TOMADA -> EN_PROCESO -> COMPLETADA (automatico al
 * validar todos los items) o CANCELADA. Toda transicion queda en la bitacora append-only de la orden (bandeja).
 */
@Service
public class OrdenService {

    private static final Set<String> ESTADOS = Set.of("SOLICITADA", "MUESTRA_TOMADA", "EN_PROCESO", "COMPLETADA", "CANCELADA");
    private static final Set<String> TIPOS = Set.of("LAB", "IMAGEN", "PATOLOGIA", "FUNCIONAL");
    private static final Set<String> PRIORIDADES = Set.of("RUTINA", "URGENTE", "STAT");
    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrdenRepository ordenes;
    private final CatalogoRepository catalogo;
    private final ResultadoRepository resultados;
    private final InformeRepository informes;
    private final PacienteLector pacientes;
    private final SedePrincipal sede;
    private final ArchivosApi archivos;

    public OrdenService(OrdenRepository ordenes, CatalogoRepository catalogo, ResultadoRepository resultados,
            InformeRepository informes, PacienteLector pacientes, SedePrincipal sede, ArchivosApi archivos) {
        this.ordenes = ordenes;
        this.catalogo = catalogo;
        this.resultados = resultados;
        this.informes = informes;
        this.pacientes = pacientes;
        this.sede = sede;
        this.archivos = archivos;
    }

    // ------------------------------------------------------------------ crear

    @Transactional
    public OrdenResponse crear(OrdenRequest r) {
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        pacientes.obtener(r.pacienteId());
        if (r.consultaId() != null) {
            var consulta = ordenes.consulta(r.consultaId())
                    .orElseThrow(() -> new BusinessException("La consulta " + r.consultaId() + " no existe"));
            if (!consulta.pacienteId().equals(r.pacienteId())) {
                throw new BusinessException("La consulta " + r.consultaId() + " no es del paciente");
            }
        }
        String prioridad = r.prioridad() == null ? "RUTINA" : r.prioridad();
        Set<Long> examenes = new LinkedHashSet<>();
        if (r.examenIds() != null) {
            examenes.addAll(r.examenIds());
        }
        if (r.examenCodigos() != null) {
            for (String codigo : r.examenCodigos()) {
                examenes.add(catalogo.idPorCodigo(codigo.trim())
                        .orElseThrow(() -> new BusinessException("El examen " + codigo + " no existe en el catalogo")));
            }
        }
        if (examenes.isEmpty()) {
            throw new BusinessException("Indique al menos un examen (examenIds o examenCodigos)");
        }
        List<ExamenResponse> elegidos = new ArrayList<>();
        for (Long id : examenes) {
            ExamenResponse e = catalogo.detalle(id).orElseThrow(() -> new BusinessException("El examen " + id + " no existe"));
            if (!e.activo()) {
                throw new BusinessException("El examen " + e.codigo() + " esta inactivo");
            }
            elegidos.add(e);
        }
        long id = ordenes.siguienteId();
        String codigo = "ORD-%s-%05d".formatted(ZonaClinica.hoy().format(FECHA_CODIGO), id);
        ordenes.insertarOrden(id, codigo, sede.id(), r.pacienteId(), r.consultaId(), usuario, prioridad,
                Textos.limpiar(r.laboratorioExterno()), Textos.limpiar(r.indicacionClinica()));
        for (ExamenResponse e : elegidos) {
            ordenes.insertarItem(id, e.id());
        }
        ordenes.evento(id, null, "CREADA", null, "SOLICITADA",
                "Prioridad " + prioridad + "; examenes: " + String.join(", ", elegidos.stream().map(ExamenResponse::codigo).toList()),
                usuario, "MANUAL");
        return obtener(id);
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public PageResponse<OrdenResumen> listar(String estado, String prioridad, Long pacienteId, Long consultaId, String tipo,
            LocalDate desde, LocalDate hasta, String q, Pageable pageable) {
        String estados = null;
        if (estado != null && !estado.isBlank()) {
            List<String> lista = List.of(estado.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
            for (String e : lista) {
                if (!ESTADOS.contains(e)) {
                    throw new BusinessException("Estado no valido: " + e);
                }
            }
            estados = String.join(",", lista);
        }
        if (prioridad != null && !PRIORIDADES.contains(prioridad)) {
            throw new BusinessException("Prioridad no valida: " + prioridad);
        }
        if (tipo != null && !TIPOS.contains(tipo)) {
            throw new BusinessException("Tipo de examen no valido: " + tipo);
        }
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new BusinessException("'hasta' no puede ser anterior a 'desde'");
        }
        Filtro f = new Filtro(estados, prioridad, pacienteId, consultaId, tipo,
                desde == null ? null : ZonaClinica.instante(desde, LocalTime.MIN).atOffset(ZoneOffset.UTC),
                hasta == null ? null : ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN).atOffset(ZoneOffset.UTC),
                q == null || q.isBlank() ? null : "%" + q.trim() + "%");
        long total = ordenes.contar(f);
        List<OrdenResumen> items = ordenes.pagina(f, pageable.getPageSize(), pageable.getOffset());
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id) {
        OrdenRow o = orden(id);
        PacienteBasico p = pacientes.obtener(o.pacienteId());
        List<ItemResponse> items = ordenes.items(id).stream().map(this::item).toList();
        boolean criticos = items.stream().flatMap(i -> i.resultados().stream()).anyMatch(ResultadoResponse::critico);
        List<ArchivosApi.ArchivoInfo> deLaOrden = archivos.listarPorOrden(id).stream().filter(a -> a.ordenItemId() == null).toList();
        return new OrdenResponse(o.id(), o.codigo(), o.pacienteId(), p.nombre(), p.codigo(), p.especie(), o.consultaId(),
                o.prioridad(), o.estado(), o.indicacion(), o.laboratorioExterno(), o.solicitadaEn(),
                o.solicitadoPor() == null ? null : new Persona(o.solicitadoPor(), o.solicitadoNombre()), o.muestraEn(),
                o.muestraPor() == null ? null : new Persona(o.muestraPor(), o.muestraPorNombre()), o.muestraObs(),
                o.completadaEn(), o.canceladaEn(), o.motivoCancelacion(), criticos, items, ordenes.eventos(id), deLaOrden);
    }

    /** Item con sus resultados vigentes y corregidos, informes y archivos. */
    @Transactional(readOnly = true)
    public ItemResponse itemResponse(Long ordenId, Long itemId) {
        return item(ordenes.item(ordenId, itemId).orElseThrow(() -> new ResourceNotFoundException("Item de orden", itemId)));
    }

    ItemResponse item(ItemRow it) {
        List<ResultadoResponse> todos = resultados.porItem(it.id());
        List<ResultadoResponse> vigentes = todos.stream().filter(r -> !"CORREGIDO".equals(r.estado())).toList();
        List<ResultadoResponse> corregidos = todos.stream().filter(r -> "CORREGIDO".equals(r.estado())).toList();
        TipoExamen kind = TipoExamen.de(it.tipo());
        return new ItemResponse(it.id(), it.examenId(), it.examenCodigo(), it.examenNombre(), it.tipo(), it.estado(),
                it.interpretacion(), it.validadoPor() == null ? null : new Persona(it.validadoPor(), it.validadoNombre()),
                it.validadoEn(), it.version(), it.version() > 1,
                it.capturadoPor() == null ? null : new Persona(it.capturadoPor(), it.capturadoNombre()), it.capturadoEn(),
                (int) catalogo.parametrosInfo(it.examenId()).stream().filter(p -> p.activo()).count(), vigentes, corregidos,
                kind == TipoExamen.IMAGEN ? informes.imagenVigente(it.id()).orElse(null) : null,
                kind == TipoExamen.PATOLOGIA ? informes.patologiaVigente(it.id()).orElse(null) : null,
                archivos.listarPorItem(it.id()), it.motivoCancelacion());
    }

    OrdenRow orden(Long id) {
        return ordenes.orden(id).orElseThrow(() -> new ResourceNotFoundException("Orden de examen", id));
    }

    // ------------------------------------------------------------------ flujo

    @Transactional
    public OrdenResponse registrarMuestra(Long id, MuestraRequest r) {
        ordenes.bloquear(id);
        OrdenRow o = orden(id);
        if (!"SOLICITADA".equals(o.estado())) {
            throw new ConflictException("La orden " + o.codigo() + " esta " + o.estado() + "; la muestra solo se toma en SOLICITADA");
        }
        if (ordenes.items(id).stream().noneMatch(i -> TipoExamen.de(i.tipo()).requiereMuestra() && !"CANCELADO".equals(i.estado()))) {
            throw new BusinessException("La orden no tiene examenes que requieran muestra; pase a EN_PROCESO");
        }
        Long usuario = UsuarioActual.id().orElse(null);
        String obs = r == null ? null : Textos.limpiar(r.observaciones());
        ordenes.registrarMuestra(id, usuario, obs);
        ordenes.evento(id, null, "MUESTRA", "SOLICITADA", "MUESTRA_TOMADA", obs, usuario, "MANUAL");
        return obtener(id);
    }

    @Transactional
    public OrdenResponse cambiarEstado(Long id, EstadoOrdenRequest r) {
        ordenes.bloquear(id);
        OrdenRow o = orden(id);
        Long usuario = UsuarioActual.id().orElse(null);
        switch (r.estado()) {
            case "MUESTRA_TOMADA" -> {
                return registrarMuestra(id, new MuestraRequest(r.motivo()));
            }
            case "EN_PROCESO" -> {
                if ("EN_PROCESO".equals(o.estado())) {
                    throw new ConflictException("La orden ya esta EN_PROCESO");
                }
                if (!List.of("SOLICITADA", "MUESTRA_TOMADA").contains(o.estado())) {
                    throw new ConflictException("La orden esta " + o.estado() + " y no puede pasar a EN_PROCESO");
                }
                boolean requiereMuestra = ordenes.items(id).stream()
                        .anyMatch(i -> TipoExamen.de(i.tipo()).requiereMuestra() && !"CANCELADO".equals(i.estado()));
                if ("SOLICITADA".equals(o.estado()) && requiereMuestra) {
                    throw new ConflictException("Registre primero la toma de muestra");
                }
                ordenes.cambiarEstado(id, "EN_PROCESO");
                ordenes.evento(id, null, "ESTADO", o.estado(), "EN_PROCESO", Textos.limpiar(r.motivo()), usuario, "MANUAL");
            }
            case "CANCELADA" -> {
                String motivo = Textos.limpiar(r.motivo());
                if (motivo == null) {
                    throw new BusinessException("El motivo de cancelacion es obligatorio");
                }
                if ("COMPLETADA".equals(o.estado()) || "CANCELADA".equals(o.estado())) {
                    throw new ConflictException("La orden esta " + o.estado() + " y no se puede cancelar");
                }
                if (ordenes.itemsValidados(id) > 0) {
                    throw new ConflictException("La orden tiene examenes ya validados; no se cancela (corrija el examen si hay un error)");
                }
                ordenes.cancelarItemsPendientes(id, motivo);
                ordenes.cancelar(id, usuario, motivo);
                ordenes.evento(id, null, "CANCELACION", o.estado(), "CANCELADA", motivo, usuario, "MANUAL");
            }
            default -> throw new BusinessException("Estado no permitido: " + r.estado());
        }
        return obtener(id);
    }

    /** Pasa SOLICITADA/MUESTRA_TOMADA a EN_PROCESO al capturar el primer resultado o informe. */
    void iniciarProceso(OrdenRow o, Long usuario, String origen) {
        if ("MUESTRA_TOMADA".equals(o.estado()) || "SOLICITADA".equals(o.estado())) {
            ordenes.cambiarEstado(o.id(), "EN_PROCESO");
            ordenes.evento(o.id(), null, "ESTADO", o.estado(), "EN_PROCESO", "Primer resultado registrado", usuario, origen);
        }
    }

    /** COMPLETADA cuando todos los items no cancelados estan validados. */
    void completarSiCorresponde(Long ordenId, Long usuario) {
        OrdenRow o = orden(ordenId);
        if (!"COMPLETADA".equals(o.estado()) && !"CANCELADA".equals(o.estado())
                && ordenes.itemsPendientesDeValidar(ordenId) == 0 && ordenes.itemsValidados(ordenId) > 0) {
            ordenes.completar(ordenId);
            ordenes.evento(ordenId, null, "ESTADO", o.estado(), "COMPLETADA", "Todos los examenes validados", usuario, "MANUAL");
        }
    }
}
