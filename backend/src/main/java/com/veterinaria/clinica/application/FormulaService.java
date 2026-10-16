package com.veterinaria.clinica.application;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.clinica.api.dto.PlanDtos.ControlResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanItemResponse;
import com.veterinaria.clinica.api.dto.PlanDtos.PlanResponse;
import com.veterinaria.clinica.domain.Consulta;
import com.veterinaria.clinica.domain.Diagnostico;
import com.veterinaria.clinica.domain.Receta;
import com.veterinaria.clinica.domain.RecetaItem;
import com.veterinaria.clinica.internal.ConsultaRepository;
import com.veterinaria.clinica.internal.RecetaRepository;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.clinico.ProfesionalBasico;
import com.veterinaria.shared.clinico.ProfesionalLector;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.pdf.DocumentoPdf;
import com.veterinaria.shared.pdf.DocumentoPdf.Campo;
import com.veterinaria.shared.pdf.DocumentoPdf.Celda;

/**
 * Formula medica en PDF: membrete, paciente y tutor, diagnosticos, medicamentos (concentracion/presentacion, dosis, via,
 * frecuencia, duracion, cantidad, indicaciones), recomendaciones, plan de tratamiento, controles, advertencias, firma con
 * tarjeta profesional, numero de formula y codigo de verificacion (publico: {@code GET /api/verificar/{codigo}}).
 */
@Service
public class FormulaService {

    public record Formula(Long recetaId, Long consultaId, Long pacienteId, String numero, String codigoVerificacion,
            boolean consultaCerrada, boolean anulada, byte[] pdf) {
    }

    static final String ADVERTENCIAS_GENERALES = "Administre los medicamentos exactamente como fueron indicados y complete el "
            + "tratamiento. No automedique ni comparta estos medicamentos con otras mascotas. Mantengalos fuera del alcance de "
            + "ninos y animales. Ante vomito persistente, reacciones alergicas, decaimiento o cualquier signo de alarma, "
            + "comuniquese de inmediato con la clinica.";

    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecetaRepository recetas;
    private final ConsultaRepository consultas;
    private final PlanTratamientoService planes;
    private final PacienteLector pacientes;
    private final ProfesionalLector profesionales;
    private final DocumentoPdf pdf;
    private final JdbcClient jdbc;
    private final String publicUrl;

    public FormulaService(RecetaRepository recetas, ConsultaRepository consultas, PlanTratamientoService planes,
            PacienteLector pacientes, ProfesionalLector profesionales, DocumentoPdf pdf, JdbcClient jdbc,
            @Value("${vet.public-url:http://localhost:9010}") String publicUrl) {
        this.recetas = recetas;
        this.consultas = consultas;
        this.planes = planes;
        this.pacientes = pacientes;
        this.profesionales = profesionales;
        this.pdf = pdf;
        this.jdbc = jdbc;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Transactional
    public Formula porReceta(Long recetaId) {
        Receta r = recetas.findById(recetaId).orElseThrow(() -> new ResourceNotFoundException("Receta", recetaId));
        return generar(r);
    }

    /** Formula de la consulta: su receta mas reciente que no este anulada. */
    @Transactional
    public Formula porConsulta(Long consultaId) {
        Consulta c = consultas.findById(consultaId).orElseThrow(() -> new ResourceNotFoundException("Consulta", consultaId));
        Receta r = c.getRecetas().stream().filter(x -> !"CANCELADA".equals(x.getEstado()))
                .max(Comparator.comparing(Receta::getId))
                .orElseThrow(() -> new ResourceNotFoundException("Formula (receta vigente) de la consulta", consultaId));
        return generar(r);
    }

    private Formula generar(Receta r) {
        Consulta c = r.getConsulta();
        String[] codigos = asegurarCodigos(r.getId());
        String numero = codigos[0];
        String codigo = codigos[1];
        PacienteBasico p = pacientes.obtener(r.getPaciente().getId());
        ProfesionalBasico prof = profesionales.buscar(r.getVeterinario().getId()).orElse(null);
        boolean anulada = "CANCELADA".equals(r.getEstado());
        var plan = planes.buscar(c.getId()).orElse(null);

        var b = pdf.nuevo("Formula medica", numero).paciente(p, r.getFecha());
        b.seccion("Formula");
        b.campos(List.of(new Campo("Numero de formula", numero), new Campo("Fecha", r.getFecha().format(FECHA)),
                new Campo("Consulta", c.getCodigo()),
                new Campo("Valida hasta", r.getValidaHasta() == null ? null : r.getValidaHasta().format(FECHA)),
                new Campo("Veterinario", r.getVeterinario().getNombre())), 2);
        if (anulada) {
            b.nota("FORMULA ANULADA: este documento no tiene validez.");
        } else if (!c.estaCerrada()) {
            b.nota("BORRADOR: la consulta aun esta abierta y la formula puede cambiar.");
        }

        b.seccion("Diagnostico(s)");
        List<Diagnostico> dx = c.getDiagnosticos().stream().sorted(Comparator.comparingInt(Diagnostico::getOrden)).toList();
        if (dx.isEmpty()) {
            b.parrafo(c.getDiagnostico() == null || c.getDiagnostico().isBlank() ? "No registrado" : c.getDiagnostico());
        } else {
            for (Diagnostico d : dx) {
                b.parrafo("- " + d.getDescripcion() + (d.getCodigoCie() == null ? "" : " (CIE " + d.getCodigoCie() + ")")
                        + (d.getTipo() == null ? "" : " - " + d.getTipo().toLowerCase()));
            }
        }

        b.seccion("Medicamentos prescritos");
        List<List<Celda>> filas = new ArrayList<>();
        for (RecetaItem i : r.getItems()) {
            filas.add(List.of(Celda.de(i.getMedicamento()).enNegrita(), Celda.de(unir(i.getConcentracion(), i.getPresentacion())),
                    Celda.de(i.getDosis()), Celda.de(i.getVia()), Celda.de(i.getFrecuencia()), Celda.de(i.getDuracion()),
                    Celda.de(i.getCantidad() == null ? null : String.valueOf(i.getCantidad())), Celda.de(i.getIndicaciones())));
        }
        b.tabla(List.of("Medicamento", "Concentracion / presentacion", "Dosis", "Via", "Frecuencia", "Duracion", "Cant.",
                "Indicaciones"), new float[] {2.4f, 2.2f, 1.5f, 1f, 1.5f, 1.4f, 0.8f, 2.6f}, filas);
        if (r.isEsControlada()) {
            b.nota("Contiene medicamento de control especial.");
        }
        b.texto("Indicaciones generales", r.getIndicacionesGenerales());

        if (plan != null) {
            planEnFormula(b, plan);
        }
        controlesEnFormula(b, planes.controles(c.getId()));
        if (c.getProximaRevision() != null) {
            b.texto("Proxima revision sugerida", c.getProximaRevision().format(FECHA));
        }

        b.seccion("Advertencias");
        if (plan != null && plan.advertencias() != null) {
            b.parrafo(plan.advertencias());
        }
        b.parrafo(ADVERTENCIAS_GENERALES);

        if (prof != null) {
            b.firma(prof.nombre(), "Medico veterinario" + (prof.especialidad() == null ? "" : " - " + prof.especialidad())
                    + (prof.numeroProfesional() == null ? " - T.P. no registrada" : " - T.P. " + prof.numeroProfesional()));
        } else {
            b.firma(r.getVeterinario().getNombre(), "Medico veterinario - T.P. no registrada");
        }
        b.seccion("Verificacion del documento");
        b.texto("Codigo de verificacion", formatear(codigo));
        b.nota("Compruebe la autenticidad en " + publicUrl + "/api/verificar/" + codigo);
        return new Formula(r.getId(), c.getId(), r.getPaciente().getId(), numero, codigo, c.estaCerrada(), anulada, b.construir());
    }

    private static void planEnFormula(DocumentoPdf.Builder b, PlanResponse plan) {
        if (plan.recomendaciones() != null) {
            b.seccion("Recomendaciones y cuidados");
            b.parrafo(plan.recomendaciones());
        }
        if (plan.objetivo() != null || !plan.items().isEmpty()) {
            b.seccion("Plan de tratamiento");
            b.texto("Objetivo", plan.objetivo());
            if (!plan.items().isEmpty()) {
                List<List<Celda>> filas = new ArrayList<>();
                for (PlanItemResponse i : plan.items()) {
                    filas.add(List.of(Celda.de(i.fase()), Celda.de(i.tipo()), Celda.de(i.descripcion()),
                            Celda.de(i.fechaInicio() == null ? null : i.fechaInicio().format(FECHA)),
                            Celda.de(i.fechaFin() == null ? null : i.fechaFin().format(FECHA))));
                }
                b.tabla(List.of("Fase", "Tipo", "Descripcion", "Desde", "Hasta"), new float[] {1.8f, 1.4f, 4.4f, 1.2f, 1.2f}, filas);
            }
        }
    }

    private static void controlesEnFormula(DocumentoPdf.Builder b, List<ControlResponse> todos) {
        List<ControlResponse> controles = todos.stream().filter(c -> !"CANCELADO".equals(c.estado())).toList();
        if (!controles.isEmpty()) {
            b.seccion("Controles y proxima cita sugerida");
            List<List<Celda>> filas = new ArrayList<>();
            for (ControlResponse c : controles) {
                filas.add(List.of(Celda.de(c.fechaSugerida().format(FECHA)), Celda.de(c.motivo()), Celda.de(c.estado())));
            }
            b.tabla(List.of("Fecha sugerida", "Motivo", "Estado"), new float[] {1.6f, 5.4f, 1.6f}, filas);
        }
    }

    private static String unir(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return b == null || b.isBlank() ? a : a + " / " + b;
    }

    static String formatear(String codigo) {
        return codigo.length() == 10 ? codigo.substring(0, 5) + "-" + codigo.substring(5) : codigo;
    }

    /** Asigna (una sola vez) el numero de formula y el codigo de verificacion; devuelve {numero, codigo}. */
    private String[] asegurarCodigos(Long recetaId) {
        var actual = jdbc.sql("select numero_formula, codigo_verificacion from receta where id = :id").param("id", recetaId)
                .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).single();
        if (actual[0] != null && actual[1] != null) {
            return actual;
        }
        String codigo = aleatorio(10);
        jdbc.sql("""
                update receta set numero_formula = 'FOR-' || to_char(now() at time zone 'America/Bogota', 'YYYYMMDD') || '-'
                                                    || lpad(nextval('seq_formula_medica')::text, 5, '0'),
                       codigo_verificacion = :c, formula_emitida_en = now()
                 where id = :id and numero_formula is null
                """).param("c", codigo).param("id", recetaId).update();
        return jdbc.sql("select numero_formula, codigo_verificacion from receta where id = :id").param("id", recetaId)
                .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).single();
    }

    private static String aleatorio(int largo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
