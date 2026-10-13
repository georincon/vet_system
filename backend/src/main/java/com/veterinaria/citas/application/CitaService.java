package com.veterinaria.citas.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.api.dto.CambioEstadoRequest;
import com.veterinaria.citas.api.dto.CitaRequest;
import com.veterinaria.citas.api.dto.CitaResponse;
import com.veterinaria.citas.api.dto.EstadoCitaResponse;
import com.veterinaria.citas.domain.BloqueoAgenda;
import com.veterinaria.citas.domain.Cita;
import com.veterinaria.citas.domain.EstadoCita;
import com.veterinaria.citas.domain.EstadoCitaCodigo;
import com.veterinaria.citas.domain.Sala;
import com.veterinaria.citas.domain.TipoCita;
import com.veterinaria.citas.internal.BloqueoAgendaRepository;
import com.veterinaria.citas.internal.CitaRepository;
import com.veterinaria.citas.internal.EstadoCitaRepository;
import com.veterinaria.citas.internal.SalaRepository;
import com.veterinaria.citas.internal.TipoCitaRepository;
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

/** Casos de uso de la agenda de citas. */
@Service
public class CitaService {

    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration DURACION_POR_DEFECTO = Duration.ofMinutes(30);
    private static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59);

    private final CitaRepository citaRepository;
    private final EstadoCitaRepository estadoCitaRepository;
    private final PacienteRepository pacienteRepository;
    private final VeterinarioLookup veterinarioLookup;
    private final TipoCitaRepository tipoCitaRepository;
    private final SalaRepository salaRepository;
    private final BloqueoAgendaRepository bloqueoRepository;
    private final SedePrincipal sedePrincipal;

    public CitaService(CitaRepository citaRepository, EstadoCitaRepository estadoCitaRepository,
            PacienteRepository pacienteRepository, VeterinarioLookup veterinarioLookup,
            TipoCitaRepository tipoCitaRepository, SalaRepository salaRepository,
            BloqueoAgendaRepository bloqueoRepository, SedePrincipal sedePrincipal) {
        this.citaRepository = citaRepository;
        this.estadoCitaRepository = estadoCitaRepository;
        this.pacienteRepository = pacienteRepository;
        this.veterinarioLookup = veterinarioLookup;
        this.tipoCitaRepository = tipoCitaRepository;
        this.salaRepository = salaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.sedePrincipal = sedePrincipal;
    }

    @Transactional(readOnly = true)
    public List<EstadoCitaResponse> estados() {
        return estadoCitaRepository.findByActivoTrueOrderByIdAsc().stream().map(EstadoCitaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CitaResponse> list(LocalDate fecha, Pageable pageable) {
        return PageResponse.from(
                fecha == null
                        ? citaRepository.findByActivoTrue(pageable)
                        : citaRepository.findByActivoTrueAndFecha(fecha, pageable),
                CitaResponse::from);
    }

    @Transactional(readOnly = true)
    public CitaResponse find(Long id) {
        return CitaResponse.from(obtener(id));
    }

    @Transactional
    public CitaResponse create(CitaRequest r) {
        if (r.fecha().isBefore(ZonaClinica.hoy())) {
            throw new BusinessException("La fecha de la cita no puede ser anterior a hoy");
        }
        Cita cita = new Cita();
        cita.setCodigo(generarCodigo());
        cita.setPaciente(obtenerPaciente(r.pacienteId()));
        cita.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        cita.setEstadoCita(r.estadoCitaId() == null ? estadoInicial() : obtenerEstado(r.estadoCitaId()));
        cita.setSedeId(sedePrincipal.id());
        TipoCita tipo = r.tipoCitaId() == null ? null : obtenerTipo(r.tipoCitaId());
        cita.setTipoCita(tipo);
        cita.setSala(r.salaId() == null ? null : obtenerSala(r.salaId()));
        cita.setMotivo(Textos.limpiar(r.motivo()));
        cita.setNotas(Textos.limpiar(r.notas()));
        LocalTime fin = r.horaFin() != null ? r.horaFin()
                : finPorDefecto(r.horaInicio(), tipo != null ? Duration.ofMinutes(tipo.getDuracionMinutos()) : DURACION_POR_DEFECTO);
        programar(cita, r.fecha(), r.horaInicio(), fin);
        verificarDisponibilidad(cita);
        return CitaResponse.from(citaRepository.saveAndFlush(cita));
    }

    @Transactional
    public CitaResponse update(Long id, CitaRequest r) {
        Cita cita = obtener(id);

        if (r.pacienteId() != null) {
            cita.setPaciente(obtenerPaciente(r.pacienteId()));
        }
        if (r.veterinarioId() != null) {
            cita.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        }
        if (r.estadoCitaId() != null && !r.estadoCitaId().equals(cita.getEstadoCita().getId())) {
            EstadoCita destino = obtenerEstado(r.estadoCitaId());
            aplicarEstado(cita, codigoDe(destino), null);
        }
        Duration duracion = duracionActual(cita);
        if (r.tipoCitaId() != null) {
            TipoCita tipo = obtenerTipo(r.tipoCitaId());
            cita.setTipoCita(tipo);
            duracion = Duration.ofMinutes(tipo.getDuracionMinutos());
        }
        if (Boolean.TRUE.equals(r.quitarSala())) {
            cita.setSala(null);
        } else if (r.salaId() != null) {
            cita.setSala(obtenerSala(r.salaId()));
        }
        cita.setMotivo(Textos.actualizar(cita.getMotivo(), r.motivo()));
        cita.setNotas(Textos.actualizar(cita.getNotas(), r.notas()));

        LocalDate fecha = r.fecha() != null ? r.fecha() : cita.getFecha();
        if (r.fecha() != null && !r.fecha().equals(cita.getFecha()) && r.fecha().isBefore(ZonaClinica.hoy())) {
            throw new BusinessException("La fecha de la cita no puede ser anterior a hoy");
        }
        LocalTime inicio = r.horaInicio() != null ? r.horaInicio() : cita.getHoraInicio();
        LocalTime fin = r.horaFin() != null ? r.horaFin()
                : (r.horaInicio() != null || r.tipoCitaId() != null) ? finPorDefecto(inicio, duracion)
                : cita.getHoraFin();
        programar(cita, fecha, inicio, fin);
        verificarDisponibilidad(cita);
        return CitaResponse.from(citaRepository.saveAndFlush(cita));
    }

    /** Cambia el estado validando la maquina de estados (409 si la transicion no es valida). */
    @Transactional
    public CitaResponse cambiarEstado(Long id, CambioEstadoRequest r) {
        Cita cita = obtener(id);
        EstadoCitaCodigo destino = EstadoCitaCodigo.parse(r.estado())
                .orElseThrow(() -> new BusinessException("Estado de cita desconocido: " + r.estado()));
        aplicarEstado(cita, destino, Textos.limpiar(r.motivo()));
        return CitaResponse.from(citaRepository.saveAndFlush(cita));
    }

    /**
     * Marca la cita en consulta (EN_CURSO) desde PROGRAMADA, CONFIRMADA o EN_SALA (atajo de recepcion:
     * si no hubo check-in se registra la llegada ahora). Lo usa el inicio de consulta.
     */
    @Transactional
    public Cita iniciarAtencion(Long id) {
        Cita cita = obtener(id);
        EstadoCitaCodigo actual = codigoDe(cita.getEstadoCita());
        if (actual != EstadoCitaCodigo.PROGRAMADA && actual != EstadoCitaCodigo.CONFIRMADA
                && actual != EstadoCitaCodigo.EN_SALA) {
            throw new ConflictException("La cita " + cita.getCodigo() + " esta " + actual
                    + " y no puede iniciar una consulta");
        }
        if (cita.getHoraLlegada() == null) {
            cita.setHoraLlegada(Instant.now());
        }
        cita.setEstadoCita(estadoPorCodigo(EstadoCitaCodigo.EN_CURSO));
        cita.setAtencionInicio(Instant.now());
        return citaRepository.saveAndFlush(cita);
    }

    /** Cierra la cita como ATENDIDA si esta en consulta; en otro caso no hace nada. */
    @Transactional
    public void marcarAtendida(Long id) {
        Cita cita = obtener(id);
        if (codigoDe(cita.getEstadoCita()) == EstadoCitaCodigo.EN_CURSO) {
            cita.setEstadoCita(estadoPorCodigo(EstadoCitaCodigo.ATENDIDA));
            citaRepository.saveAndFlush(cita);
        }
    }

    /** Aplica una transicion de estado y sus efectos (llegada, inicio de atencion, cancelacion). */
    private void aplicarEstado(Cita cita, EstadoCitaCodigo destino, String motivo) {
        EstadoCitaCodigo actual = codigoDe(cita.getEstadoCita());
        if (!actual.puedePasarA(destino)) {
            throw new ConflictException("Transicion de estado invalida: %s -> %s".formatted(actual, destino));
        }
        cita.setEstadoCita(estadoPorCodigo(destino));
        Instant ahora = Instant.now();
        switch (destino) {
            case CONFIRMADA -> cita.setConfirmadaEn(ahora);
            case EN_SALA -> cita.setHoraLlegada(ahora);
            case EN_CURSO -> cita.setAtencionInicio(ahora);
            case CANCELADA, NO_ASISTIO, REAGENDADA -> {
                cita.setCanceladaEn(ahora);
                cita.setCanceladaPor(UsuarioActual.id().orElse(null));
                cita.setMotivoCancelacion(motivo);
            }
            default -> { }
        }
    }

    private static EstadoCitaCodigo codigoDe(EstadoCita estado) {
        return EstadoCitaCodigo.parse(estado.getCodigo())
                .orElseThrow(() -> new IllegalStateException("Estado de cita no soportado: " + estado.getCodigo()));
    }

    private EstadoCita estadoPorCodigo(EstadoCitaCodigo codigo) {
        return estadoCitaRepository.findByCodigo(codigo.name())
                .orElseThrow(() -> new IllegalStateException("Estado de cita " + codigo + " no encontrado"));
    }

    private TipoCita obtenerTipo(Long id) {
        TipoCita tipo = tipoCitaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("TipoCita", id));
        if (!tipo.isActivo()) {
            throw new BusinessException("El tipo de cita seleccionado esta inactivo");
        }
        return tipo;
    }

    private Sala obtenerSala(Long id) {
        Sala sala = salaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sala", id));
        if (!sala.isActivo()) {
            throw new BusinessException("La sala seleccionada esta inactiva");
        }
        return sala;
    }

    private Cita obtener(Long id) {
        return citaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cita", id));
    }

    private Paciente obtenerPaciente(Long id) {
        return pacienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }

    private EstadoCita obtenerEstado(Integer id) {
        return estadoCitaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("EstadoCita", id));
    }

    private EstadoCita estadoInicial() {
        return estadoCitaRepository.findByCodigo(EstadoCitaCodigo.PROGRAMADA.name())
                .orElseThrow(() -> new IllegalStateException("Estado de cita PROGRAMADA no encontrado"));
    }

    /** Fija fecha y horas locales y los instantes equivalentes en la zona de la clinica. */
    private static void programar(Cita cita, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        if (!fin.isAfter(inicio)) {
            throw new BusinessException("La hora de fin debe ser posterior a la hora de inicio");
        }
        cita.setFecha(fecha);
        cita.setHoraInicio(inicio);
        cita.setHoraFin(fin);
        cita.setFechaHoraInicio(instante(fecha, inicio));
        cita.setFechaHoraFin(instante(fecha, fin));
    }

    private static Instant instante(LocalDate fecha, LocalTime hora) {
        return ZonaClinica.instante(fecha, hora);
    }

    private static Duration duracionActual(Cita cita) {
        return Duration.between(cita.getHoraInicio(), cita.getHoraFin());
    }

    /** Fin = inicio + duracion, acotado al final del mismo dia. */
    private static LocalTime finPorDefecto(LocalTime inicio, Duration duracion) {
        LocalTime fin = inicio.plus(duracion);
        return fin.isAfter(inicio) ? fin : FIN_DEL_DIA;
    }

    /** Rechaza la cita si el veterinario o el paciente ya tienen otra cita que se traslapa. */
    private void verificarDisponibilidad(Cita cita) {
        if (EstadoCitaCodigo.LIBERAN_AGENDA.contains(cita.getEstadoCita().getCodigo())) {
            return;
        }
        // Una cita nueva aun no tiene id: -1 nunca coincide con una fila existente.
        long propia = cita.getId() == null ? -1L : cita.getId();
        if (citaRepository.existeTraslapeVeterinario(cita.getVeterinario().getId(), cita.getFechaHoraInicio(),
                cita.getFechaHoraFin(), propia, EstadoCitaCodigo.LIBERAN_AGENDA)) {
            throw new ConflictException("El veterinario ya tiene una cita en ese horario");
        }
        if (citaRepository.existeTraslapePaciente(cita.getPaciente().getId(), cita.getFechaHoraInicio(),
                cita.getFechaHoraFin(), propia, EstadoCitaCodigo.LIBERAN_AGENDA)) {
            throw new ConflictException("El paciente ya tiene una cita en ese horario");
        }
        Long salaId = cita.getSala() == null ? null : cita.getSala().getId();
        if (salaId != null && citaRepository.existeTraslapeSala(salaId, cita.getFechaHoraInicio(),
                cita.getFechaHoraFin(), propia, EstadoCitaCodigo.LIBERAN_AGENDA)) {
            throw new ConflictException("La sala " + cita.getSala().getNombre() + " ya esta reservada en ese horario");
        }
        for (BloqueoAgenda b : bloqueoRepository.solapados(cita.getVeterinario().getId(), salaId,
                cita.getFechaHoraInicio(), cita.getFechaHoraFin())) {
            String detalle = b.getTipo() + (b.getMotivo() == null ? "" : ": " + b.getMotivo());
            if (b.getVeterinario() != null && b.getVeterinario().getId().equals(cita.getVeterinario().getId())) {
                throw new ConflictException("El veterinario tiene un bloqueo de agenda en ese horario (" + detalle + ")");
            }
            throw new ConflictException("La sala esta bloqueada en ese horario (" + detalle + ")");
        }
    }

    /** Codigo CIT-YYYYMMDD-NNNN con consecutivo diario. */
    private String generarCodigo() {
        String prefijo = "CIT-" + ZonaClinica.hoy().format(FECHA_CODIGO) + "-";
        return prefijo + "%04d".formatted(citaRepository.countByCodigoStartingWith(prefijo) + 1);
    }
}
