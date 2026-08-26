package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.extraction.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Rastert die Seiten eines PDF, damit eine Zeichenerkennung darauf arbeiten kann.
 *
 * <p>Graustufen statt Farbe: Tesseract binarisiert ohnehin, und ein Drittel des Speichers
 * genuegt fuer dasselbe Ergebnis. Die Seitenzahl ist begrenzt, weil ein irrtuemlich
 * eingereichter Katalog sonst minutenlang Rechenzeit bindet - die buchungsrelevanten Angaben
 * eines Belegs stehen auf den ersten Seiten.
 */
public class PdfPageRenderer {

    private final int dpi;
    private final int maxPages;

    public PdfPageRenderer(int dpi, int maxPages) {
        this.dpi = dpi;
        this.maxPages = maxPages;
    }

    public List<BufferedImage> render(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            List<BufferedImage> images = new ArrayList<>(pages);
            for (int page = 0; page < pages; page++) {
                images.add(renderer.renderImageWithDPI(page, dpi, ImageType.GRAY));
            }
            return images;
        } catch (Exception e) {
            throw new DocumentExtractionException("PDF konnte nicht gerastert werden: " + e.getMessage(), e);
        }
    }
}
