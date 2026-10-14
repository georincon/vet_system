package com.veterinaria.tutores.application;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.shared.error.ConflictException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.text.Textos;
import com.veterinaria.shared.web.PageResponse;
import com.veterinaria.tutores.api.dto.PacienteResumen;
import com.veterinaria.tutores.api.dto.TutorRequest;
import com.veterinaria.tutores.api.dto.TutorResponse;
import com.veterinaria.tutores.domain.Tutor;
import com.veterinaria.tutores.internal.PacienteRepository;
import com.veterinaria.tutores.internal.TutorRepository;

/** Casos de uso de tutores. */
@Service
public class TutorService {

    private final TutorRepository tutorRepository;
    private final PacienteRepository pacienteRepository;

    public TutorService(TutorRepository tutorRepository, PacienteRepository pacienteRepository) {
        this.tutorRepository = tutorRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TutorResponse> list(String q, Pageable pageable) {
        String termino = q == null ? "" : q.trim();
        return PageResponse.from(tutorRepository.buscar(termino, pageable), TutorResponse::from);
    }

    @Transactional(readOnly = true)
    public TutorResponse find(Long id) {
        return TutorResponse.from(obtener(id));
    }

    @Transactional(readOnly = true)
    public List<PacienteResumen> pacientesDe(Long tutorId) {
        obtener(tutorId);
        return pacienteRepository.findDistinctByTutoresTutorIdOrderByNombreAsc(tutorId).stream()
                .map(PacienteResumen::from)
                .toList();
    }

    @Transactional
    public TutorResponse create(TutorRequest request) {
        String numero = request.numeroDocumento().trim();
        if (tutorRepository.existsByTipoDocumentoAndNumeroDocumento(request.tipoDocumento(), numero)) {
            throw documentoDuplicado(numero);
        }
        Tutor tutor = new Tutor();
        aplicar(tutor, request);
        tutor.setActivo(request.activo() == null || request.activo());
        return TutorResponse.from(tutorRepository.saveAndFlush(tutor));
    }

    @Transactional
    public TutorResponse update(Long id, TutorRequest request) {
        Tutor tutor = obtener(id);
        String numero = request.numeroDocumento().trim();
        if (tutorRepository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(request.tipoDocumento(), numero, id)) {
            throw documentoDuplicado(numero);
        }
        aplicar(tutor, request);
        if (request.activo() != null) {
            tutor.setActivo(request.activo());
        }
        return TutorResponse.from(tutorRepository.saveAndFlush(tutor));
    }

    private Tutor obtener(Long id) {
        return tutorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tutor", id));
    }

    private static void aplicar(Tutor tutor, TutorRequest r) {
        tutor.setTipoDocumento(r.tipoDocumento());
        tutor.setNumeroDocumento(r.numeroDocumento().trim());
        tutor.setNombres(r.nombres().trim());
        tutor.setApellidos(Textos.limpiar(r.apellidos()));
        tutor.setTelefono(Textos.limpiar(r.telefono()));
        tutor.setTelefonoAlt(Textos.limpiar(r.telefonoAlt()));
        tutor.setEmail(Textos.limpiar(r.email()));
        tutor.setDireccion(Textos.limpiar(r.direccion()));
        tutor.setCiudad(Textos.limpiar(r.ciudad()));
        tutor.setNotas(Textos.limpiar(r.notas()));
        if (r.aceptaEmail() != null) {
            tutor.setAceptaEmail(r.aceptaEmail());
        }
        if (r.aceptaWhatsapp() != null) {
            tutor.setAceptaWhatsapp(r.aceptaWhatsapp());
        }
        if (r.aceptaSms() != null) {
            tutor.setAceptaSms(r.aceptaSms());
        }
        if (r.autorizaTratamientoDatos() != null) {
            if (r.autorizaTratamientoDatos() && !tutor.isAutorizaTratamientoDatos()) {
                tutor.setFechaAutorizacionDatos(java.time.Instant.now());
            }
            tutor.setAutorizaTratamientoDatos(r.autorizaTratamientoDatos());
        }
    }

    private static ConflictException documentoDuplicado(String numero) {
        return new ConflictException("Ya existe un tutor con el documento '%s'.".formatted(numero));
    }
}
