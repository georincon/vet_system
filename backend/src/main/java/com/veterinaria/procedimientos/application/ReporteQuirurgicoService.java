package com.veterinaria.procedimientos.application;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.procedimientos.api.dto.ProcDtos.ConsentimientoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.MonitoreoResponse;
import com.veterinaria.procedimientos.api.dto.ProcDtos.ProcedimientoResponse;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.clinico.ProfesionalBasico;
import com.veterinaria.shared.clinico.ProfesionalLector;
import com.veterinaria.shared.pdf.DocumentoPdf;
import com.veterinaria.shared.pdf.DocumentoPdf.Campo;
import com.veterinaria.shared.pdf.DocumentoPdf.Celda;
import com.veterinaria.shared.time.ZonaClinica;

/** Reporte quirurgico / de procedimiento en PDF (membrete, paciente y tutor, equipo, anestesia, tecnica, consentimiento y firma). */
@Service
public class ReporteQuirurgicoService {

    public record Reporte(String codigo, byte[] pdf) {
    }

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProcedimientoService service;
    private final DocumentoPdf pdf;
    private final PacienteLector pacientes;
    private final ProfesionalLector profesionales;

    public ReporteQuirurgicoService(ProcedimientoService service, DocumentoPdf pdf, PacienteLector pacientes,
            ProfesionalLector profesionales) {
        this.service = service;
        this.pdf = pdf;
        this.pacientes = pacientes;
        this.profesionales = profesionales;
    }

    @Transactional(readOnly = true)
    public Reporte generar(Long id) {
        ProcedimientoResponse r = service.obtener(id);
        var paciente = pacientes.obtener(r.pacienteId());
        boolean cirugia = "CIRUGIA".equals(r.tipo());
        var fechaRef = ZonaClinica.local(r.inicioEn() != null ? r.inicioEn() : r.programadaPara() != null ? r.programadaPara() : r.creadoEn()).toLocalDate();
        var b = pdf.nuevo(cirugia ? "Reporte quirurgico" : "Reporte de procedimiento", r.codigo()).paciente(paciente, fechaRef);

        b.seccion(cirugia ? "Cirugia" : "Procedimiento");
        b.campos(List.of(new Campo("Procedimiento", r.procedimiento()), new Campo("Estado", r.estado()),
                new Campo("Programada", fecha(r.programadaPara())), new Campo("Inicio", fecha(r.inicioEn())),
                new Campo("Fin", fecha(r.finEn())), new Campo("Urgencia", r.urgencia() ? "Si" : "No"),
                new Campo("Consulta", r.consultaId() == null ? null : "#" + r.consultaId()),
                new Campo("Peso (kg)", r.pesoKg() == null ? null : r.pesoKg().stripTrailingZeros().toPlainString())), 2);
        b.seccion("Equipo");
        b.campos(List.of(new Campo("Responsable", r.cirujano().nombre()),
                new Campo("Ayudante", r.ayudante() == null ? null : r.ayudante().nombre()),
                new Campo("Anestesiologo", r.anestesiologo() == null ? null : r.anestesiologo().nombre())), 3);
        b.seccion("Valoracion preoperatoria");
        b.campos(List.of(new Campo("Riesgo ASA", r.asa() == null ? null : String.valueOf(r.asa())),
                new Campo("Ayuno (h)", r.ayunoHoras() == null ? null : String.valueOf(r.ayunoHoras()))), 2);
        b.texto("Diagnostico preoperatorio", r.diagnosticoPreoperatorio());

        if (r.anestesiaTipo() != null || r.premedicacion() != null || r.induccion() != null || r.mantenimiento() != null
                || r.analgesia() != null) {
            b.seccion("Anestesia");
            b.texto("Tipo", r.anestesiaTipo());
            b.texto("Premedicacion", r.premedicacion());
            b.texto("Induccion", r.induccion());
            b.texto("Mantenimiento", r.mantenimiento());
            b.texto("Analgesia", r.analgesia());
        }
        if (!r.monitoreo().isEmpty()) {
            b.seccion("Monitoreo anestesico");
            List<List<Celda>> filas = new ArrayList<>();
            for (MonitoreoResponse m : r.monitoreo()) {
                filas.add(List.of(Celda.de(ZonaClinica.local(m.registradoEn()).format(DateTimeFormatter.ofPattern("HH:mm"))),
                        Celda.de(str(m.frecuenciaCardiaca())), Celda.de(str(m.frecuenciaRespiratoria())), Celda.de(str(m.spo2())),
                        Celda.de(str(m.etco2())), Celda.de(str(m.presionSistolica())), Celda.de(str(m.temperatura())),
                        Celda.de(str(m.agenteInhalatorioPct())), Celda.de(str(m.fluidoMlH())), Celda.de(m.notas())));
            }
            b.tabla(List.of("Hora", "FC", "FR", "SpO2", "EtCO2", "PAS", "T (C)", "Agente %", "Fluido ml/h", "Notas"),
                    new float[] {1f, 0.8f, 0.8f, 0.9f, 0.9f, 0.9f, 0.9f, 1f, 1.1f, 2.4f}, filas);
        }
        b.seccion("Tecnica y hallazgos");
        b.texto("Descripcion de la tecnica", r.descripcionTecnica());
        b.texto("Hallazgos", r.hallazgos());
        b.texto("Complicaciones", r.complicaciones());
        b.texto("Material de sutura", r.materialSutura());
        if (r.muestrasHistopatologia()) {
            b.nota("Se enviaron muestras para histopatologia.");
        }
        if (!r.insumos().isEmpty()) {
            b.seccion("Insumos utilizados");
            List<List<Celda>> filas = r.insumos().stream().map(i -> List.of(Celda.de(i.descripcion()),
                    Celda.de(i.cantidad().stripTrailingZeros().toPlainString()), Celda.de(i.lote()), Celda.de(i.notas()))).toList();
            b.tabla(List.of("Insumo", "Cantidad", "Lote", "Notas"), new float[] {4f, 1f, 1.5f, 3f}, filas);
        }
        b.seccion("Resultado y seguimiento");
        b.texto("Diagnostico postoperatorio", r.diagnosticoPostoperatorio());
        b.texto("Resultado", r.resultado());
        b.texto("Cuidados postoperatorios", r.indicacionesPostoperatorias());
        b.texto("Controles", r.controles());
        b.texto("Proximo control", r.proximoControl() == null ? null : r.proximoControl().format(FECHA));
        b.texto("Motivo de cancelacion", r.motivoCancelacion());

        ConsentimientoResponse k = r.consentimiento();
        b.seccion("Consentimiento informado");
        if (k == null) {
            b.parrafo("Sin consentimiento registrado" + (r.urgencia() ? " (procedimiento de urgencia)." : "."));
        } else {
            b.campos(List.of(new Campo("Estado", k.estado()), new Campo("Tutor", k.tutor().nombre()),
                    new Campo("Firmante", k.firmanteNombre()), new Campo("Documento", k.firmanteDocumento()),
                    new Campo("Fecha", fecha(k.firmadoEn())), new Campo("Metodo", k.metodoFirma()),
                    new Campo("Archivo firmado", k.archivoId() == null ? "No" : "Adjunto #" + k.archivoId())), 2);
            b.nota(k.textoFirmado());
        }
        ProfesionalBasico firma = profesionales.buscar(r.cirujano().id()).orElse(null);
        b.firma(firma == null ? r.cirujano().nombre() : firma.nombre(), "Medico veterinario"
                + (firma == null || firma.numeroProfesional() == null ? "" : " - T.P. " + firma.numeroProfesional()));
        return new Reporte(r.codigo(), b.construir());
    }

    private static String fecha(java.time.Instant i) {
        return i == null ? null : ZonaClinica.local(i).format(FECHA_HORA);
    }

    private static String str(Object o) {
        return o == null ? null : o instanceof java.math.BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(o);
    }
}
