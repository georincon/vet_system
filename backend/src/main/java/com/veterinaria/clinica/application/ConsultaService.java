package com.veterinaria.clinica.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.application.CitaService;
import com.veterinaria.citas.domain.Cita;
import com.veterinaria.citas.internal.CitaRepository;
import com.veterinaria.clinica.ConsultaCerrada;
import com.veterinaria.clinica.api.dto.ConsultaMedicamentoDto;
import com.veterinaria.clinica.api.dto.ConsultaRequest;
import com.veterinaria.clinica.api.dto.ConsultaResponse;
import com.veterinaria.clinica.api.dto.ConsultaVacunaDto;
import com.veterinaria.clinica.api.dto.IniciarConsultaRequest;
import com.veterinaria.clinica.domain.Consulta;
import com.veterinaria.clinica.domain.ConsultaMedicamento;
import com.veterinaria.clinica.domain.ConsultaVacuna;
import com.veterinaria.clinica.domain.Diagnostico;
import com.veterinaria.clinica.domain.PlantillaSoap;
import com.veterinaria.clinica.internal.ConsultaRepository;
import com.veterinaria.clinica.internal.PlantillaSoapRepository;
import com.veterinaria.seguridad.application.VeterinarioLookup;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.internal.PacienteRepository;

/** Casos de uso del ciclo de vida de la consulta: alta, edicion, inicio desde cita y cierre. */
@Service
public class ConsultaService {

    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ConsultaRepository consultaRepository;
    private final CitaRepository citaRepository;
    private final CitaService citaService;
    private final PacienteRepository pacienteRepository;
    private final PlantillaSoapRepository plantillaRepository;
    private final VeterinarioLookup veterinarioLookup;
    private final PesoService pesoService;
    private final SedePrincipal sedePrincipal;
    private final ApplicationEventPublisher eventos;

    public ConsultaService(ConsultaRepository consultaRepository, CitaRepository citaRepository,
            CitaService citaService, PacienteRepository pacienteRepository,
            PlantillaSoapRepository plantillaRepository, VeterinarioLookup veterinarioLookup,
            PesoService pesoService, SedePrincipal sedePrincipal, ApplicationEventPublisher eventos) {
        this.consultaRepository = consultaRepository;
        this.citaRepository = citaRepository;
        this.citaService = citaService;
        this.pacienteRepository = pacienteRepository;
        this.plantillaRepository = plantillaRepository;
        this.veterinarioLookup = veterinarioLookup;
        this.pesoService = pesoService;
        this.sedePrincipal = sedePrincipal;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConsultaResponse> list(LocalDate fecha, Long pacienteId, String estado, Pageable pageable) {
        return PageResponse.from(consultaRepository.buscar(fecha, pacienteId, estado, pageable),
                ConsultaResponse::from);
    }

    @Transactional(readOnly = true)
    public ConsultaResponse find(Long id) {
        return ConsultaResponse.from(obtener(id));
    }

    @Transactional
    public ConsultaResponse create(ConsultaRequest r) {
        Consulta consulta = new Consulta();
        consulta.setCodigo(generarCodigo());
        consulta.setSedeId(sedePrincipal.id());
        consulta.setPaciente(obtenerPaciente(r.pacienteId()));
        consulta.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        consulta.setFecha(r.fecha());
        consulta.setFechaHora(r.fecha().atStartOfDay(ZonaClinica.ZONA).toInstant());
        vincularCita(consulta, r.citaId());
        aplicarDatosClinicos(consulta, r);
        reemplazarMedicamentos(consulta, r.medicamentos());
        reemplazarVacunas(consulta, r.vacunas());
        Consulta guardada = consultaRepository.saveAndFlush(consulta);
        if (r.pesoKg() != null) {
            pesoService.registrar(guardada.getPaciente(), r.pesoKg(), guardada.getFecha());
        }
        return ConsultaResponse.from(guardada);
    }

    @Transactional
    public ConsultaResponse update(Long id, ConsultaRequest r) {
        Consulta consulta = obtenerAbierta(id);
        if (r.pacienteId() != null) {
            consulta.setPaciente(obtenerPaciente(r.pacienteId()));
        }
        if (r.veterinarioId() != null) {
            consulta.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        }
        if (r.fecha() != null) {
            consulta.setFecha(r.fecha());
            consulta.setFechaHora(r.fecha().atStartOfDay(ZonaClinica.ZONA).toInstant());
        }
        if (r.citaId() != null) {
            vincularCita(consulta, r.citaId());
        } else {
            verificarCitaDelPaciente(consulta);
        }
        boolean pesoNuevo = r.pesoKg() != null && r.pesoKg().compareTo(
                consulta.getPesoKg() == null ? java.math.BigDecimal.ZERO : consulta.getPesoKg()) != 0;
        aplicarDatosClinicos(consulta, r);
        reemplazarMedicamentos(consulta, r.medicamentos());
        reemplazarVacunas(consulta, r.vacunas());
        Consulta guardada = consultaRepository.saveAndFlush(consulta);
        if (pesoNuevo) {
            pesoService.registrar(guardada.getPaciente(), r.pesoKg(), guardada.getFecha());
        }
        return ConsultaResponse.from(guardada);
    }

    /**
     * Crea la consulta (ABIERTA) de una cita y la pone EN_CURSO. 409 si la cita ya tiene consulta o esta
     * cancelada/atendida.
     */
    @Transactional
    public ConsultaResponse iniciarDesdeCita(Long citaId, IniciarConsultaRequest r) {
        if (!citaRepository.existsById(citaId)) {
            throw new ResourceNotFoundException("Cita", citaId);
        }
        if (consultaRepository.existsByCitaId(citaId)) {
            throw new ConflictException("La cita ya tiene una consulta registrada");
        }
        Cita cita = citaService.iniciarAtencion(citaId);
        Consulta consulta = new Consulta();
        consulta.setCodigo(generarCodigo());
        consulta.setSedeId(cita.getSedeId() != null ? cita.getSedeId() : sedePrincipal.id());
        consulta.setCita(cita);
        consulta.setPaciente(cita.getPaciente());
        consulta.setVeterinario(cita.getVeterinario());
        consulta.setFecha(ZonaClinica.hoy());
        consulta.setFechaHora(Instant.now());
        consulta.setMotivo(cita.getMotivo());
        if (r != null && r.tipoConsulta() != null) {
            consulta.setTipoConsulta(r.tipoConsulta());
        }
        if (r != null && r.plantillaId() != null) {
            aplicarPlantilla(consulta, obtenerPlantilla(r.plantillaId()));
        }
        return ConsultaResponse.from(consultaRepository.saveAndFlush(consulta));
    }

    /**
     * Cierra la consulta: los hijos (signos, diagnosticos, recetas...) ya estan persistidos y se vuelcan
     * primero; despues un unico UPDATE la pasa a CERRADA (el trigger de la BD solo admite ABIERTA -> CERRADA).
     * La cita vinculada pasa a ATENDIDA. Desde aqui la consulta es inmutable.
     */
    @Transactional
    public ConsultaResponse cerrar(Long id) {
        Consulta consulta = obtenerAbierta(id);
        consultaRepository.flush();
        Instant ahora = Instant.now();
        consulta.setEstado(Consulta.ESTADO_CERRADA);
        consulta.setCerradaEn(ahora);
        consulta.setCerradaPor(UsuarioActual.id().orElse(null));
        consulta.setFirmaHash(firma(consulta, ahora));
        Consulta cerrada = consultaRepository.saveAndFlush(consulta);
        if (cerrada.getCita() != null) {
            citaService.marcarAtendida(cerrada.getCita().getId());
        }
        // Las vacunas de la consulta pasan a inmunizaciones (modulo inmunizacion) tras confirmarse el cierre.
        eventos.publishEvent(new ConsultaCerrada(cerrada.getId(), cerrada.getPaciente().getId(),
                cerrada.getVeterinario().getId(), cerrada.getFecha(),
                cerrada.getVacunas().stream().map(v -> new ConsultaCerrada.VacunaAplicada(v.getId(), v.getNombre(),
                        v.getLote(), v.getFechaAplicacion(), v.getProximaFecha())).toList()));
        return ConsultaResponse.from(cerrada);
    }

    private Consulta obtener(Long id) {
        return consultaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consulta", id));
    }

    private Consulta obtenerAbierta(Long id) {
        Consulta consulta = obtener(id);
        if (consulta.estaCerrada()) {
            throw new ConflictException("La consulta " + consulta.getCodigo()
                    + " esta CERRADA y es inmutable; registre un addendum");
        }
        return consulta;
    }

    private Paciente obtenerPaciente(Long id) {
        return pacienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }

    private PlantillaSoap obtenerPlantilla(Long id) {
        return plantillaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PlantillaSoap", id));
    }

    private static void aplicarPlantilla(Consulta c, PlantillaSoap p) {
        c.setPlantillaId(p.getId());
        c.setSubjetivo(p.getSubjetivo());
        c.setObjetivo(p.getObjetivo());
        c.setEvaluacion(p.getEvaluacion());
        c.setPlan(p.getPlan());
    }

    /** Asocia la cita (si se indica) verificando que sea del mismo paciente y que no tenga ya consulta. */
    private void vincularCita(Consulta consulta, Long citaId) {
        if (citaId == null) {
            return;
        }
        Cita cita = citaRepository.findById(citaId).orElseThrow(() -> new ResourceNotFoundException("Cita", citaId));
        boolean usada = consulta.getId() == null
                ? consultaRepository.existsByCitaId(citaId)
                : consultaRepository.existsByCitaIdAndIdNot(citaId, consulta.getId());
        if (usada) {
            throw new ConflictException("La cita ya tiene una consulta registrada");
        }
        consulta.setCita(cita);
        verificarCitaDelPaciente(consulta);
    }

    private static void verificarCitaDelPaciente(Consulta consulta) {
        Cita cita = consulta.getCita();
        if (cita != null && !cita.getPaciente().getId().equals(consulta.getPaciente().getId())) {
            throw new BusinessException("La cita no corresponde al paciente de la consulta");
        }
    }

    private void aplicarDatosClinicos(Consulta c, ConsultaRequest r) {
        c.setMotivo(Textos.actualizar(c.getMotivo(), r.motivo()));
        c.setSintomatologia(Textos.actualizar(c.getSintomatologia(), r.sintomatologia()));
        c.setDiagnostico(Textos.actualizar(c.getDiagnostico(), r.diagnostico()));
        c.setTratamiento(Textos.actualizar(c.getTratamiento(), r.tratamiento()));
        c.setObservaciones(Textos.actualizar(c.getObservaciones(), r.observaciones()));
        c.setAnamnesis(Textos.actualizar(c.getAnamnesis(), r.anamnesis()));
        c.setSubjetivo(Textos.actualizar(c.getSubjetivo(), r.subjetivo()));
        c.setObjetivo(Textos.actualizar(c.getObjetivo(), r.objetivo()));
        c.setEvaluacion(Textos.actualizar(c.getEvaluacion(), r.evaluacion()));
        c.setPlan(Textos.actualizar(c.getPlan(), r.plan()));
        c.setPronostico(Textos.actualizar(c.getPronostico(), r.pronostico()));
        if (r.pesoKg() != null) {
            c.setPesoKg(r.pesoKg());
        }
        if (r.temperaturaC() != null) {
            c.setTemperaturaC(r.temperaturaC());
        }
        if (r.frecuenciaCardiaca() != null) {
            c.setFrecuenciaCardiaca(r.frecuenciaCardiaca());
        }
        if (r.frecuenciaRespiratoria() != null) {
            c.setFrecuenciaRespiratoria(r.frecuenciaRespiratoria());
        }
        if (r.tipoConsulta() != null) {
            c.setTipoConsulta(r.tipoConsulta());
        }
        if (r.triaje() != null) {
            c.setTriaje(r.triaje());
        }
        if (r.proximaRevision() != null) {
            c.setProximaRevision(r.proximaRevision());
        }
        if (r.plantillaId() != null) {
            c.setPlantillaId(obtenerPlantilla(r.plantillaId()).getId());
        }
    }

    private static void reemplazarMedicamentos(Consulta consulta, List<ConsultaMedicamentoDto> dtos) {
        if (dtos == null) {
            return;
        }
        consulta.getMedicamentos().clear();
        int orden = 1;
        for (ConsultaMedicamentoDto dto : dtos) {
            ConsultaMedicamento m = new ConsultaMedicamento();
            m.setConsulta(consulta);
            m.setNombre(dto.nombre().trim());
            m.setDosis(Textos.limpiar(dto.dosis()));
            m.setFrecuencia(Textos.limpiar(dto.frecuencia()));
            m.setDuracion(Textos.limpiar(dto.duracion()));
            m.setVia(Textos.limpiar(dto.via()));
            m.setObservaciones(Textos.limpiar(dto.observaciones()));
            m.setOrden(dto.orden() != null ? dto.orden() : orden);
            consulta.getMedicamentos().add(m);
            orden++;
        }
    }

    private static void reemplazarVacunas(Consulta consulta, List<ConsultaVacunaDto> dtos) {
        if (dtos == null) {
            return;
        }
        consulta.getVacunas().clear();
        for (ConsultaVacunaDto dto : dtos) {
            ConsultaVacuna v = new ConsultaVacuna();
            v.setConsulta(consulta);
            v.setNombre(dto.nombre().trim());
            v.setLote(Textos.limpiar(dto.lote()));
            v.setFechaAplicacion(dto.fechaAplicacion());
            v.setProximaFecha(dto.proximaFecha());
            consulta.getVacunas().add(v);
        }
    }

    /** SHA-256 (hex, 64 car.) del contenido clinico al momento del cierre. */
    private static String firma(Consulta c, Instant cierre) {
        String diagnosticos = c.getDiagnosticos().stream().map(Diagnostico::getDescripcion)
                .collect(Collectors.joining(";"));
        String contenido = String.join("|", String.valueOf(c.getId()), c.getCodigo(),
                String.valueOf(c.getPaciente().getId()), String.valueOf(c.getVeterinario().getId()),
                String.valueOf(c.getFecha()), String.valueOf(c.getMotivo()), String.valueOf(c.getSubjetivo()),
                String.valueOf(c.getObjetivo()), String.valueOf(c.getEvaluacion()), String.valueOf(c.getPlan()),
                String.valueOf(c.getDiagnostico()), String.valueOf(c.getTratamiento()), diagnosticos,
                String.valueOf(cierre));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Codigo CON-YYYYMMDD-NNNN con consecutivo diario. */
    private String generarCodigo() {
        String prefijo = "CON-" + ZonaClinica.hoy().format(FECHA_CODIGO) + "-";
        return prefijo + "%04d".formatted(consultaRepository.countByCodigoStartingWith(prefijo) + 1);
    }
}
