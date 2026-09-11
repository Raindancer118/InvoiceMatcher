package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.extraction.einvoice.EInvoiceXmlParser;
import de.pegasus.invoicematcher.extraction.ocr.FallbackOcrTextRecognizer;
import de.pegasus.invoicematcher.extraction.ocr.GeminiVisionRecognizer;
import de.pegasus.invoicematcher.extraction.ocr.OcrPdfTextSource;
import de.pegasus.invoicematcher.extraction.ocr.OcrTextRecognizer;
import de.pegasus.invoicematcher.extraction.ocr.PdfPageRenderer;
import de.pegasus.invoicematcher.extraction.ocr.TesseractCliRecognizer;
import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.extraction.text.PdfTextSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stellt die rahmenwerksfreien Bausteine der Extraktion als Beans bereit. */
@Configuration
public class ExtractionConfiguration {

    @Bean
    public EInvoiceXmlParser eInvoiceXmlParser() {
        return new EInvoiceXmlParser();
    }

    /**
     * Tesseract bleibt die erste Erkennung; ist der Gemini-Ausweichweg eingeschaltet, legt sich
     * {@link FallbackOcrTextRecognizer} davor und fragt ihn nur bei Seiten ohne erkennbaren
     * Belegtitel an - siehe dessen Klassendoku.
     */
    @Bean
    public OcrTextRecognizer ocrTextRecognizer(OcrProperties ocr) {
        OcrTextRecognizer tesseract = new TesseractCliRecognizer(ocr.command(), ocr.language(), ocr.tessdataDir(),
                ocr.pageSegmentationMode(), ocr.dpi(), ocr.timeout());
        if (!ocr.gemini().enabled()) {
            return tesseract;
        }
        GeminiVisionRecognizer gemini = new GeminiVisionRecognizer(ocr.gemini().apiKey(), ocr.gemini().model(),
                ocr.gemini().timeout());
        return new FallbackOcrTextRecognizer(tesseract, gemini);
    }

    /**
     * Genau eine Textquelle im Kontext: die Zeichenerkennung ist kein zweiter Kandidat neben
     * PDFBox, sondern legt sich davor. Ist sie abgeschaltet, bleibt die reine Textebene uebrig.
     */
    @Bean
    public PdfTextSource pdfTextSource(OcrProperties ocr, OcrTextRecognizer recognizer) {
        PdfBoxTextSource embedded = new PdfBoxTextSource();
        if (!ocr.enabled()) {
            return embedded;
        }
        return new OcrPdfTextSource(embedded, new PdfPageRenderer(ocr.dpi(), ocr.maxPages()),
                recognizer, ocr.minCharactersPerPage());
    }
}
