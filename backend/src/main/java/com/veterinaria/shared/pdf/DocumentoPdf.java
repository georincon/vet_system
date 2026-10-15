package com.veterinaria.shared.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.veterinaria.shared.clinico.PacienteBasico;
import com.veterinaria.shared.time.ZonaClinica;

/**
 * Fabrica de documentos PDF de la clinica (OpenPDF), reutilizable por todos los documentos clinicos
 * (informe de laboratorio, imagenologia, patologia, reporte quirurgico, formula, carnet...).
 *
 * <p>Uso: {@code documentoPdf.nuevo("Informe de laboratorio", "ORD-...").paciente(p, fecha).seccion("...").tabla(...)
 * .firma(nombre, detalle).construir()}. Incluye membrete con el nombre de la clinica ({@code vet.clinica.nombre}),
 * encabezado de paciente/tutor, tablas con celdas coloreadas, imagenes (JPG/PNG), firma y pie con paginacion.
 */
@Component
public class DocumentoPdf {

    static final Color VERDE = new Color(0x1B, 0x5E, 0x20);
    static final Color VERDE_CLARO = new Color(0x2E, 0x7D, 0x32);
    static final Color GRIS = new Color(0x61, 0x61, 0x61);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font F_CLINICA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, VERDE);
    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font F_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, VERDE);
    private static final Font F_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font F_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font F_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
    private static final Font F_NOTA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, GRIS);

    private final String clinica;

    public DocumentoPdf(@Value("${vet.clinica.nombre:Clinica Veterinaria}") String clinica) {
        this.clinica = clinica;
    }

    /** Celda de tabla con color de texto y fondo opcionales. */
    public record Celda(String texto, Color color, Color fondo, boolean negrita) {

        public static Celda de(String texto) {
            return new Celda(texto, null, null, false);
        }

        public Celda color(Color c) {
            return new Celda(texto, c, fondo, negrita);
        }

        public Celda fondo(Color f) {
            return new Celda(texto, color, f, negrita);
        }

        public Celda enNegrita() {
            return new Celda(texto, color, fondo, true);
        }
    }

    /** Par etiqueta/valor para bloques de datos. */
    public record Campo(String etiqueta, String valor) {
    }

    /** Imagen (JPG/PNG) con leyenda para galerias. */
    public record Imagen(byte[] contenido, String leyenda) {
    }

    /** Documento nuevo con membrete; {@code codigo} aparece en el encabezado y el pie. */
    public Builder nuevo(String titulo, String codigo) {
        return new Builder(titulo, codigo);
    }

    public final class Builder {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Document doc = new Document(PageSize.A4, 36, 36, 36, 48);
        private final String titulo;

        private Builder(String titulo, String codigo) {
            this.titulo = titulo;
            try {
                PdfWriter writer = PdfWriter.getInstance(doc, out);
                writer.setPageEvent(new Pie(clinica, codigo));
                doc.addTitle(titulo);
                doc.addAuthor(clinica);
                doc.addCreator(clinica);
                doc.open();
                membrete(codigo);
            } catch (DocumentException e) {
                throw new IllegalStateException("No se pudo iniciar el PDF", e);
            }
        }

        private void membrete(String codigo) throws DocumentException {
            PdfPTable t = new PdfPTable(new float[] {3, 2});
            t.setWidthPercentage(100);
            PdfPCell izq = new PdfPCell(new Phrase(clinica, F_CLINICA));
            izq.setBorder(Rectangle.NO_BORDER);
            izq.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(izq);
            Paragraph der = new Paragraph();
            der.add(new Chunk(titulo + "\n", F_TITULO));
            der.add(new Chunk((codigo == null ? "" : "No. " + codigo + "\n") + "Emitido: "
                    + ZonaClinica.local(java.time.Instant.now()).format(FECHA_HORA), F_NORMAL));
            der.setAlignment(Element.ALIGN_RIGHT);
            PdfPCell d = new PdfPCell(der);
            d.setBorder(Rectangle.NO_BORDER);
            d.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(d);
            t.setSpacingAfter(2);
            doc.add(t);
            PdfPTable linea = new PdfPTable(1);
            linea.setWidthPercentage(100);
            PdfPCell c = new PdfPCell(new Phrase(" ", F_NOTA));
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColorBottom(VERDE);
            c.setBorderWidthBottom(1.5f);
            c.setFixedHeight(4);
            linea.addCell(c);
            linea.setSpacingAfter(6);
            doc.add(linea);
        }

        /** Bloques "Paciente" y "Tutor" con los datos de identificacion. */
        public Builder paciente(PacienteBasico p, LocalDate fechaReferencia) {
            seccion("Paciente");
            campos(List.of(
                    new Campo("Nombre", p.nombre()), new Campo("Codigo", p.codigo()),
                    new Campo("Especie", p.especie()), new Campo("Raza", p.raza()),
                    new Campo("Sexo", "M".equals(p.sexo()) ? "Macho" : "F".equals(p.sexo()) ? "Hembra" : p.sexo()),
                    new Campo("Edad", p.edadTexto(fechaReferencia)),
                    new Campo("Nacimiento", p.fechaNacimiento() == null ? null : p.fechaNacimiento().format(FECHA)),
                    new Campo("Microchip", p.microchip())), 4);
            seccion("Tutor");
            campos(List.of(
                    new Campo("Nombre", p.tutorNombre()), new Campo("Documento", p.tutorDocumento()),
                    new Campo("Telefono", p.tutorTelefono()), new Campo("Correo", p.tutorEmail())), 2);
            return this;
        }

        public Builder seccion(String texto) {
            Paragraph s = new Paragraph(texto, F_SECCION);
            s.setSpacingBefore(8);
            s.setSpacingAfter(3);
            add(s);
            return this;
        }

        public Builder parrafo(String texto) {
            if (texto != null && !texto.isBlank()) {
                Paragraph p = new Paragraph(texto, F_NORMAL);
                p.setSpacingAfter(3);
                add(p);
            }
            return this;
        }

        /** Parrafo con etiqueta en negrita; se omite si el contenido esta vacio. */
        public Builder texto(String etiqueta, String contenido) {
            if (contenido != null && !contenido.isBlank()) {
                Paragraph p = new Paragraph();
                p.add(new Chunk(etiqueta + ": ", F_NEGRITA));
                p.add(new Chunk(contenido, F_NORMAL));
                p.setSpacingAfter(3);
                add(p);
            }
            return this;
        }

        public Builder nota(String texto) {
            if (texto != null && !texto.isBlank()) {
                add(new Paragraph(texto, F_NOTA));
            }
            return this;
        }

        /** Rejilla de pares etiqueta/valor en {@code columnas} columnas; los valores vacios se muestran como "-". */
        public Builder campos(List<Campo> campos, int columnas) {
            PdfPTable t = new PdfPTable(columnas);
            t.setWidthPercentage(100);
            for (Campo c : campos) {
                Phrase f = new Phrase();
                f.add(new Chunk(c.etiqueta() + ": ", F_NEGRITA));
                f.add(new Chunk(c.valor() == null || c.valor().isBlank() ? "-" : c.valor(), F_NORMAL));
                PdfPCell cell = new PdfPCell(f);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(3);
                t.addCell(cell);
            }
            for (int i = campos.size() % columnas; i != 0 && i < columnas; i++) {
                PdfPCell vacia = new PdfPCell(new Phrase(" "));
                vacia.setBorder(Rectangle.NO_BORDER);
                t.addCell(vacia);
            }
            add(t);
            return this;
        }

        /** Tabla con cabecera verde; {@code anchos} son proporciones relativas. */
        public Builder tabla(List<String> cabeceras, float[] anchos, List<List<Celda>> filas) {
            PdfPTable t = new PdfPTable(anchos);
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String c : cabeceras) {
                PdfPCell h = new PdfPCell(new Phrase(c, F_CABECERA));
                h.setBackgroundColor(VERDE_CLARO);
                h.setPadding(4);
                t.addCell(h);
            }
            if (filas.isEmpty()) {
                PdfPCell vacio = new PdfPCell(new Phrase("Sin registros", F_NORMAL));
                vacio.setColspan(cabeceras.size());
                t.addCell(vacio);
            }
            for (List<Celda> fila : filas) {
                for (Celda c : fila) {
                    Font base = c.negrita() ? F_NEGRITA : F_NORMAL;
                    Font f = c.color() == null ? base : new Font(base.getFamily(), base.getSize(), base.getStyle(), c.color());
                    PdfPCell cell = new PdfPCell(new Phrase(c.texto() == null || c.texto().isBlank() ? "-" : c.texto(), f));
                    cell.setPadding(3);
                    if (c.fondo() != null) {
                        cell.setBackgroundColor(c.fondo());
                    }
                    t.addCell(cell);
                }
            }
            t.setSpacingAfter(4);
            add(t);
            return this;
        }

        /** Galeria de imagenes (3 por fila) con leyenda; las ilegibles se sustituyen por un aviso. */
        public Builder imagenes(List<Imagen> imagenes) {
            if (imagenes.isEmpty()) {
                return this;
            }
            PdfPTable t = new PdfPTable(3);
            t.setWidthPercentage(100);
            for (Imagen im : imagenes) {
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.BOX);
                cell.setPadding(4);
                try {
                    Image img = Image.getInstance(im.contenido());
                    img.scaleToFit(155, 130);
                    img.setAlignment(Element.ALIGN_CENTER);
                    cell.addElement(img);
                } catch (Exception e) {
                    cell.addElement(new Paragraph("(imagen no disponible)", F_NOTA));
                }
                Paragraph leyenda = new Paragraph(im.leyenda() == null ? "" : im.leyenda(), F_NOTA);
                leyenda.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(leyenda);
                t.addCell(cell);
            }
            for (int i = imagenes.size() % 3; i != 0 && i < 3; i++) {
                PdfPCell vacia = new PdfPCell(new Phrase(" "));
                vacia.setBorder(Rectangle.NO_BORDER);
                t.addCell(vacia);
            }
            add(t);
            return this;
        }

        /** Linea de firma con nombre y detalle (cargo / tarjeta profesional). */
        public Builder firma(String nombre, String detalle) {
            Paragraph p = new Paragraph("\n\n______________________________________\n"
                    + (nombre == null ? "Firma del medico veterinario" : nombre)
                    + (detalle == null || detalle.isBlank() ? "" : "\n" + detalle), F_NORMAL);
            p.setAlignment(Element.ALIGN_RIGHT);
            p.setKeepTogether(true);
            add(p);
            return this;
        }

        public byte[] construir() {
            if (doc.isOpen()) {
                doc.close();
            }
            return out.toByteArray();
        }

        private void add(Element e) {
            try {
                doc.add(e);
            } catch (DocumentException ex) {
                throw new IllegalStateException("No se pudo generar el PDF", ex);
            }
        }
    }

    /** Pie de pagina con clinica, codigo y "Pagina x de y". */
    private static final class Pie extends PdfPageEventHelper {

        private final String clinica;
        private final String codigo;
        private PdfTemplate total;
        private BaseFont base;

        Pie(String clinica, String codigo) {
            this.clinica = clinica;
            this.codigo = codigo;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String izq = clinica + (codigo == null ? "" : " - " + codigo);
            String pag = "Pagina " + writer.getPageNumber() + " de ";
            float y = document.bottom() - 18;
            cb.saveState();
            cb.setColorFill(GRIS);
            cb.beginText();
            cb.setFontAndSize(base, 7.5f);
            cb.setTextMatrix(document.left(), y);
            cb.showText(izq);
            cb.endText();
            float ancho = base.getWidthPoint(pag, 7.5f);
            float x = document.right() - ancho - 14;
            cb.beginText();
            cb.setFontAndSize(base, 7.5f);
            cb.setTextMatrix(x, y);
            cb.showText(pag);
            cb.endText();
            cb.addTemplate(total, x + ancho, y);
            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, 7.5f);
            total.setColorFill(GRIS);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
