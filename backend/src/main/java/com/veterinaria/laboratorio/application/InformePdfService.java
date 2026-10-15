package com.veterinaria.laboratorio.application;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.archivos.ArchivosApi;
import com.veterinaria.archivos.ArchivosApi.ArchivoInfo;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformeImagenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.InformePatologiaResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ItemResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.OrdenResponse;
import com.veterinaria.laboratorio.api.dto.LabDtos.ResultadoResponse;
import com.veterinaria.laboratorio.domain.TipoExamen;
import com.veterinaria.laboratorio.internal.CatalogoRepository;
import com.veterinaria.laboratorio.internal.CatalogoRepository.ParametroInfo;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.clinico.PacienteLector;
import com.veterinaria.shared.clinico.ProfesionalBasico;
import com.veterinaria.shared.clinico.ProfesionalLector;
import com.veterinaria.shared.error.BusinessException;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.pdf.DocumentoPdf;
import com.veterinaria.shared.pdf.DocumentoPdf.Campo;
import com.veterinaria.shared.pdf.DocumentoPdf.Celda;
import com.veterinaria.shared.pdf.DocumentoPdf.Imagen;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Informe PDF de una orden (laboratorio, imagenologia y patologia) con membrete, datos del paciente y tutor, tabla de
 * parametros con banderas coloreadas, informes estructurados con miniaturas de las imagenes, interpretacion y firma
 * del veterinario que valido. Los examenes sin validar se marcan como PRELIMINARES.
 */
@Service
public class InformePdfService {

    public record Informe(String codigo, byte[] pdf) {
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color ROJO = new Color(0xB7, 0x1C, 0x1C);
    private static final Color FONDO_ROJO = new Color(0xFF, 0xEB, 0xEE);
    private static final Color AMBAR = new Color(0xE6, 0x51, 0x00);
    private static final Color FONDO_AMBAR = new Color(0xFF, 0xF3, 0xE0);

    private final OrdenService ordenService;
    private final DocumentoPdf pdf;
    private final PacienteLector pacientes;
    private final ProfesionalLector profesionales;
    private final CatalogoRepository catalogo;
    private final ArchivosApi archivos;

    public InformePdfService(OrdenService ordenService, DocumentoPdf pdf, PacienteLector pacientes,
            ProfesionalLector profesionales, CatalogoRepository catalogo, ArchivosApi archivos) {
        this.ordenService = ordenService;
        this.pdf = pdf;
        this.pacientes = pacientes;
        this.profesionales = profesionales;
        this.catalogo = catalogo;
        this.archivos = archivos;
    }

    @Transactional(readOnly = true)
    public Informe generar(Long ordenId, Long itemId) {
        OrdenResponse o = ordenService.obtener(ordenId);
        List<ItemResponse> items = o.items().stream().filter(i -> !"CANCELADO".equals(i.estado()))
                .filter(i -> itemId == null || i.id().equals(itemId)).toList();
        if (itemId != null && items.isEmpty()) {
            throw new ResourceNotFoundException("Item de orden", itemId);
        }
        if (items.isEmpty()) {
            throw new BusinessException("La orden no tiene examenes activos para informar");
        }
        PacienteBasico p = pacientes.obtener(o.pacienteId());
        var b = pdf.nuevo(titulo(items), o.codigo()).paciente(p, ZonaClinica.local(o.solicitadaEn()).toLocalDate());

        b.seccion("Orden");
        b.campos(List.of(
                new Campo("Orden", o.codigo()), new Campo("Prioridad", o.prioridad()),
                new Campo("Estado", o.estado()),
                new Campo("Solicitada", ZonaClinica.local(o.solicitadaEn()).format(FECHA_HORA)),
                new Campo("Solicitada por", o.solicitadoPor() == null ? null : o.solicitadoPor().nombre()),
                new Campo("Muestra tomada", o.muestraTomadaEn() == null ? null : ZonaClinica.local(o.muestraTomadaEn()).format(FECHA_HORA)),
                new Campo("Laboratorio externo", o.laboratorioExterno()), new Campo("Consulta", o.consultaId() == null ? null : "#" + o.consultaId())), 2);
        b.texto("Indicacion clinica", o.indicacionClinica());

        boolean criticos = false;
        boolean preliminar = false;
        Long ultimoValidador = null;
        java.time.Instant ultimoValidadoEn = null;
        for (ItemResponse i : items) {
            b.seccion(i.examenNombre() + " (" + i.examenCodigo() + ")");
            if ("VALIDADO".equals(i.estado())) {
                b.nota("VALIDADO por " + (i.validadoPor() == null ? "-" : i.validadoPor().nombre()) + " el "
                        + ZonaClinica.local(i.validadoEn()).format(FECHA_HORA) + (i.corregido() ? " (version " + i.version() + ", corregido)" : ""));
                if (ultimoValidadoEn == null || i.validadoEn().isAfter(ultimoValidadoEn)) {
                    ultimoValidadoEn = i.validadoEn();
                    ultimoValidador = i.validadoPor() == null ? null : i.validadoPor().id();
                }
            } else {
                preliminar = true;
                b.nota("PRELIMINAR - pendiente de validacion del veterinario (estado: " + i.estado() + ")");
            }
            switch (TipoExamen.de(i.tipo())) {
                case RESULTADOS -> criticos |= resultados(b, i);
                case IMAGEN -> imagen(b, i);
                case PATOLOGIA -> patologia(b, i);
            }
            b.texto("Interpretacion del veterinario", i.interpretacion());
        }
        if (criticos) {
            b.nota("ATENCION: el informe contiene valores criticos (LL/HH).");
        }
        b.nota("L/H: fuera del rango de referencia; LL/HH: valor critico; A: resultado cualitativo anormal. Los rangos son "
                + "orientativos y dependen de la especie, sexo y edad del paciente y del metodo del laboratorio.");

        ProfesionalBasico firma = ultimoValidador == null ? null : profesionales.buscar(ultimoValidador).orElse(null);
        if (firma != null) {
            b.firma(firma.nombre(), (firma.especialidad() == null ? "Medico veterinario" : firma.especialidad())
                    + (firma.numeroProfesional() == null ? "" : " - T.P. " + firma.numeroProfesional()));
        } else if (preliminar) {
            b.firma(null, "Pendiente de validacion");
        }
        return new Informe(o.codigo(), b.construir());
    }

    private static String titulo(List<ItemResponse> items) {
        boolean lab = items.stream().anyMatch(i -> TipoExamen.de(i.tipo()) == TipoExamen.RESULTADOS);
        boolean img = items.stream().anyMatch(i -> TipoExamen.de(i.tipo()) == TipoExamen.IMAGEN);
        boolean pat = items.stream().anyMatch(i -> TipoExamen.de(i.tipo()) == TipoExamen.PATOLOGIA);
        if (lab && !img && !pat) {
            return "Informe de laboratorio";
        }
        if (img && !lab && !pat) {
            return "Informe de imagenologia";
        }
        if (pat && !lab && !img) {
            return "Informe de patologia";
        }
        return "Informe de examenes diagnosticos";
    }

    // ------------------------------------------------------------------ laboratorio

    private boolean resultados(DocumentoPdf.Builder b, ItemResponse item) {
        Map<Long, ParametroInfo> params = catalogo.parametrosInfo(item.examenId()).stream()
                .collect(Collectors.toMap(ParametroInfo::id, x -> x));
        List<List<Celda>> filas = new ArrayList<>();
        boolean criticos = false;
        for (ResultadoResponse r : item.resultados()) {
            String bandera = r.bandera();
            Color color = null;
            Color fondo = null;
            if ("LL".equals(bandera) || "HH".equals(bandera)) {
                color = ROJO;
                fondo = FONDO_ROJO;
            } else if ("L".equals(bandera) || "H".equals(bandera)) {
                color = AMBAR;
                fondo = FONDO_AMBAR;
            } else if ("A".equals(bandera)) {
                color = ROJO;
            }
            criticos |= r.critico();
            boolean resaltar = color != null;
            ParametroInfo pi = params.get(r.parametroId());
            String ref = r.refMin() != null || r.refMax() != null
                    ? (r.refMin() == null ? "" : num(r.refMin())) + " - " + (r.refMax() == null ? "" : num(r.refMax()))
                    : pi != null && pi.valorNormalTexto() != null ? "Normal: " + pi.valorNormalTexto() : "-";
            String valor = r.valorTexto() != null && r.valorNumerico() != null ? r.valorTexto()
                    : r.valorNumerico() != null ? num(r.valorNumerico()) : r.valorTexto();
            Celda cValor = Celda.de(valor);
            Celda cBandera = Celda.de(bandera == null ? "-" : etiqueta(bandera) + (r.critico() ? " (CRITICO)" : ""));
            if (resaltar) {
                cValor = cValor.color(color).enNegrita();
                cBandera = cBandera.color(color).enNegrita();
                if (fondo != null) {
                    cValor = cValor.fondo(fondo);
                    cBandera = cBandera.fondo(fondo);
                }
            }
            filas.add(List.of(Celda.de(r.parametroNombre()), cValor, Celda.de(r.unidad()), Celda.de(ref), cBandera));
        }
        b.tabla(List.of("Parametro", "Resultado", "Unidad", "Referencia", "Bandera"), new float[] {3.2f, 1.6f, 1.3f, 1.8f, 2f}, filas);
        String correcciones = item.resultados().stream().filter(r -> r.motivoCorreccion() != null)
                .map(r -> r.parametroCodigo() + ": " + r.motivoCorreccion()).collect(Collectors.joining("; "));
        if (!correcciones.isEmpty()) {
            b.nota("Correcciones: " + correcciones);
        }
        return criticos;
    }

    private static String etiqueta(String bandera) {
        return switch (bandera) {
            case "N" -> "Normal";
            case "L" -> "Bajo";
            case "H" -> "Alto";
            case "LL" -> "Bajo critico";
            case "HH" -> "Alto critico";
            case "A" -> "Anormal";
            default -> bandera;
        };
    }

    private static String num(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ imagen y patologia

    private void imagen(DocumentoPdf.Builder b, ItemResponse item) {
        InformeImagenResponse inf = item.informeImagen();
        if (inf == null) {
            b.parrafo("Sin informe registrado.");
        } else {
            b.campos(List.of(new Campo("Region / organo", inf.region()), new Campo("Equipo", inf.equipo()),
                    new Campo("Fecha del estudio", inf.fechaEstudio() == null ? null : inf.fechaEstudio().format(FECHA)),
                    new Campo("Informado por", inf.informadoPor() == null ? null : inf.informadoPor().nombre())), 2);
            b.texto("Tecnica", inf.tecnica());
            b.texto("Hallazgos", inf.hallazgos());
            b.texto("Impresion diagnostica", inf.impresionDiagnostica());
            b.texto("Recomendaciones", inf.recomendaciones());
        }
        galeria(b, item.archivos());
    }

    private void patologia(DocumentoPdf.Builder b, ItemResponse item) {
        InformePatologiaResponse inf = item.informePatologia();
        if (inf == null) {
            b.parrafo("Sin informe registrado.");
        } else {
            b.campos(List.of(new Campo("Tipo de muestra", inf.tipoMuestra()), new Campo("Sitio anatomico", inf.sitioAnatomico()),
                    new Campo("Fecha de toma", inf.fechaToma() == null ? null : inf.fechaToma().format(FECHA)),
                    new Campo("Laboratorio externo", inf.laboratorioExterno()),
                    new Campo("Caso externo", inf.numeroCasoExterno()), new Campo("Patologo", inf.patologo()),
                    new Campo("Fecha del informe", inf.fechaInforme() == null ? null : inf.fechaInforme().format(FECHA)),
                    new Campo("Informado por", inf.informadoPor() == null ? null : inf.informadoPor().nombre())), 2);
            b.texto("Descripcion macroscopica", inf.descripcionMacroscopica());
            b.texto("Descripcion microscopica", inf.descripcionMicroscopica());
            b.texto("Diagnostico morfologico", inf.diagnosticoMorfologico());
            b.texto("Margenes", inf.margenes());
            b.texto("Comentarios", inf.comentarios());
        }
        galeria(b, item.archivos());
    }

    /** Miniaturas de las imagenes JPG/PNG del item; los demas archivos se listan por nombre. */
    private void galeria(DocumentoPdf.Builder b, List<ArchivoInfo> adjuntos) {
        List<Imagen> imagenes = new ArrayList<>();
        List<String> otros = new ArrayList<>();
        for (ArchivoInfo a : adjuntos) {
            var mini = a.tieneMiniatura() ? archivos.leerMiniatura(a.id()) : java.util.Optional.<byte[]>empty();
            if (mini.isPresent()) {
                imagenes.add(new Imagen(mini.get(), (a.descripcion() != null ? a.descripcion() : a.nombreOriginal())));
            } else {
                otros.add(a.nombreOriginal() + " (" + a.tipo() + ")");
            }
        }
        if (!imagenes.isEmpty()) {
            b.seccion("Imagenes del estudio");
            b.imagenes(imagenes);
        }
        if (!otros.isEmpty()) {
            b.nota("Otros archivos adjuntos: " + String.join(", ", otros));
        }
    }
}
