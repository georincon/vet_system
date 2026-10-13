package com.veterinaria.citas.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.api.dto.BloqueoRequest;
import com.veterinaria.citas.api.dto.BloqueoResponse;
import com.veterinaria.citas.domain.BloqueoAgenda;
import com.veterinaria.citas.domain.EstadoCitaCodigo;
import com.veterinaria.citas.internal.BloqueoAgendaRepository;
import com.veterinaria.citas.internal.CitaRepository;
import com.veterinaria.citas.internal.SalaRepository;
import com.veterinaria.seguridad.application.VeterinarioLookup;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.time.ZonaClinica;

/** CRUD de bloqueos de agenda (veterinario y/o sala). */
@Service
public class BloqueoService {

    private final BloqueoAgendaRepository bloqueoRepository;
    private final CitaRepository citaRepository;
    private final SalaRepository salaRepository;
    private final VeterinarioLookup veterinarioLookup;
    private final SedePrincipal sedePrincipal;

    public BloqueoService(BloqueoAgendaRepository bloqueoRepository, CitaRepository citaRepository,
            SalaRepository salaRepository, VeterinarioLookup veterinarioLookup, SedePrincipal sedePrincipal) {
        this.bloqueoRepository = bloqueoRepository;
        this.citaRepository = citaRepository;
        this.salaRepository = salaRepository;
        this.veterinarioLookup = veterinarioLookup;
        this.sedePrincipal = sedePrincipal;
    }

    @Transactional(readOnly = true)
    public List<BloqueoResponse> list(LocalDate desde, LocalDate hasta, Long veterinarioId, Long salaId) {
        return bloqueoRepository.delRango(veterinarioId, salaId,
                ZonaClinica.instante(desde, java.time.LocalTime.MIN),
                ZonaClinica.instante(hasta.plusDays(1), java.time.LocalTime.MIN))
                .stream().map(BloqueoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BloqueoResponse find(Long id) {
        return BloqueoResponse.from(obtener(id));
    }

    @Transactional
    public BloqueoResponse create(BloqueoRequest r) {
        BloqueoAgenda b = new BloqueoAgenda();
        b.setSedeId(sedePrincipal.id());
        aplicar(b, r);
        verificarSinCitas(b);
        return BloqueoResponse.from(bloqueoRepository.saveAndFlush(b));
    }

    @Transactional
    public BloqueoResponse update(Long id, BloqueoRequest r) {
        BloqueoAgenda b = obtener(id);
        aplicar(b, r);
        verificarSinCitas(b);
        return BloqueoResponse.from(bloqueoRepository.saveAndFlush(b));
    }

    @Transactional
    public void delete(Long id) {
        bloqueoRepository.delete(obtener(id));
    }

    private void aplicar(BloqueoAgenda b, BloqueoRequest r) {
        if (r.veterinarioId() != null) {
            b.setVeterinario(veterinarioLookup.obtener(r.veterinarioId()));
        }
        if (r.salaId() != null) {
            b.setSala(salaRepository.findById(r.salaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sala", r.salaId())));
        }
        if (r.inicio() != null) {
            b.setInicio(ZonaClinica.instante(r.inicio()));
        }
        if (r.fin() != null) {
            b.setFin(ZonaClinica.instante(r.fin()));
        }
        if (r.tipo() != null) {
            b.setTipo(r.tipo());
        }
        b.setMotivo(Textos.actualizar(b.getMotivo(), r.motivo()));
        if (b.getVeterinario() == null && b.getSala() == null) {
            throw new BusinessException("El bloqueo debe indicar un veterinario y/o una sala");
        }
        if (!b.getFin().isAfter(b.getInicio())) {
            throw new BusinessException("El fin del bloqueo debe ser posterior al inicio");
        }
    }

    /** No se bloquea una franja que ya tiene citas vigentes del veterinario o de la sala. */
    private void verificarSinCitas(BloqueoAgenda b) {
        long citas = citaRepository.contarVigentesEnRango(
                b.getVeterinario() == null ? null : b.getVeterinario().getId(),
                b.getSala() == null ? null : b.getSala().getId(),
                b.getInicio(), b.getFin(), EstadoCitaCodigo.LIBERAN_AGENDA);
        if (citas > 0) {
            throw new ConflictException("Hay " + citas + " cita(s) vigente(s) en ese rango; reprogrameelas o cancelelas antes de bloquear");
        }
    }

    private BloqueoAgenda obtener(Long id) {
        return bloqueoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bloqueo", id));
    }
}
