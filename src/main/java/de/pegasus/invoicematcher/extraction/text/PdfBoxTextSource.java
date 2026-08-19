package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.extraction.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/** Layouterhaltende Textextraktion auf Basis von Apache PDFBox. */
@Component
public class PdfBoxTextSource implements PdfTextSource {

    @Override
    public String extractText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new LayoutTextStripper().getText(document);
        } catch (Exception e) {
            throw new DocumentExtractionException("PDF konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }
}
