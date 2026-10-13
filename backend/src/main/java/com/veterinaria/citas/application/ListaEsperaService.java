package com.veterinaria.citas.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.api.dto.ListaEsperaRequest;
import com.veterinaria.citas.api.dto.ListaEsperaResponse;
import com.veterinaria.citas.domain.ListaEspera;
import com.veterinaria.citas.internal.CitaRepository;
import com.veterinaria.citas.internal.ListaEsperaRepository;
import com.veterinaria.citas.internal.TipoCitaRepository;
import com.veterinaria.seguridad.application.VeterinarioLookup;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.PacienteTutor;
import com.veterinaria.tutores.domain.Tutor;
import com.veterinaria.tutores.internal.PacienteRepository;
import com.veterinaria.tutores.internal.TutorRepository;

/** Lista de espera de la agenda. */
@Service
public class ListaEsperaService {

    private final ListaEsperaRepository repository;
    private final PacienteRepository pacienteRepository;
    private final TutorRepository tutorRepository;
    private final TipoCitaRepository tipoCitaRepository;
    private final CitaRepository citaRepository;
    private final VeterinarioLookup veterinarioLookup;
    private final SedePrincipal sedePrincipal;

    public ListaEsperaService(ListaEsperaRepository repository, PacienteRepository pacienteRepository,
            TutorRepository tutorRepository, TipoCitaRepository tipoCitaRepository, CitaRepository citaRepository,
            VeterinarioLookup veterinarioLookup, SedePrincipal sedePrincipal) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.tutorRepository = tutorRepository;
        this.tipoCitaRepository = tipoCitaRepository;
        this.citaRepository = citaRepository;
        this.veterinarioLookup = veterinarioLookup;
        this.sedePrincipal = sedePrincipal;
    }

    @Transactional(readOnly = true)
    public List<ListaEsperaResponse> list(String estado) {
        return repository.buscar(estado).stream().map(ListaEsperaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ListaEsperaResponse find(Long id) {
        return ListaEsperaResponse.from(obtener(id));
    }

    @Transactional
    public ListaEsperaResponse create(ListaEsperaRequest r) {
        ListaEspera l = new ListaEspera();
        l.setSedeId(sedePrincipal.id());
        Paciente paciente = pacienteRepository.findById(r.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", r.pacienteId()));
        l.setPaciente(paciente);
        l.setTutor(r.tutorId() != null ? obtenerTutor(r.tutorId()) : tutorPrincipal(paciente));
        aplicar(l, r);
        return ListaEsperaResponse.from(repository.saveAndFlush(l));
    }

    @Transactional
    public ListaEsperaResponse update(Long id, ListaEsperaRequest r) {
        ListaEspera l = obtener(id);
        if (r.pacienteId() != null) {
            l.setPaciente(pacienteRepository.findById(r.pacienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paciente", r.pacienteId())));
        }
        if (r.tutorId() != null) {
            l.setTutor(obtenerTutor(r.tutorId()));
        }
        aplicar(l, r);
        return ListaEsperaResponse.from(repository.saveAndFlush(l));
    }

    /** Baja logica: pasa a CANCELADA. */
    @Transactional
    public void cancelar(Long id) {
        ListaEspera l = obtener(id);
        l.setEstado("CANCELADA");
        repository.save(l);
    }

    /** Marca la entrada como ASIGNADA a una cita ya creada. */
    @Transactional
    public ListaEsperaResponse asignar(Long id, Long citaId) {
        ListaEspera l = obtener(id);
        citaRepository.findById(citaId).orElseThrow(() -> new ResourceNotFoundException("Cita", citaId));
        l.setCitaAsignadaId(citaId);
        l.setEstado("ASIGNADA");
        return ListaEsperaResponse.from(repository.saveAndFlush(l));
    }

    private void aplicar(ListaEspera l, ListaEsperaRequest r) {
        if (r.tipoCitaId() != null) {
            l.setTipoCita(tipoCitaRepository.findById(r.tipoCitaId())
                    .orElseThrow(() -> new ResourceNotFoundException("TipoCita", r.tipoCitaId())));
        }
        if (r.veterinarioId() != null) {
            l.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        }
        if (r.fechaDeseadaDesde() != null) {
            l.setFechaDeseadaDesde(r.fechaDeseadaDesde());
        }
        if (r.fechaDeseadaHasta() != null) {
            l.setFechaDeseadaHasta(r.fechaDeseadaHasta());
        }
        if (l.getFechaDeseadaDesde() != null && l.getFechaDeseadaHasta() != null
                && l.getFechaDeseadaHasta().isBefore(l.getFechaDeseadaDesde())) {
            throw new BusinessException("La fecha deseada 'hasta' no puede ser anterior a 'desde'");
        }
        if (r.franjaPreferida() != null) {
            l.setFranjaPreferida(r.franjaPreferida());
        }
        if (r.prioridad() != null) {
            l.setPrioridad(r.prioridad().shortValue());
        }
        if (r.estado() != null) {
            l.setEstado(r.estado());
        }
        l.setNotas(Textos.actualizar(l.getNotas(), r.notas()));
    }

    private Tutor obtenerTutor(Long id) {
        return tutorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tutor", id));
    }

    private static Tutor tutorPrincipal(Paciente paciente) {
        return paciente.getTutores().stream().filter(PacienteTutor::isEsPrincipal).findFirst()
                .or(() -> paciente.getTutores().stream().findFirst())
                .map(PacienteTutor::getTutor)
                .orElseThrow(() -> new BusinessException("El paciente no tiene tutor; indique tutorId"));
    }

    private ListaEspera obtener(Long id) {
        return repository.findWithDetalleById(id).orElseThrow(() -> new ResourceNotFoundException("ListaEspera", id));
    }
}
