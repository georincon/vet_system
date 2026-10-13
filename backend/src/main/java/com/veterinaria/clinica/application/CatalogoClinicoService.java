package com.veterinaria.clinica.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.api.dto.CatalogoDiagnosticoResponse;
import com.veterinaria.clinica.api.dto.PlantillaResponse;
import com.veterinaria.clinica.internal.CatalogoDiagnosticoRepository;
import com.veterinaria.clinica.internal.PlantillaSoapRepository;
import com.veterinaria.shared.text.Textos;

/** Lectura de catalogos clinicos: plantillas SOAP y diagnosticos frecuentes. */
@Service
public class CatalogoClinicoService {

    private final PlantillaSoapRepository plantillaRepository;
    private final CatalogoDiagnosticoRepository diagnosticoRepository;

    public CatalogoClinicoService(PlantillaSoapRepository plantillaRepository,
            CatalogoDiagnosticoRepository diagnosticoRepository) {
        this.plantillaRepository = plantillaRepository;
        this.diagnosticoRepository = diagnosticoRepository;
    }

    @Transactional(readOnly = true)
    public List<PlantillaResponse> plantillas(Long especieId) {
        return plantillaRepository.buscar(especieId).stream().map(PlantillaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDiagnosticoResponse> diagnosticos(String q) {
        return diagnosticoRepository.buscar(Textos.limpiar(q)).stream().map(CatalogoDiagnosticoResponse::from).toList();
    }
}
