package com.veterinaria.clinica.application;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.api.dto.AlergiaRequest;
import com.veterinaria.clinica.api.dto.AlergiaResponse;
import com.veterinaria.clinica.api.dto.HistoriaItem;
import com.veterinaria.clinica.api.dto.PesoRequest;
import com.veterinaria.clinica.api.dto.PesoResponse;
import com.veterinaria.clinica.domain.Alergia;
import com.veterinaria.clinica.internal.AlergiaRepository;
import com.veterinaria.clinica.internal.HistoriaRepository;
import com.veterinaria.clinica.internal.PesoRegistroRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.internal.PacienteRepository;

/** Datos clinicos transversales del paciente: alergias, pesos y linea de tiempo. */
@Service
public class PacienteClinicoService {

    private final PacienteRepository pacienteRepository;
    private final AlergiaRepository alergiaRepository;
    private final PesoRegistroRepository pesoRepository;
    private final PesoService pesoService;
    private final HistoriaRepository historiaRepository;

    public PacienteClinicoService(PacienteRepository pacienteRepository, AlergiaRepository alergiaRepository,
            PesoRegistroRepository pesoRepository, PesoService pesoService, HistoriaRepository historiaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.alergiaRepository = alergiaRepository;
        this.pesoRepository = pesoRepository;
        this.pesoService = pesoService;
        this.historiaRepository = historiaRepository;
    }

    // ------------------------------------------------------------------ alergias

    @Transactional(readOnly = true)
    public List<AlergiaResponse> alergias(Long pacienteId, boolean incluirInactivas) {
        paciente(pacienteId);
        return (incluirInactivas ? alergiaRepository.findByPacienteIdOrderByIdAsc(pacienteId)
                : alergiaRepository.findByPacienteIdAndActivoTrueOrderByIdAsc(pacienteId))
                .stream().map(AlergiaResponse::from).toList();
    }

    @Transactional
    public AlergiaResponse agregarAlergia(Long pacienteId, AlergiaRequest r) {
        Alergia a = new Alergia();
        a.setPaciente(paciente(pacienteId));
        a.setRegistradaEn(ZonaClinica.hoy());
        String descripcion = Textos.limpiar(r.descripcion());
        if (descripcion == null) {
            throw new BusinessException("La descripcion es obligatoria");
        }
        a.setDescripcion(descripcion);
        aplicar(a, r);
        return AlergiaResponse.from(alergiaRepository.saveAndFlush(a));
    }

    @Transactional
    public AlergiaResponse actualizarAlergia(Long pacienteId, Long alergiaId, AlergiaRequest r) {
        Alergia a = alergia(pacienteId, alergiaId);
        if (r.descripcion() != null) {
            String descripcion = Textos.limpiar(r.descripcion());
            if (descripcion == null) {
                throw new BusinessException("La descripcion no puede quedar vacia");
            }
            a.setDescripcion(descripcion);
        }
        aplicar(a, r);
        return AlergiaResponse.from(alergiaRepository.saveAndFlush(a));
    }

    /** Baja logica (activo=false); el registro se conserva en el historial. */
    @Transactional
    public void desactivarAlergia(Long pacienteId, Long alergiaId) {
        alergia(pacienteId, alergiaId).setActivo(false);
    }

    private static void aplicar(Alergia a, AlergiaRequest r) {
        if (r.severidad() != null) {
            a.setSeveridad(r.severidad());
        }
        if (r.tipo() != null) {
            a.setTipo(r.tipo());
        }
        a.setReaccion(Textos.actualizar(a.getReaccion(), r.reaccion()));
        if (r.activo() != null) {
            a.setActivo(r.activo());
        }
    }

    private Alergia alergia(Long pacienteId, Long alergiaId) {
        return alergiaRepository.findById(alergiaId)
                .filter(a -> a.getPaciente().getId().equals(pacienteId))
                .orElseThrow(() -> new ResourceNotFoundException("Alergia", alergiaId));
    }

    // ------------------------------------------------------------------ pesos

    @Transactional(readOnly = true)
    public List<PesoResponse> pesos(Long pacienteId) {
        paciente(pacienteId);
        return pesoRepository.findByPacienteIdOrderByFechaDescIdDesc(pacienteId).stream()
                .map(PesoResponse::from).toList();
    }

    @Transactional
    public PesoResponse registrarPeso(Long pacienteId, PesoRequest r) {
        Paciente paciente = paciente(pacienteId);
        var fecha = r.fecha() != null ? r.fecha() : ZonaClinica.hoy();
        if (fecha.isAfter(ZonaClinica.hoy())) {
            throw new BusinessException("La fecha del peso no puede ser futura");
        }
        return PesoResponse.from(pesoService.registrar(paciente, r.pesoKg(), fecha));
    }

    // ------------------------------------------------------------------ historia

    /** Linea de tiempo paginada (mas reciente primero); {@code tipo} filtra por CONSULTA, VACUNA, PESO o ALERGIA. */
    @Transactional(readOnly = true)
    public PageResponse<HistoriaItem> historia(Long pacienteId, String tipo, Pageable pageable) {
        paciente(pacienteId);
        long total = historiaRepository.total(pacienteId, tipo);
        List<HistoriaItem> items = historiaRepository.pagina(pacienteId, tipo, (int) pageable.getOffset(),
                pageable.getPageSize());
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    private Paciente paciente(Long id) {
        return pacienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
    }
}
