package com.veterinaria.inmunizacion.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inmunizacion.TipoAplicacion;
import com.veterinaria.inmunizacion.api.dto.PendienteResponse;
import com.veterinaria.inmunizacion.internal.PendientesRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.shared.web.PageResponse;

/** Proximas dosis pendientes (vencidas y por vencer) de vacunas y antiparasitarios. */
@Service
public class PendientesService {

    public static final int DIAS_POR_DEFECTO = 30;
    private static final int LOTE = 500;

    private final PendientesRepository repository;

    public PendientesService(PendientesRepository repository) {
        this.repository = repository;
    }

    /**
     * Pendientes paginados. Sin {@code hasta} se usa hoy + {@code dias} (30 por defecto); sin {@code desde} se
     * incluyen todas las vencidas.
     */
    @Transactional(readOnly = true)
    public PageResponse<PendienteResponse> pendientes(LocalDate desde, LocalDate hasta, Integer dias, Long especieId,
            TipoAplicacion tipo, String estado, Pageable pageable) {
        LocalDate hoy = ZonaClinica.hoy();
        if (estado != null && !estado.equals("VENCIDA") && !estado.equals("POR_VENCER")) {
            throw new BusinessException("estado debe ser VENCIDA o POR_VENCER");
        }
        if (dias != null && dias < 0) {
            throw new BusinessException("dias no puede ser negativo");
        }
        LocalDate limite = hasta != null ? hasta : hoy.plusDays(dias != null ? dias : DIAS_POR_DEFECTO);
        if (desde != null && desde.isAfter(limite)) {
            throw new BusinessException("desde no puede ser posterior a hasta");
        }
        String t = tipo == null ? null : tipo.name();
        long total = repository.total(hoy, desde, limite, especieId, t, estado);
        List<PendienteResponse> items = repository.pagina(hoy, desde, limite, especieId, t, estado,
                (int) pageable.getOffset(), pageable.getPageSize());
        int paginas = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), total, paginas,
                pageable.getPageNumber() == 0, pageable.getPageNumber() + 1 >= paginas);
    }

    /** Cantidad de proximas dosis que vencen entre hoy y hoy + {@code dias} (no incluye las ya vencidas). */
    @Transactional(readOnly = true)
    public long contarPorVencer(int dias) {
        LocalDate hoy = ZonaClinica.hoy();
        return repository.total(hoy, hoy, hoy.plusDays(dias), null, null, null);
    }

    /** Todas las pendientes con proxima fecha en [desde, hasta] (uso interno de recordatorios). */
    @Transactional(readOnly = true)
    public List<PendienteResponse> entre(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = ZonaClinica.hoy();
        List<PendienteResponse> todas = new ArrayList<>();
        int offset = 0;
        List<PendienteResponse> pagina;
        do {
            pagina = repository.pagina(hoy, desde, hasta, null, null, null, offset, LOTE);
            todas.addAll(pagina);
            offset += LOTE;
        } while (pagina.size() == LOTE);
        return todas;
    }
}
