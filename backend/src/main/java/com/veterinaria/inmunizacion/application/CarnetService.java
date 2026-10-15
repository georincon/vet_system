package com.veterinaria.inmunizacion.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.veterinaria.inmunizacion.TipoAplicacion;
import com.veterinaria.inmunizacion.api.dto.InmunizacionResponse;
import com.veterinaria.shared.error.ResourceNotFoundException;
import com.veterinaria.shared.security.UsuarioActual;
import com.veterinaria.shared.time.ZonaClinica;
import com.veterinaria.tutores.domain.Paciente;
import com.veterinaria.tutores.domain.PacienteTutor;
import com.veterinaria.tutores.domain.Tutor;
import com.veterinaria.tutores.internal.PacienteRepository;

/** Genera el carnet de vacunacion y desparasitacion en PDF (OpenPDF) y registra su emision en certificado_vacunacion. */
@Service
public class CarnetService {

    public record Carnet(String numero, byte[] pdf) {
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x1B, 0x5E, 0x20));
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8);

    private final PacienteRepository pacienteRepository;
    private final InmunizacionService inmunizacionService;
    private final JdbcClient jdbc;
    private final String clinica;

    public CarnetService(PacienteRepository pacienteRepository, InmunizacionService inmunizacionService,
            JdbcClient jdbc, @Value("${vet.clinica.nombre:Clinica Veterinaria}") String clinica) {
        this.pacienteRepository = pacienteRepository;
        this.inmunizacionService = inmunizacionService;
        this.jdbc = jdbc;
        this.clinica = clinica;
    }

    @Transactional
    public Carnet generar(Long pacienteId) {
        Paciente p = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", pacienteId));
        List<InmunizacionResponse> vacunas = inmunizacionService.listar(pacienteId, TipoAplicacion.VACUNA, false);
        List<InmunizacionResponse> despar = inmunizacionService.listar(pacienteId, TipoAplicacion.ANTIPARASITARIO, false);
        // la lista viene de la mas reciente a la mas antigua: el carnet va cronologico
        vacunas = vacunas.reversed();
        despar = despar.reversed();

        long id = jdbc.sql("select nextval(pg_get_serial_sequence('certificado_vacunacion', 'id'))")
                .query(Long.class).single();
        String numero = "CAR-%s-%05d".formatted(ZonaClinica.hoy().format(DateTimeFormatter.BASIC_ISO_DATE), id);
        byte[] pdf = construir(p, numero, vacunas, despar);
        jdbc.sql("""
                insert into certificado_vacunacion (id, numero, paciente_id, tipo, emitido_por, hash_documento)
                values (:id, :numero, :paciente, 'CARNET', :usuario, :hash)
                """).param("id", id).param("numero", numero).param("paciente", pacienteId)
                .param("usuario", UsuarioActual.id().orElse(null)).param("hash", sha256(pdf)).update();
        return new Carnet(numero, pdf);
    }

    private byte[] construir(Paciente p, String numero, List<InmunizacionResponse> vacunas,
            List<InmunizacionResponse> despar) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Carnet de vacunacion - " + p.getNombre());
            doc.addAuthor(clinica);
            doc.open();
            Paragraph titulo = new Paragraph(clinica + " - Carnet de vacunacion y desparasitacion", TITULO);
            titulo.setSpacingAfter(2);
            doc.add(titulo);
            doc.add(new Paragraph("Documento " + numero + " - emitido el " + ZonaClinica.hoy().format(FECHA), NORMAL));

            doc.add(seccion("Paciente"));
            PdfPTable datos = new PdfPTable(new float[] {1, 1, 1, 1});
            datos.setWidthPercentage(100);
            dato(datos, "Nombre", p.getNombre());
            dato(datos, "Codigo", p.getCodigo());
            dato(datos, "Especie", p.getEspecie().getNombre());
            dato(datos, "Raza", p.getRaza() == null ? "-" : p.getRaza().getNombre());
            dato(datos, "Sexo", String.valueOf(p.getSexo()));
            dato(datos, "Nacimiento", p.getFechaNacimiento() == null ? "-" : p.getFechaNacimiento().format(FECHA));
            dato(datos, "Color", p.getColor() == null ? "-" : p.getColor());
            dato(datos, "Microchip", p.getMicrochip() == null ? "-" : p.getMicrochip());
            doc.add(datos);

            doc.add(seccion("Tutor"));
            Tutor t = p.getTutores().stream().sorted((a, b) -> Boolean.compare(b.isEsPrincipal(), a.isEsPrincipal()))
                    .map(PacienteTutor::getTutor).findFirst().orElse(null);
            PdfPTable tutor = new PdfPTable(new float[] {2, 1, 1, 2});
            tutor.setWidthPercentage(100);
            dato(tutor, "Nombre", t == null ? "-" : (t.getNombres() + " " + (t.getApellidos() == null ? "" : t.getApellidos())).trim());
            dato(tutor, "Documento", t == null ? "-" : t.getTipoDocumento() + " " + t.getNumeroDocumento());
            dato(tutor, "Telefono", t == null || t.getTelefono() == null ? "-" : t.getTelefono());
            dato(tutor, "Correo", t == null || t.getEmail() == null ? "-" : t.getEmail());
            doc.add(tutor);

            doc.add(seccion("Vacunas"));
            doc.add(tabla(vacunas, true));
            doc.add(seccion("Desparasitaciones"));
            doc.add(tabla(despar, false));

            String vet = java.util.stream.Stream.concat(vacunas.stream(), despar.stream())
                    .filter(i -> i.veterinario() != null).reduce((a, b) -> b).map(i -> i.veterinario().nombre())
                    .orElse(UsuarioActual.username().orElse(""));
            Paragraph firma = new Paragraph("\n\n______________________________________\nFirma y sello del medico veterinario"
                    + (vet.isEmpty() ? "" : "\n" + vet), NORMAL);
            firma.setAlignment(Element.ALIGN_RIGHT);
            doc.add(firma);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del carnet", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private static Paragraph seccion(String texto) {
        Paragraph s = new Paragraph(texto, SUBTITULO);
        s.setSpacingBefore(8);
        s.setSpacingAfter(3);
        return s;
    }

    private static void dato(PdfPTable tabla, String etiqueta, String valor) {
        Phrase f = new Phrase();
        f.add(new com.lowagie.text.Chunk(etiqueta + ": ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        f.add(new com.lowagie.text.Chunk(valor, NORMAL));
        PdfPCell c = new PdfPCell(f);
        c.setBorder(0);
        c.setPaddingBottom(3);
        tabla.addCell(c);
    }

    private static PdfPTable tabla(List<InmunizacionResponse> filas, boolean vacuna) {
        String[] cab = vacuna
                ? new String[] {"Fecha", "Vacuna", "Dosis", "Lote", "Vence lote", "Proxima dosis", "Veterinario"}
                : new String[] {"Fecha", "Producto", "Tipo", "Lote", "Vence lote", "Proxima aplicacion", "Veterinario"};
        PdfPTable t = new PdfPTable(new float[] {1.1f, 3f, 1.3f, 1.4f, 1.2f, 1.4f, 2f});
        t.setWidthPercentage(100);
        for (String c : cab) {
            PdfPCell h = new PdfPCell(new Phrase(c, CABECERA));
            h.setBackgroundColor(new Color(0x2E, 0x7D, 0x32));
            t.addCell(h);
        }
        if (filas.isEmpty()) {
            PdfPCell vacio = new PdfPCell(new Phrase("Sin registros", CELDA));
            vacio.setColspan(cab.length);
            t.addCell(vacio);
        }
        for (InmunizacionResponse i : filas) {
            t.addCell(celda(i.fechaAplicacion().format(FECHA)));
            t.addCell(celda(i.producto()));
            t.addCell(celda(vacuna ? (i.dosisNombre() != null ? i.dosisNombre()
                    : i.dosisNumero() == null ? "-" : "Dosis " + i.dosisNumero()) : i.clasificacion()));
            t.addCell(celda(i.lote()));
            t.addCell(celda(i.fechaVencimientoLote() == null ? null : i.fechaVencimientoLote().format(FECHA)));
            t.addCell(celda(i.proximaFecha() == null ? "-" : i.proximaFecha().format(FECHA)));
            t.addCell(celda(i.veterinario() == null ? null : i.veterinario().nombre()));
        }
        return t;
    }

    private static PdfPCell celda(String texto) {
        return new PdfPCell(new Phrase(texto == null ? "-" : texto, CELDA));
    }

    private static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
