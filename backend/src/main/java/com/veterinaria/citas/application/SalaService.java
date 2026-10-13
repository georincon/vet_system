package com.veterinaria.citas.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.citas.api.dto.SalaRequest;
import com.veterinaria.citas.api.dto.SalaResponse;
import com.veterinaria.citas.domain.Sala;
import com.veterinaria.citas.internal.SalaRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.sede.SedePrincipal;
import com.veterinaria.shared.text.Textos;

/** CRUD de salas reservables. */
@Service
public class SalaService {

    private final SalaRepository salaRepository;
    private final SedePrincipal sedePrincipal;

    public SalaService(SalaRepository salaRepository, SedePrincipal sedePrincipal) {
        this.salaRepository = salaRepository;
        this.sedePrincipal = sedePrincipal;
    }

    @Transactional(readOnly = true)
    public List<SalaResponse> list(boolean incluirInactivas) {
        return (incluirInactivas ? salaRepository.findAllByOrderByNombreAsc()
                : salaRepository.findByActivoTrueOrderByNombreAsc()).stream().map(SalaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SalaResponse find(Long id) {
        return SalaResponse.from(obtener(id));
    }

    @Transactional
    public SalaResponse create(SalaRequest r) {
        String nombre = Textos.limpiar(r.nombre());
        if (nombre == null) {
            throw new BusinessException("El nombre es obligatorio");
        }
        if (salaRepository.existsBySedeIdAndNombreIgnoreCase(sedePrincipal.id(), nombre)) {
            throw new ConflictException("Ya existe una sala con ese nombre");
        }
        Sala sala = new Sala();
        sala.setSedeId(sedePrincipal.id());
        sala.setNombre(nombre);
        aplicar(sala, r);
        return SalaResponse.from(salaRepository.saveAndFlush(sala));
    }

    @Transactional
    public SalaResponse update(Long id, SalaRequest r) {
        Sala sala = obtener(id);
        if (r.nombre() != null) {
            String nombre = Textos.limpiar(r.nombre());
            if (nombre == null) {
                throw new BusinessException("El nombre no puede quedar vacio");
            }
            if (salaRepository.existsBySedeIdAndNombreIgnoreCaseAndIdNot(sala.getSedeId(), nombre, id)) {
                throw new ConflictException("Ya existe una sala con ese nombre");
            }
            sala.setNombre(nombre);
        }
        aplicar(sala, r);
        return SalaResponse.from(salaRepository.saveAndFlush(sala));
    }

    /** Baja logica: la sala deja de ofrecerse pero las citas historicas la conservan. */
    @Transactional
    public void desactivar(Long id) {
        Sala sala = obtener(id);
        sala.setActivo(false);
        salaRepository.save(sala);
    }

    private static void aplicar(Sala sala, SalaRequest r) {
        if (r.tipo() != null) {
            sala.setTipo(r.tipo());
        }
        if (r.capacidad() != null) {
            sala.setCapacidad(r.capacidad());
        }
        if (r.activo() != null) {
            sala.setActivo(r.activo());
        }
    }

    private Sala obtener(Long id) {
        return salaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sala", id));
    }
}
