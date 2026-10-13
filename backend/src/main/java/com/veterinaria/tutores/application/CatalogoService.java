package com.veterinaria.tutores.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.tutores.api.dto.EspecieResponse;
import com.veterinaria.tutores.api.dto.RazaResponse;
import com.veterinaria.tutores.domain.Raza;
import com.veterinaria.tutores.internal.EspecieRepository;
import com.veterinaria.tutores.internal.RazaRepository;

/** Consulta de los catalogos de especies y razas activas. */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final EspecieRepository especieRepository;
    private final RazaRepository razaRepository;

    public CatalogoService(EspecieRepository especieRepository, RazaRepository razaRepository) {
        this.especieRepository = especieRepository;
        this.razaRepository = razaRepository;
    }

    public List<EspecieResponse> especies() {
        return especieRepository.findByActivoTrueOrderByNombreAsc().stream().map(EspecieResponse::from).toList();
    }

    /** Razas activas de la especie indicada; sin especie, todas las razas activas. */
    public List<RazaResponse> razas(Long especieId) {
        List<Raza> razas = especieId == null
                ? razaRepository.findByActivoTrueOrderByNombreAsc()
                : razaRepository.findByEspecieIdAndActivoTrueOrderByNombreAsc(especieId);
        return razas.stream().map(RazaResponse::from).toList();
    }
}
