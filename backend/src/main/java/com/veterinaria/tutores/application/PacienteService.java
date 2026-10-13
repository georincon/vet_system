package com.veterinaria.tutores.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.web.PageResponse;
import com.veterinaria.tutores.api.dto.PacienteRequest;
import com.veterinaria.tutores.api.dto.PacienteResponse;
import com.veterinaria.tutores.domain.Especie;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.PacienteTutor;
import com.veterinaria.tutores.domain.Raza;
import com.veterinaria.tutores.domain.Tutor;
import com.veterinaria.tutores.internal.EspecieRepository;
import com.veterinaria.tutores.internal.PacienteRepository;
import com.veterinaria.tutores.internal.RazaRepository;
import com.veterinaria.tutores.internal.TutorRepository;

/** Casos de uso de pacientes y de sus vinculos con tutores. */
@Service
public class PacienteService {

    private static final DateTimeFormatter FECHA_CODIGO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PacienteRepository pacienteRepository;
    private final TutorRepository tutorRepository;
    private final EspecieRepository especieRepository;
    private final RazaRepository razaRepository;

    public PacienteService(PacienteRepository pacienteRepository, TutorRepository tutorRepository,
            EspecieRepository especieRepository, RazaRepository razaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.tutorRepository = tutorRepository;
        this.especieRepository = especieRepository;
        this.razaRepository = razaRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PacienteResponse> list(String q, Pageable pageable) {
        String termino = q == null ? "" : q.trim();
        return PageResponse.from(
                pacienteRepository.findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCase(
                        termino, termino, pageable),
                PacienteResponse::from);
    }

    @Transactional(readOnly = true)
    public PacienteResponse find(Long id) {
        return PacienteResponse.from(obtener(id));
    }

    @Transactional
    public PacienteResponse create(PacienteRequest request) {
        Paciente paciente = new Paciente();
        paciente.setCodigo(generarCodigo());
        aplicar(paciente, request);
        aplicarTutores(paciente, request);
        return PacienteResponse.from(pacienteRepository.saveAndFlush(paciente));
    }

    @Transactional
    public PacienteResponse update(Long id, PacienteRequest request) {
        Paciente paciente = obtener(id);
        aplicar(paciente, request);
        aplicarTutores(paciente, request);
        return PacienteResponse.from(pacienteRepository.saveAndFlush(paciente));
    }

    private Paciente obtener(Long id) {
        return pacienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }

    /** Codigo PAC-YYYYMMDD-NNNN con consecutivo diario. */
    private String generarCodigo() {
        String prefijo = "PAC-" + LocalDate.now().format(FECHA_CODIGO) + "-";
        return prefijo + "%04d".formatted(pacienteRepository.countByCodigoStartingWith(prefijo) + 1);
    }

    private void aplicar(Paciente paciente, PacienteRequest r) {
        Especie especie = especieRepository.findById(r.especieId())
                .orElseThrow(() -> new ResourceNotFoundException("Especie", r.especieId()));
        paciente.setEspecie(especie);
        paciente.setRaza(r.razaId() == null ? null : resolverRaza(r.razaId(), especie));

        String microchip = Textos.limpiar(r.microchip());
        if (microchip != null && microchipEnUso(microchip, paciente.getId())) {
            throw new ConflictException("Ya existe un paciente con el microchip '%s'.".formatted(microchip));
        }
        paciente.setMicrochip(microchip);

        paciente.setNombre(r.nombre().trim());
        paciente.setSexo(r.sexo());
        paciente.setEstadoReproductivo(Textos.limpiar(r.estadoReproductivo()));
        paciente.setColor(Textos.limpiar(r.color()));
        paciente.setFechaNacimiento(r.fechaNacimiento());
        paciente.setPesoActual(r.pesoActual());
        if (r.esterilizado() != null) {
            paciente.setEsterilizado(r.esterilizado());
        }
        if (r.fallecido() != null) {
            paciente.setFallecido(r.fallecido());
        }
        paciente.setAlertas(Textos.limpiar(r.alertas()));
        paciente.setNotas(Textos.limpiar(r.notas()));
    }

    private Raza resolverRaza(Long razaId, Especie especie) {
        Raza raza = razaRepository.findById(razaId).orElseThrow(() -> new ResourceNotFoundException("Raza", razaId));
        if (!raza.getEspecie().getId().equals(especie.getId())) {
            throw new BusinessException("La raza seleccionada no pertenece a la especie indicada.");
        }
        return raza;
    }

    private boolean microchipEnUso(String microchip, Long pacienteId) {
        return pacienteId == null
                ? pacienteRepository.existsByMicrochip(microchip)
                : pacienteRepository.existsByMicrochipAndIdNot(microchip, pacienteId);
    }

    /**
     * Sincroniza los vinculos con tutores. Sin {@code tutorIds} ni principal no se toca nada;
     * sin {@code tutorIds} pero con principal se reasigna entre los vinculos actuales.
     */
    private void aplicarTutores(Paciente paciente, PacienteRequest r) {
        if (r.tutorIds() == null && r.tutorPrincipalId() == null) {
            return;
        }
        List<Long> ids = r.tutorIds() != null
                ? r.tutorIds().stream().distinct().toList()
                : paciente.getTutores().stream().map(v -> v.getTutor().getId()).toList();
        Long principalId = r.tutorPrincipalId() != null ? r.tutorPrincipalId() : principalActual(paciente, ids);
        boolean principalInvalido = ids.isEmpty() ? r.tutorPrincipalId() != null : !ids.contains(principalId);
        if (principalInvalido) {
            throw new BusinessException("El tutor principal debe estar dentro de los tutores del paciente.");
        }

        Map<Long, Tutor> tutores = tutorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tutor::getId, Function.identity()));
        ids.stream().filter(id -> !tutores.containsKey(id)).findFirst().ifPresent(id -> {
            throw new ResourceNotFoundException("Tutor", id);
        });

        paciente.getTutores().removeIf(v -> !ids.contains(v.getTutor().getId()));
        Map<Long, PacienteTutor> vigentes = paciente.getTutores().stream()
                .collect(Collectors.toMap(v -> v.getTutor().getId(), Function.identity()));
        for (Long id : ids) {
            PacienteTutor vinculo = vigentes.get(id);
            if (vinculo == null) {
                vinculo = new PacienteTutor(paciente, tutores.get(id), false);
                paciente.getTutores().add(vinculo);
            }
            vinculo.setEsPrincipal(id.equals(principalId));
        }
    }

    /** Principal vigente si sigue en la lista; si no, el primer tutor. */
    private static Long principalActual(Paciente paciente, List<Long> ids) {
        return paciente.getTutores().stream()
                .filter(PacienteTutor::isEsPrincipal)
                .map(v -> v.getTutor().getId())
                .filter(ids::contains)
                .findFirst()
                .orElse(ids.isEmpty() ? null : ids.get(0));
    }
}
