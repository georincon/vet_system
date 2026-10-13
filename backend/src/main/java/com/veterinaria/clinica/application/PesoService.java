package com.veterinaria.clinica.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.domain.PesoRegistro;
import com.veterinaria.clinica.internal.PesoRegistroRepository;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.tutores.domain.Paciente;

/** Registro de peso de pacientes; mantiene {@code paciente.peso_actual} con el peso mas reciente. */
@Service
public class PesoService {

    private final PesoRegistroRepository repository;

    public PesoService(PesoRegistroRepository repository) {
        this.repository = repository;
    }

    /** Guarda el peso y, si no es anterior al ultimo registrado, lo fija como peso actual del paciente. */
    @Transactional
    public PesoRegistro registrar(Paciente paciente, BigDecimal pesoKg, LocalDate fecha) {
        Optional<PesoRegistro> ultimo = repository.findFirstByPacienteIdOrderByFechaDescIdDesc(paciente.getId());
        PesoRegistro registro = new PesoRegistro();
        registro.setPaciente(paciente);
        registro.setPesoKg(pesoKg);
        registro.setFecha(fecha);
        registro.setRegistradoPor(UsuarioActual.id().orElse(null));
        registro = repository.save(registro);
        if (ultimo.isEmpty() || !fecha.isBefore(ultimo.get().getFecha())) {
            paciente.setPesoActual(pesoKg);
        }
        return registro;
    }
}
