package com.veterinaria.clinica.application;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.api.dto.PlanDtos.ControlResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanItemResponse;
import com.veterinaria.clinica.domain.Consulta;
import com.veterinaria.clinica.domain.Diagnostico;
import com.veterinaria.clinica.domain.Receta;
import com.veterinaria.clinica.domain.RecetaItem;
import com.veterinaria.clinica.domain.SignoVital;
import com.veterinaria.clinica.internal.ConsultaRepository;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.clinico.ProfesionalBasico;
import com.veterinaria.shared.clinico.ProfesionalLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.pdf.DocumentoPdf;
import com.veterinaria.shared.pdf.DocumentoPdf.Campo;
import com.veterinaria.shared.pdf.DocumentoPdf.Celda;

/** Resumen de consulta en PDF (motivo, hallazgos, diagnosticos, tratamiento y controles). Solo de consultas CERRADAS. */
@Service
public class ResumenConsultaService {

    public record Resumen(Long consultaId, Long pacienteId, String codigo, byte[] pdf) {
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConsultaRepository consultas;
    private final PlanTratamientoService planes;
    private final PacienteLector pacientes;
    private final ProfesionalLector profesionales;
    private final DocumentoPdf pdf;

    public ResumenConsultaService(ConsultaRepository consultas, PlanTratamientoService planes, PacienteLector pacientes,
            ProfesionalLector profesionales, DocumentoPdf pdf) {
        this.consultas = consultas;
        this.planes = planes;
        this.pacientes = pacientes;
        this.profesionales = profesionales;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true)
    public Resumen generar(Long consultaId) {
        Consulta c = consultas.findById(consultaId).orElseThrow(() -> new ResourceNotFoundException("Consulta", consultaId));
        if (!c.estaCerrada()) {
            throw new BusinessException("La consulta " + c.getCodigo() + " aun esta abierta; el resumen solo se emite de consultas CERRADAS");
        }
        var p = pacientes.obtener(c.getPaciente().getId());
        var b = pdf.nuevo("Resumen de consulta", c.getCodigo()).paciente(p, c.getFecha());
        b.seccion("Consulta");
        b.campos(List.of(new Campo("Codigo", c.getCodigo()), new Campo("Fecha", c.getFecha().format(FECHA)),
                new Campo("Tipo", c.getTipoConsulta()), new Campo("Veterinario", c.getVeterinario().getNombre())), 2);
        b.texto("Motivo de consulta", c.getMotivo());

        b.seccion("Hallazgos");
        b.texto("Anamnesis", c.getAnamnesis());
        b.texto("Subjetivo", c.getSubjetivo());
        b.texto("Examen fisico / objetivo", c.getObjetivo());
        c.getSignosVitales().stream().max(Comparator.comparing(SignoVital::getId)).ifPresent(s -> b.campos(List.of(
                new Campo("Temperatura (C)", s.getTemperatura() == null ? null : s.getTemperatura().toPlainString()),
                new Campo("Frec. cardiaca", s.getFrecuenciaCardiaca() == null ? null : String.valueOf(s.getFrecuenciaCardiaca())),
                new Campo("Frec. respiratoria", s.getFrecuenciaRespiratoria() == null ? null : String.valueOf(s.getFrecuenciaRespiratoria())),
                new Campo("Peso (kg)", s.getPesoKg() == null ? null : s.getPesoKg().toPlainString())), 4));
        b.texto("Evaluacion", c.getEvaluacion());

        b.seccion("Diagnostico(s)");
        List<Diagnostico> dx = c.getDiagnosticos().stream().sorted(Comparator.comparingInt(Diagnostico::getOrden)).toList();
        if (dx.isEmpty()) {
            b.parrafo(c.getDiagnostico() == null || c.getDiagnostico().isBlank() ? "No registrado" : c.getDiagnostico());
        }
        for (Diagnostico d : dx) {
            b.parrafo("- " + d.getDescripcion() + (d.getCodigoCie() == null ? "" : " (CIE " + d.getCodigoCie() + ")"));
        }
        b.texto("Pronostico", c.getPronostico());

        b.seccion("Tratamiento");
        b.texto("Plan", c.getPlan());
        b.texto("Tratamiento", c.getTratamiento());
        List<List<Celda>> filas = new ArrayList<>();
        for (Receta r : c.getRecetas()) {
            if ("CANCELADA".equals(r.getEstado())) {
                continue;
            }
            for (RecetaItem i : r.getItems()) {
                filas.add(List.of(Celda.de(i.getMedicamento()), Celda.de(i.getDosis()), Celda.de(i.getVia()),
                        Celda.de(i.getFrecuencia()), Celda.de(i.getDuracion())));
            }
        }
        if (!filas.isEmpty()) {
            b.tabla(List.of("Medicamento", "Dosis", "Via", "Frecuencia", "Duracion"), new float[] {3f, 1.6f, 1.2f, 1.8f, 1.6f}, filas);
        }
        planes.buscar(c.getId()).ifPresent(plan -> {
            b.texto("Objetivo del plan", plan.objetivo());
            b.texto("Recomendaciones y cuidados", plan.recomendaciones());
            for (PlanItemResponse i : plan.items()) {
                b.parrafo("- " + (i.fase() == null ? "" : i.fase() + ": ") + i.descripcion());
            }
            b.texto("Advertencias", plan.advertencias());
        });

        List<ControlResponse> controles = planes.controles(c.getId()).stream().filter(x -> !"CANCELADO".equals(x.estado())).toList();
        if (!controles.isEmpty() || c.getProximaRevision() != null) {
            b.seccion("Controles de seguimiento");
            for (ControlResponse x : controles) {
                b.parrafo("- " + x.fechaSugerida().format(FECHA) + ": " + x.motivo());
            }
            if (c.getProximaRevision() != null) {
                b.texto("Proxima revision", c.getProximaRevision().format(FECHA));
            }
        }
        ProfesionalBasico prof = profesionales.buscar(c.getVeterinario().getId()).orElse(null);
        b.firma(c.getVeterinario().getNombre(), "Medico veterinario" + (prof == null || prof.numeroProfesional() == null
                ? " - T.P. no registrada" : " - T.P. " + prof.numeroProfesional()));
        return new Resumen(c.getId(), c.getPaciente().getId(), c.getCodigo(), b.construir());
    }
}
