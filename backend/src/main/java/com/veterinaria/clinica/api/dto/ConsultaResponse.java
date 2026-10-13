package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.veterinaria.citas.api.dto.SalaResponse;
import com.veterinaria.citas.api.dto.TipoCitaResponse;
import com.veterinaria.citas.domain.Cita;
import com.veterinaria.clinica.domain.Consulta;
import com.veterinaria.seguridad.api.dto.UsuarioResponse;
import com.veterinaria.tutores.api.dto.PacienteResumen;

public record ConsultaResponse(
        Long id,
        String codigo,
        Long citaId,
        TipoCitaResponse tipoCita,
        SalaResponse sala,
        PacienteResumen paciente,
        UsuarioResponse veterinario,
        LocalDate fecha,
        String estado,
        boolean cerrada,
        Instant cerradaEn,
        String firmaHash,
        String tipoConsulta,
        String triaje,
        LocalDate proximaRevision,
        Long plantillaId,
        String motivo,
        String sintomatologia,
        String diagnostico,
        String tratamiento,
        String observaciones,
        String anamnesis,
        String subjetivo,
        String objetivo,
        String evaluacion,
        String plan,
        String pronostico,
        BigDecimal pesoKg,
        BigDecimal temperaturaC,
        Integer frecuenciaCardiaca,
        Integer frecuenciaRespiratoria,
        boolean activo,
        List<ConsultaMedicamentoDto> medicamentos,
        List<ConsultaVacunaDto> vacunas,
        List<SignoVitalResponse> signosVitales,
        List<DiagnosticoResponse> diagnosticos,
        List<RecetaResponse> recetas) {

    public static ConsultaResponse from(Consulta c) {
        Cita cita = c.getCita();
        return new ConsultaResponse(c.getId(), c.getCodigo(), cita == null ? null : cita.getId(),
                cita == null ? null : TipoCitaResponse.from(cita.getTipoCita()),
                cita == null ? null : SalaResponse.from(cita.getSala()),
                PacienteResumen.from(c.getPaciente()), UsuarioResponse.from(c.getVeterinario()), c.getFecha(),
                c.getEstado(), c.estaCerrada(), c.getCerradaEn(), c.getFirmaHash(), c.getTipoConsulta(),
                c.getTriaje(), c.getProximaRevision(), c.getPlantillaId(),
                c.getMotivo(), c.getSintomatologia(), c.getDiagnostico(), c.getTratamiento(), c.getObservaciones(),
                c.getAnamnesis(), c.getSubjetivo(), c.getObjetivo(), c.getEvaluacion(), c.getPlan(), c.getPronostico(),
                c.getPesoKg(), c.getTemperaturaC(), c.getFrecuenciaCardiaca(), c.getFrecuenciaRespiratoria(),
                c.isActivo(),
                c.getMedicamentos().stream().map(ConsultaMedicamentoDto::from).toList(),
                c.getVacunas().stream().map(ConsultaVacunaDto::from).toList(),
                c.getSignosVitales().stream().map(SignoVitalResponse::from).toList(),
                c.getDiagnosticos().stream().map(DiagnosticoResponse::from).toList(),
                c.getRecetas().stream().map(RecetaResponse::from).toList());
    }
}
