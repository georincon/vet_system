package com.veterinaria.laboratorio.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.ExamenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ParametroRequest;
import com.veterinaria.laboratorio.api.dto.LabDtos.RangoRequest;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;

/** Catalogo de examenes (paneles), parametros con LOINC/UCUM y rangos de referencia por especie/sexo/edad. */
@Service
public class ExamenCatalogoService {

    private final CatalogoRepository repository;

    public ExamenCatalogoService(CatalogoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ExamenResponse> listar(String tipo, String q, boolean soloActivos) {
        return repository.listar(tipo, q, soloActivos);
    }

    @Transactional(readOnly = true)
    public ExamenResponse obtener(Long id) {
        return repository.detalle(id).orElseThrow(() -> new ResourceNotFoundException("Examen", id));
    }

    @Transactional
    public ExamenResponse crear(ExamenRequest r) {
        validar(r, null);
        long id = repository.insertarExamen(r);
        sincronizarParametros(id, r.parametros());
        return obtener(id);
    }

    /** Actualiza el examen; si se envia {@code parametros}, los que no llegan se desactivan (los resultados los referencian). */
    @Transactional
    public ExamenResponse actualizar(Long id, ExamenRequest r) {
        obtener(id);
        validar(r, id);
        repository.actualizarExamen(id, r);
        if (r.parametros() != null) {
            sincronizarParametros(id, r.parametros());
        }
        return obtener(id);
    }

    private void validar(ExamenRequest r, Long id) {
        if (repository.existeCodigo(r.codigo().trim(), id)) {
            throw new ConflictException("Ya existe un examen con el codigo " + r.codigo().trim());
        }
        if (r.itemId() != null && !repository.existeItem(r.itemId())) {
            throw new BusinessException("El item de inventario/servicio " + r.itemId() + " no existe");
        }
        if (r.parametros() == null) {
            return;
        }
        Set<String> codigos = new HashSet<>();
        for (ParametroRequest p : r.parametros()) {
            if (!codigos.add(p.codigo().trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException("Codigo de parametro repetido: " + p.codigo().trim());
            }
            if (p.rangos() != null) {
                validarRangos(p);
            }
        }
    }

    private void validarRangos(ParametroRequest p) {
        Set<String> claves = new HashSet<>();
        for (RangoRequest g : p.rangos()) {
            if (!repository.existeEspecie(g.especieId())) {
                throw new BusinessException("La especie " + g.especieId() + " no existe (parametro " + p.codigo() + ")");
            }
            if (g.valorMin() != null && g.valorMax() != null && g.valorMax().compareTo(g.valorMin()) < 0) {
                throw new BusinessException("Rango invalido en " + p.codigo() + ": valorMax menor que valorMin");
            }
            if (g.edadMinMeses() != null && g.edadMaxMeses() != null && g.edadMaxMeses() < g.edadMinMeses()) {
                throw new BusinessException("Rango invalido en " + p.codigo() + ": edadMaxMeses menor que edadMinMeses");
            }
            if (g.criticoMin() != null && g.valorMin() != null && g.criticoMin().compareTo(g.valorMin()) > 0) {
                throw new BusinessException("Rango invalido en " + p.codigo() + ": criticoMin mayor que valorMin");
            }
            if (g.criticoMax() != null && g.valorMax() != null && g.criticoMax().compareTo(g.valorMax()) < 0) {
                throw new BusinessException("Rango invalido en " + p.codigo() + ": criticoMax menor que valorMax");
            }
            if (!claves.add(g.especieId() + "|" + g.sexo() + "|" + g.edadMinMeses())) {
                throw new BusinessException("Rango duplicado (especie, sexo, edad minima) en el parametro " + p.codigo());
            }
        }
    }

    private void sincronizarParametros(Long examenId, List<ParametroRequest> parametros) {
        if (parametros == null) {
            return;
        }
        List<Long> conservar = new ArrayList<>();
        int orden = 1;
        for (ParametroRequest p : parametros) {
            Long existente = p.id();
            if (existente != null) {
                var info = repository.parametroInfo(existente).filter(x -> x.examenId().equals(examenId))
                        .orElseThrow(() -> new BusinessException("El parametro " + p.id() + " no pertenece al examen"));
                existente = info.id();
            } else {
                existente = repository.parametroDeExamenPorCodigo(examenId, p.codigo().trim()).orElse(null);
            }
            long pid;
            if (existente == null) {
                pid = repository.insertarParametro(examenId, p, orden);
            } else {
                pid = existente;
                repository.actualizarParametro(pid, p, orden);
            }
            if (p.rangos() != null) {
                repository.reemplazarRangos(pid, p.rangos());
            }
            conservar.add(pid);
            orden++;
        }
        repository.desactivarParametrosExcepto(examenId, conservar);
    }
}
