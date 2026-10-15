package com.veterinaria.procedimientos.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.archivos.ArchivosApi;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.EstadoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.Persona;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoRequest;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResumen;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoUpdate;
import com.veterinaria.procedimientos.internal.ProcedimientoRepository;
import com.veterinaria.procedimientos.internal.ProcedimientoRepository.Fila;
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
 * Registro de procedimientos ambulatorios y cirugias. Regla de seguridad clinica: una CIRUGIA no pasa a EN_CURSO ni
 * REALIZADA sin consentimiento informado FIRMADO del tutor, salvo urgencia declarada.
 */
@Service
public class ProcedimientoService {

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "PROGRAMADA", Set.of("EN_CURSO", "REALIZADA", "CANCELADA", "APLAZADA"),
            "APLAZADA", Set.of("PROGRAMADA", "CANCELADA"),
            "EN_CURSO", Set.of("REALIZADA", "CANCELADA"),
            "REALIZADA", Set.of(),
            "CANCELADA", Set.of());
    private static final List<String> ROLES_CLINICOS = List.of("VETERINARIO", "ADMIN");
    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.BASIC_ISO_DATE;

    private final ProcedimientoRepository repository;
    private final PacienteLector pacientes;
    private final SedePrincipal sede;
    private final ArchivosApi archivos;

    public ProcedimientoService(ProcedimientoRepository repository, PacienteLector pacientes, SedePrincipal sede,
            ArchivosApi archivos) {
        this.repository = repository;
        this.pacientes = pacientes;
        this.sede = sede;
        this.archivos = archivos;
    }

    // ------------------------------------------------------------------ alta y edicion

    @Transactional
    public ProcedimientoResponse crear(ProcedimientoRequest r) {
        Long usuario = UsuarioActual.id().orElseThrow(() -> new UnauthorizedException("Sesion sin identificador de usuario."));
        pacientes.obtener(r.pacienteId());
        if (r.consultaId() != null && repository.consultaDePaciente(r.consultaId(), r.pacienteId()).isEmpty()) {
            throw new BusinessException("La consulta " + r.consultaId() + " no existe o no es del paciente");
        }
        String tipo = r.tipo() == null ? "PROCEDIMIENTO" : r.tipo();
        String nombre = Textos.limpiar(r.procedimiento());
        if (nombre == null && r.itemId() != null) {
            nombre = repository.itemNombre(r.itemId()).orElseThrow(() -> new BusinessException("El item " + r.itemId() + " no existe"));
        }
        if (nombre == null) {
            throw new BusinessException("Indique el procedimiento (nombre) o un item del catalogo de servicios");
        }
        if (r.itemId() != null && repository.itemNombre(r.itemId()).isEmpty()) {
            throw new BusinessException("El item " + r.itemId() + " no existe");
        }
        Long cirujano = r.veterinarioId() != null ? r.veterinarioId() : usuario;
        verificarUsuario(cirujano, ROLES_CLINICOS, "El responsable debe ser un veterinario o administrador activo");
        if (r.ayudanteId() != null) {
            verificarUsuario(r.ayudanteId(), List.of(), "El ayudante no existe o esta inactivo");
        }
        if (r.anestesiologoId() != null) {
            verificarUsuario(r.anestesiologoId(), List.of(), "El anestesiologo no existe o esta inactivo");
        }
        String estado = r.estado() != null ? r.estado() : ("CIRUGIA".equals(tipo) ? "PROGRAMADA" : "REALIZADA");
        boolean urgencia = Boolean.TRUE.equals(r.urgencia());
        if ("CIRUGIA".equals(tipo) && Set.of("EN_CURSO", "REALIZADA").contains(estado) && !urgencia) {
            throw new BusinessException("Una cirugia requiere consentimiento informado firmado antes de iniciar: "
                    + "registrela como PROGRAMADA, firme el consentimiento y luego inicie (o marque urgencia)");
        }
        Instant inicio = instante(r.inicioEn());
        Instant fin = instante(r.finEn());
        validarFechas(inicio, fin);
        if ("REALIZADA".equals(estado) && fin == null) {
            fin = inicio != null ? inicio : Instant.now();
            if (inicio == null) {
                inicio = fin;
            }
        }
        if ("EN_CURSO".equals(estado) && inicio == null) {
            inicio = Instant.now();
        }

        long id = repository.siguienteId();
        String codigo = ("CIRUGIA".equals(tipo) ? "CIR" : "PRC") + "-%s-%05d".formatted(ZonaClinica.hoy().format(FECHA_CODIGO), id);
        Map<String, Object> p = new HashMap<>();
        p.put("paciente", r.pacienteId());
        p.put("consulta", r.consultaId());
        p.put("item", r.itemId());
        p.put("tipo", tipo);
        p.put("procedimiento", nombre);
        p.put("cirujano", cirujano);
        p.put("ayudante", r.ayudanteId());
        p.put("anestesiologo", r.anestesiologoId());
        p.put("programada", odt(instante(r.programadaPara())));
        p.put("inicio", odt(inicio));
        p.put("fin", odt(fin));
        p.put("asa", r.asa());
        p.put("urgencia", urgencia);
        p.put("ayuno", r.ayunoHoras());
        p.put("peso", r.pesoKg());
        p.put("anestesiaTipo", r.anestesiaTipo());
        p.put("premedicacion", Textos.limpiar(r.premedicacion()));
        p.put("induccion", Textos.limpiar(r.induccion()));
        p.put("mantenimiento", Textos.limpiar(r.mantenimiento()));
        p.put("analgesia", Textos.limpiar(r.analgesia()));
        p.put("dxPre", Textos.limpiar(r.diagnosticoPreoperatorio()));
        p.put("tecnica", Textos.limpiar(r.descripcionTecnica()));
        p.put("hallazgos", Textos.limpiar(r.hallazgos()));
        p.put("complicaciones", Textos.limpiar(r.complicaciones()));
        p.put("sutura", Textos.limpiar(r.materialSutura()));
        p.put("histo", Boolean.TRUE.equals(r.muestrasHistopatologia()));
        p.put("dxPost", Textos.limpiar(r.diagnosticoPostoperatorio()));
        p.put("resultado", Textos.limpiar(r.resultado()));
        p.put("cuidados", Textos.limpiar(r.indicacionesPostoperatorias()));
        p.put("controles", Textos.limpiar(r.controles()));
        p.put("proximoControl", r.proximoControl());
        p.put("estado", estado);
        p.put("creadoPor", usuario);
        repository.insertar(id, codigo, sede.id(), p);
        if (r.insumos() != null && !r.insumos().isEmpty()) {
            repository.reemplazarInsumos(id, r.insumos());
        }
        return obtener(id);
    }

    @Transactional
    public ProcedimientoResponse actualizar(Long id, ProcedimientoUpdate u) {
        Fila f = fila(id);
        if ("CANCELADA".equals(f.estado())) {
            throw new ConflictException("El registro " + f.codigo() + " esta CANCELADO y no se edita");
        }
        if (u.itemId() != null && repository.itemNombre(u.itemId()).isEmpty()) {
            throw new BusinessException("El item " + u.itemId() + " no existe");
        }
        if (u.veterinarioId() != null) {
            verificarUsuario(u.veterinarioId(), ROLES_CLINICOS, "El responsable debe ser un veterinario o administrador activo");
        }
        if (u.ayudanteId() != null) {
            verificarUsuario(u.ayudanteId(), List.of(), "El ayudante no existe o esta inactivo");
        }
        if (u.anestesiologoId() != null) {
            verificarUsuario(u.anestesiologoId(), List.of(), "El anestesiologo no existe o esta inactivo");
        }
        Instant inicio = u.inicioEn() != null ? instante(u.inicioEn()) : f.inicioEn();
        Instant fin = u.finEn() != null ? instante(u.finEn()) : f.finEn();
        validarFechas(inicio, fin);
        Map<String, Object> p = new HashMap<>();
        p.put("item", u.itemId());
        p.put("procedimiento", Textos.limpiar(u.procedimiento()));
        p.put("cirujano", u.veterinarioId());
        p.put("ayudante", u.ayudanteId());
        p.put("anestesiologo", u.anestesiologoId());
        p.put("programada", odt(instante(u.programadaPara())));
        p.put("inicio", odt(instante(u.inicioEn())));
        p.put("fin", odt(instante(u.finEn())));
        p.put("asa", u.asa());
        p.put("urgencia", u.urgencia());
        p.put("ayuno", u.ayunoHoras());
        p.put("peso", u.pesoKg());
        p.put("anestesiaTipo", u.anestesiaTipo());
        p.put("premedicacion", Textos.limpiar(u.premedicacion()));
        p.put("induccion", Textos.limpiar(u.induccion()));
        p.put("mantenimiento", Textos.limpiar(u.mantenimiento()));
        p.put("analgesia", Textos.limpiar(u.analgesia()));
        p.put("dxPre", Textos.limpiar(u.diagnosticoPreoperatorio()));
        p.put("tecnica", Textos.limpiar(u.descripcionTecnica()));
        p.put("hallazgos", Textos.limpiar(u.hallazgos()));
        p.put("complicaciones", Textos.limpiar(u.complicaciones()));
        p.put("sutura", Textos.limpiar(u.materialSutura()));
        p.put("histo", u.muestrasHistopatologia());
        p.put("dxPost", Textos.limpiar(u.diagnosticoPostoperatorio()));
        p.put("resultado", Textos.limpiar(u.resultado()));
        p.put("cuidados", Textos.limpiar(u.indicacionesPostoperatorias()));
        p.put("controles", Textos.limpiar(u.controles()));
        p.put("proximoControl", u.proximoControl());
        repository.actualizar(id, p);
        if (u.insumos() != null) {
            repository.reemplazarInsumos(id, u.insumos());
        }
        return obtener(id);
    }

    @Transactional
    public ProcedimientoResponse cambiarEstado(Long id, EstadoRequest r) {
        Fila f = fila(id);
        if (!TRANSICIONES.getOrDefault(f.estado(), Set.of()).contains(r.estado())) {
            throw new ConflictException("No se puede pasar de " + f.estado() + " a " + r.estado());
        }
        String motivo = Textos.limpiar(r.motivo());
        if (Set.of("CANCELADA", "APLAZADA").contains(r.estado()) && motivo == null) {
            throw new BusinessException("El motivo es obligatorio para " + r.estado());
        }
        if ("CIRUGIA".equals(f.tipo()) && Set.of("EN_CURSO", "REALIZADA").contains(r.estado()) && !f.urgencia()
                && !"FIRMADO".equals(repository.estadoConsentimiento(f.consentimientoId()).orElse(null))) {
            throw new BusinessException("La cirugia requiere consentimiento informado FIRMADO antes de iniciar (o marcarla como urgencia)");
        }
        Instant ahora = Instant.now();
        Instant inicio = null;
        Instant fin = null;
        if ("EN_CURSO".equals(r.estado()) && f.inicioEn() == null) {
            inicio = ahora;
        }
        if ("REALIZADA".equals(r.estado())) {
            fin = f.finEn() == null ? ahora : null;
            if (f.inicioEn() == null) {
                inicio = ahora;
            }
        }
        repository.cambiarEstado(id, r.estado(), inicio == null ? null : inicio, fin, "CANCELADA".equals(r.estado()) ? motivo : null);
        return obtener(id);
    }

    // ------------------------------------------------------------------ monitoreo anestesico

    @Transactional
    public MonitoreoResponse registrarMonitoreo(Long id, MonitoreoRequest m) {
        Fila f = fila(id);
        if (!Set.of("PROGRAMADA", "EN_CURSO", "REALIZADA").contains(f.estado())) {
            throw new ConflictException("No se registra monitoreo en un procedimiento " + f.estado());
        }
        if (m.frecuenciaCardiaca() == null && m.frecuenciaRespiratoria() == null && m.spo2() == null && m.etco2() == null
                && m.presionSistolica() == null && m.temperatura() == null && m.agenteInhalatorioPct() == null
                && m.fluidoMlH() == null) {
            throw new BusinessException("Indique al menos un parametro del monitoreo");
        }
        Instant cuando = m.registradoEn() == null ? Instant.now() : instante(m.registradoEn());
        if (cuando.isAfter(Instant.now().plusSeconds(300))) {
            throw new BusinessException("La hora del monitoreo no puede ser futura");
        }
        long mid = repository.insertarMonitoreo(id, m, cuando, UsuarioActual.id().orElse(null));
        return repository.monitoreo(id).stream().filter(x -> x.id() == mid).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<MonitoreoResponse> monitoreo(Long id) {
        fila(id);
        return repository.monitoreo(id);
    }

    // ------------------------------------------------------------------ consentimiento

    @Transactional
    public ConsentimientoResponse registrarConsentimiento(Long id, ConsentimientoRequest r) {
        Fila f = fila(id);
        if (Set.of("CANCELADA", "REALIZADA").contains(f.estado())) {
            throw new ConflictException("El procedimiento esta " + f.estado() + "; el consentimiento se firma antes de realizarlo");
        }
        if ("FIRMADO".equals(repository.estadoConsentimiento(f.consentimientoId()).orElse(null))) {
            throw new ConflictException("El procedimiento ya tiene un consentimiento firmado");
        }
        Long tutor = r.tutorId() != null ? r.tutorId()
                : repository.tutorPrincipal(f.pacienteId()).orElseThrow(() -> new BusinessException("El paciente no tiene tutor registrado"));
        if (!repository.esTutorDe(tutor, f.pacienteId())) {
            throw new BusinessException("El tutor " + tutor + " no es tutor del paciente");
        }
        String tipoConsent = "CIRUGIA".equals(f.tipo()) ? "CIRUGIA_ANESTESIA" : "PROCEDIMIENTO";
        String texto = Textos.limpiar(r.texto());
        if (texto == null) {
            texto = repository.plantillaConsentimiento(tipoConsent)
                    .orElseThrow(() -> new BusinessException("No hay plantilla de consentimiento activa: envie el texto"));
        }
        String firmaRuta = null;
        String firmaHash = null;
        String metodo = r.metodoFirma();
        if (r.archivoId() != null) {
            var adj = archivos.buscarActivo(r.archivoId())
                    .orElseThrow(() -> new BusinessException("El archivo " + r.archivoId() + " no existe o fue eliminado"));
            if (!adj.pacienteId().equals(f.pacienteId())) {
                throw new BusinessException("El archivo firmado no es del paciente");
            }
            if (!"CONSENTIMIENTO".equals(adj.tipo())) {
                throw new BusinessException("El archivo firmado debe subirse con tipo CONSENTIMIENTO");
            }
            firmaRuta = "adjunto:" + adj.id();
            firmaHash = adj.hashSha256();
            if (metodo == null) {
                metodo = "PAPEL_ESCANEADO";
            }
        }
        boolean acepta = Boolean.TRUE.equals(r.aceptado());
        Instant firmadoEn = null;
        if (acepta) {
            firmadoEn = r.fecha() == null ? Instant.now() : instante(r.fecha());
            if (firmadoEn.isAfter(Instant.now().plusSeconds(300))) {
                throw new BusinessException("La fecha de firma no puede ser futura");
            }
        }
        long kid = repository.insertarConsentimiento(tipoConsent, tutor, f.pacienteId(), f.consultaId(), sede.id(),
                acepta ? "FIRMADO" : "RECHAZADO", texto, r.firmanteNombre().trim(), Textos.limpiar(r.firmanteDocumento()),
                firmaRuta, firmaHash, metodo, firmadoEn, UsuarioActual.id().orElse(null));
        repository.vincularConsentimiento(id, kid);
        return repository.consentimiento(kid).orElseThrow();
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public ProcedimientoResponse obtener(Long id) {
        Fila f = fila(id);
        PacienteBasico p = pacientes.obtener(f.pacienteId());
        return new ProcedimientoResponse(f.id(), f.codigo(), f.tipo(), f.estado(), f.pacienteId(), p.nombre(), p.codigo(),
                f.consultaId(), f.itemId(), f.procedimiento(), new Persona(f.cirujanoId(), f.cirujanoNombre()),
                f.ayudanteId() == null ? null : new Persona(f.ayudanteId(), f.ayudanteNombre()),
                f.anestesiologoId() == null ? null : new Persona(f.anestesiologoId(), f.anestesiologoNombre()),
                f.programadaPara(), f.inicioEn(), f.finEn(), f.asa(), f.urgencia(), f.ayunoHoras(), f.pesoKg(), f.anestesiaTipo(),
                f.premedicacion(), f.induccion(), f.mantenimiento(), f.analgesia(), f.diagnosticoPreoperatorio(),
                f.descripcionTecnica(), f.hallazgos(), f.complicaciones(), f.materialSutura(), f.muestrasHistopatologia(),
                f.diagnosticoPostoperatorio(), f.resultado(), f.indicacionesPostoperatorias(), f.controles(), f.proximoControl(),
                f.motivoCancelacion(), f.creadoEn(), repository.consentimiento(f.consentimientoId()).orElse(null),
                repository.insumos(id), repository.monitoreo(id), archivos.listarPorCirugia(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcedimientoResumen> listar(Long pacienteId, Long consultaId, String tipo, String estado, Pageable pageable) {
        if (tipo != null && !Set.of("PROCEDIMIENTO", "CIRUGIA").contains(tipo)) {
            throw new BusinessException("Tipo no valido: " + tipo);
        }
        if (estado != null && !TRANSICIONES.containsKey(estado)) {
            throw new BusinessException("Estado no valido: " + estado);
        }
        long total = repository.contar(pacienteId, consultaId, tipo, estado);
        List<ProcedimientoResumen> items = repository.pagina(pacienteId, consultaId, tipo, estado, pageable.getPageSize(), pageable.getOffset());
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    Fila fila(Long id) {
        return repository.buscar(id).orElseThrow(() -> new ResourceNotFoundException("Procedimiento", id));
    }

    // ------------------------------------------------------------------ utilidades

    private void verificarUsuario(Long id, List<String> roles, String mensaje) {
        if (repository.usuarioActivo(id, roles).isEmpty()) {
            throw new BusinessException(mensaje);
        }
    }

    private static Instant instante(LocalDateTime local) {
        return local == null ? null : ZonaClinica.instante(local);
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    private static void validarFechas(Instant inicio, Instant fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new BusinessException("La hora de fin no puede ser anterior a la de inicio");
        }
    }
}
