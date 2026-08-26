package de.pegasus.invoicematcher.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Macht aus einem Textbeleg einen gescannten Beleg: jede Seite wird gerastert und als reines
 * Bild in ein neues PDF gelegt.
 *
 * <p>Damit entsteht genau das, was ein Buchhaltungsposteingang taeglich erreicht - ein PDF ohne
 * Textebene -, und zwar aus den echten Testbelegen statt aus einem eigens gebauten Beispiel.
 */
public final class Scans {

    private Scans() {
    }

    public static byte[] flatten(byte[] pdf, int dpi) {
        try (PDDocument source = Loader.loadPDF(pdf);
             PDDocument target = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int index = 0; index < source.getNumberOfPages(); index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.GRAY);
                appendAsImagePage(target, image, source.getPage(index).getMediaBox());
            }
            target.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendAsImagePage(PDDocument target, BufferedImage image, PDRectangle size)
            throws IOException {
        PDPage page = new PDPage(new PDRectangle(size.getWidth(), size.getHeight()));
        target.addPage(page);
        PDImageXObject xObject = LosslessFactory.createFromImage(target, image);
        try (PDPageContentStream content = new PDPageContentStream(target, page)) {
            content.drawImage(xObject, 0, 0, size.getWidth(), size.getHeight());
        }
    }
}
