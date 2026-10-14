package com.veterinaria.inmunizacion.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.inmunizacion.api.dto.AntiparasitarioRequest;
import com.veterinaria.inmunizacion.api.dto.AntiparasitarioResponse;
import com.veterinaria.inmunizacion.api.dto.EsquemaRequest;
import com.veterinaria.inmunizacion.api.dto.EsquemaResponse;
import com.veterinaria.inmunizacion.api.dto.VacunaRequest;
import com.veterinaria.inmunizacion.api.dto.VacunaResponse;
import com.veterinaria.inmunizacion.domain.Antiparasitario;
import com.veterinaria.inmunizacion.domain.EsquemaVacunal;
import com.veterinaria.inmunizacion.domain.EsquemaVacunalDosis;
import com.veterinaria.inmunizacion.domain.Vacuna;
import com.veterinaria.inmunizacion.internal.AntiparasitarioRepository;
import com.veterinaria.inmunizacion.internal.EsquemaVacunalRepository;
import com.veterinaria.inmunizacion.internal.VacunaRepository;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.tutores.domain.Especie;
import com.veterinaria.tutores.internal.EspecieRepository;

/** Catalogos de vacunas, antiparasitarios y esquemas vacunales (lectura para todos; escritura ADMIN). */
@Service
public class CatalogoInmunizacionService {

    private final VacunaRepository vacunaRepository;
    private final AntiparasitarioRepository antiparasitarioRepository;
    private final EsquemaVacunalRepository esquemaRepository;
    private final EspecieRepository especieRepository;

    public CatalogoInmunizacionService(VacunaRepository vacunaRepository,
            AntiparasitarioRepository antiparasitarioRepository, EsquemaVacunalRepository esquemaRepository,
            EspecieRepository especieRepository) {
        this.vacunaRepository = vacunaRepository;
        this.antiparasitarioRepository = antiparasitarioRepository;
        this.esquemaRepository = esquemaRepository;
        this.especieRepository = especieRepository;
    }

    // ------------------------------------------------------------------ vacunas

    @Transactional(readOnly = true)
    public List<VacunaResponse> vacunas(Long especieId, Boolean activo, String q) {
        String termino = q == null ? null : q.trim().toLowerCase();
        return vacunaRepository.findAllByOrderByNombreAsc().stream()
                .filter(v -> especieId == null || v.getEspecie() == null || v.getEspecie().getId().equals(especieId))
                .filter(v -> activo == null || v.isActivo() == activo)
                .filter(v -> termino == null || termino.isEmpty() || v.getNombre().toLowerCase().contains(termino)
                        || v.getCodigo().toLowerCase().contains(termino))
                .map(VacunaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VacunaResponse vacuna(Long id) {
        return VacunaResponse.from(obtenerVacuna(id));
    }

    @Transactional
    public VacunaResponse crearVacuna(VacunaRequest r) {
        String codigo = r.codigo().trim();
        if (vacunaRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Ya existe una vacuna con el codigo '%s'.".formatted(codigo));
        }
        Vacuna v = new Vacuna();
        v.setCodigo(codigo);
        aplicar(v, r);
        return VacunaResponse.from(vacunaRepository.saveAndFlush(v));
    }

    @Transactional
    public VacunaResponse actualizarVacuna(Long id, VacunaRequest r) {
        Vacuna v = obtenerVacuna(id);
        if (r.codigo() != null) {
            String codigo = Textos.limpiar(r.codigo());
            if (codigo == null) {
                throw new BusinessException("El codigo no puede quedar vacio");
            }
            if (vacunaRepository.existsByCodigoAndIdNot(codigo, id)) {
                throw new ConflictException("Ya existe una vacuna con el codigo '%s'.".formatted(codigo));
            }
            v.setCodigo(codigo);
        }
        aplicar(v, r);
        return VacunaResponse.from(vacunaRepository.saveAndFlush(v));
    }

    private void aplicar(Vacuna v, VacunaRequest r) {
        if (r.nombre() != null) {
            String nombre = Textos.limpiar(r.nombre());
            if (nombre == null) {
                throw new BusinessException("El nombre no puede quedar vacio");
            }
            v.setNombre(nombre);
        }
        if (r.especieId() != null) {
            v.setEspecie(especie(r.especieId()));
        }
        if (r.periodoEficaciaDias() != null) {
            v.setPeriodoEficaciaDias(r.periodoEficaciaDias());
        }
        if (r.clasificacion() != null) {
            v.setClasificacion(r.clasificacion());
        }
        v.setEnfermedadesObjetivo(Textos.actualizar(v.getEnfermedadesObjetivo(), r.enfermedadesObjetivo()));
        v.setFabricante(Textos.actualizar(v.getFabricante(), r.fabricante()));
        v.setViaAdministracion(Textos.actualizar(v.getViaAdministracion(), r.viaAdministracion()));
        if (r.dosisMl() != null) {
            v.setDosisMl(r.dosisMl());
        }
        if (r.itemId() != null) {
            v.setItemId(r.itemId());
        }
        if (r.obligatoriaLey() != null) {
            v.setObligatoriaLey(r.obligatoriaLey());
        }
        if (r.activo() != null) {
            v.setActivo(r.activo());
        }
    }

    // ------------------------------------------------------------------ antiparasitarios

    @Transactional(readOnly = true)
    public List<AntiparasitarioResponse> antiparasitarios(Long especieId, Boolean activo) {
        return antiparasitarioRepository.findAllByOrderByNombreAsc().stream()
                .filter(a -> especieId == null || a.getEspecie() == null || a.getEspecie().getId().equals(especieId))
                .filter(a -> activo == null || a.isActivo() == activo)
                .map(AntiparasitarioResponse::from).toList();
    }

    @Transactional
    public AntiparasitarioResponse crearAntiparasitario(AntiparasitarioRequest r) {
        String codigo = r.codigo().trim();
        if (antiparasitarioRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Ya existe un antiparasitario con el codigo '%s'.".formatted(codigo));
        }
        Antiparasitario a = new Antiparasitario();
        a.setCodigo(codigo);
        aplicar(a, r);
        return AntiparasitarioResponse.from(antiparasitarioRepository.saveAndFlush(a));
    }

    @Transactional
    public AntiparasitarioResponse actualizarAntiparasitario(Long id, AntiparasitarioRequest r) {
        Antiparasitario a = antiparasitarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Antiparasitario", id));
        if (r.codigo() != null) {
            String codigo = Textos.limpiar(r.codigo());
            if (codigo == null) {
                throw new BusinessException("El codigo no puede quedar vacio");
            }
            if (antiparasitarioRepository.existsByCodigoAndIdNot(codigo, id)) {
                throw new ConflictException("Ya existe un antiparasitario con el codigo '%s'.".formatted(codigo));
            }
            a.setCodigo(codigo);
        }
        aplicar(a, r);
        return AntiparasitarioResponse.from(antiparasitarioRepository.saveAndFlush(a));
    }

    private void aplicar(Antiparasitario a, AntiparasitarioRequest r) {
        if (r.nombre() != null) {
            String nombre = Textos.limpiar(r.nombre());
            if (nombre == null) {
                throw new BusinessException("El nombre no puede quedar vacio");
            }
            a.setNombre(nombre);
        }
        if (r.tipo() != null) {
            a.setTipo(r.tipo());
        }
        a.setPrincipioActivo(Textos.actualizar(a.getPrincipioActivo(), r.principioActivo()));
        if (r.especieId() != null) {
            a.setEspecie(especie(r.especieId()));
        }
        if (r.intervaloDias() != null) {
            a.setIntervaloDias(r.intervaloDias());
        }
        if (r.itemId() != null) {
            a.setItemId(r.itemId());
        }
        if (r.activo() != null) {
            a.setActivo(r.activo());
        }
    }

    // ------------------------------------------------------------------ esquemas

    @Transactional(readOnly = true)
    public List<EsquemaResponse> esquemas(Long especieId, Long vacunaId, Boolean activo) {
        return esquemaRepository.findAllByOrderByNombreAsc().stream()
                .filter(e -> especieId == null || e.getEspecie() == null || e.getEspecie().getId().equals(especieId))
                .filter(e -> vacunaId == null || e.getVacuna().getId().equals(vacunaId))
                .filter(e -> activo == null || e.isActivo() == activo)
                .map(EsquemaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EsquemaResponse esquema(Long id) {
        return EsquemaResponse.from(obtenerEsquema(id));
    }

    @Transactional
    public EsquemaResponse crearEsquema(EsquemaRequest r) {
        String codigo = r.codigo().trim();
        if (esquemaRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Ya existe un esquema con el codigo '%s'.".formatted(codigo));
        }
        EsquemaVacunal e = new EsquemaVacunal();
        e.setCodigo(codigo);
        e.setNombre(r.nombre().trim());
        e.setVacuna(obtenerVacuna(r.vacunaId()));
        aplicar(e, r);
        return EsquemaResponse.from(esquemaRepository.saveAndFlush(e));
    }

    @Transactional
    public EsquemaResponse actualizarEsquema(Long id, EsquemaRequest r) {
        EsquemaVacunal e = obtenerEsquema(id);
        if (r.codigo() != null) {
            String codigo = Textos.limpiar(r.codigo());
            if (codigo == null || esquemaRepository.existsByCodigoAndIdNot(codigo, id)) {
                throw codigo == null ? new BusinessException("El codigo no puede quedar vacio")
                        : new ConflictException("Ya existe un esquema con el codigo '%s'.".formatted(codigo));
            }
            e.setCodigo(codigo);
        }
        if (r.nombre() != null) {
            String nombre = Textos.limpiar(r.nombre());
            if (nombre == null) {
                throw new BusinessException("El nombre no puede quedar vacio");
            }
            e.setNombre(nombre);
        }
        if (r.vacunaId() != null) {
            e.setVacuna(obtenerVacuna(r.vacunaId()));
        }
        aplicar(e, r);
        return EsquemaResponse.from(esquemaRepository.saveAndFlush(e));
    }

    private void aplicar(EsquemaVacunal e, EsquemaRequest r) {
        if (r.especieId() != null) {
            e.setEspecie(especie(r.especieId()));
        }
        e.setFuente(Textos.actualizar(e.getFuente(), r.fuente()));
        e.setNotas(Textos.actualizar(e.getNotas(), r.notas()));
        if (r.activo() != null) {
            e.setActivo(r.activo());
        }
        if (r.dosis() != null) {
            reemplazarDosis(e, r.dosis());
        }
    }

    /** Actualiza por {@code orden}: modifica las existentes, agrega las nuevas y elimina las que no vienen. */
    private static void reemplazarDosis(EsquemaVacunal e, List<EsquemaRequest.Dosis> nuevas) {
        Map<Integer, EsquemaRequest.Dosis> porOrden = new HashMap<>();
        for (EsquemaRequest.Dosis d : nuevas) {
            if (porOrden.put(d.orden(), d) != null) {
                throw new BusinessException("El orden de dosis " + d.orden() + " esta repetido");
            }
        }
        e.getDosis().removeIf(actual -> !porOrden.containsKey(actual.getOrden()));
        for (EsquemaRequest.Dosis d : porOrden.values()) {
            EsquemaVacunalDosis dosis = e.getDosis().stream().filter(x -> x.getOrden() == d.orden()).findFirst()
                    .orElseGet(() -> {
                        EsquemaVacunalDosis nueva = new EsquemaVacunalDosis();
                        nueva.setEsquema(e);
                        nueva.setOrden(d.orden());
                        e.getDosis().add(nueva);
                        return nueva;
                    });
            dosis.setNombreDosis(d.nombreDosis().trim());
            dosis.setEdadMinimaSemanas(d.edadMinimaSemanas());
            dosis.setIntervaloDiasPrevia(d.intervaloDiasPrevia());
            dosis.setEsRefuerzo(d.esRefuerzo() != null && d.esRefuerzo());
            dosis.setRepetirCadaDias(d.repetirCadaDias());
        }
        e.getDosis().sort(java.util.Comparator.comparingInt(EsquemaVacunalDosis::getOrden));
    }

    // ------------------------------------------------------------------ utilidades

    private Vacuna obtenerVacuna(Long id) {
        return vacunaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vacuna", id));
    }

    private EsquemaVacunal obtenerEsquema(Long id) {
        return esquemaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Esquema vacunal", id));
    }

    private Especie especie(Long id) {
        return especieRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Especie", id));
    }
}
