package com.veterinaria.citas.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.api.dto.AgendaResponse;
import com.veterinaria.citas.api.dto.BloqueoResponse;
import com.veterinaria.citas.api.dto.CitaResponse;
import com.veterinaria.citas.api.dto.TipoCitaResponse;
import com.veterinaria.citas.domain.EstadoCitaCodigo;
import com.veterinaria.citas.internal.BloqueoAgendaRepository;
import com.veterinaria.citas.internal.CitaRepository;
import com.veterinaria.citas.internal.TipoCitaRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.time.ZonaClinica;

/** Vista de calendario: citas y bloqueos de un rango. */
@Service
public class AgendaService {

    private static final int MAX_DIAS = 62;

    private final CitaRepository citaRepository;
    private final BloqueoAgendaRepository bloqueoRepository;
    private final TipoCitaRepository tipoCitaRepository;

    public AgendaService(CitaRepository citaRepository, BloqueoAgendaRepository bloqueoRepository,
            TipoCitaRepository tipoCitaRepository) {
        this.citaRepository = citaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.tipoCitaRepository = tipoCitaRepository;
    }

    /** {@code hasta} es inclusivo. Sin filtros devuelve toda la clinica. */
    @Transactional(readOnly = true)
    public AgendaResponse agenda(LocalDate desde, LocalDate hasta, Long veterinarioId, Long salaId,
            boolean incluirCanceladas) {
        if (hasta.isBefore(desde)) {
            throw new BusinessException("'hasta' no puede ser anterior a 'desde'");
        }
        if (desde.plusDays(MAX_DIAS).isBefore(hasta)) {
            throw new BusinessException("El rango maximo de la agenda es de " + MAX_DIAS + " dias");
        }
        var inicio = ZonaClinica.instante(desde, LocalTime.MIN);
        var fin = ZonaClinica.instante(hasta.plusDays(1), LocalTime.MIN);
        List<CitaResponse> citas = citaRepository
                .agenda(inicio, fin, veterinarioId, salaId, incluirCanceladas, EstadoCitaCodigo.LIBERAN_AGENDA)
                .stream().map(CitaResponse::from).toList();
        List<BloqueoResponse> bloqueos = bloqueoRepository.delRango(veterinarioId, salaId, inicio, fin)
                .stream().map(BloqueoResponse::from).toList();
        return new AgendaResponse(desde, hasta, citas, bloqueos);
    }

    @Transactional(readOnly = true)
    public List<TipoCitaResponse> tiposCita() {
        return tipoCitaRepository.findByActivoTrueOrderByNombreAsc().stream().map(TipoCitaResponse::from).toList();
    }
}
