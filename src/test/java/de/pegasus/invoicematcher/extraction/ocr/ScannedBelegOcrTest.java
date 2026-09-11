package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Scans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Gegenprobe mit dem echten Werkzeug: ein Testbeleg wird zu einem Scan gemacht - Bild ohne
 * Textebene - und muss danach dieselben Kopfdaten liefern wie das Original.
 *
 * <p>Der Test laeuft nur, wenn Tesseract samt deutschen Sprachdaten installiert ist. Der Bau
 * im Container und die Pipeline bringen beides mit; auf einem Entwicklungsrechner ohne
 * Zeichenerkennung ist die Stufe abgeschaltet, und dann gibt es hier nichts zu pruefen.
 *
 * <p>Frueher pruefte diese Klasse zusaetzlich, dass ein Scan zur selben Kontierung fuehrt wie
 * das Original - das war eine Eigenschaft des inzwischen entfernten deterministischen
 * Regelwerks (Stichwort-Matching auf dem Positionstext). Die KI-Klassifikation trifft diese
 * Entscheidung jetzt selbst; ob sie bei leicht abweichendem OCR-Text zum selben Ergebnis kommt,
 * ist keine deterministisch pruefbare Eigenschaft mehr. Geblieben ist die Extraktion: sie muss
 * weiterhin dieselben Kopfdaten liefern, unabhaengig davon, wer/was sie anschliessend verwendet.
 */
@EnabledIf("germanRecognizerInstalled")
class ScannedBelegOcrTest {

    private static final int SCAN_DPI = 300;

    /**
     * Dieselbe Sprachwahl wie im Betrieb. Mit {@code deu} allein verliest das Systemmodell die
     * fett gesetzte Ueberschrift eines Belegs ("Rechnung" wurde zu "RGCh\u201cU\"Q"), und der
     * Beleg landete als "Sonstige" statt als Rechnungseingang. Die zweite Sprache ist hier
     * keine Zugabe, sondern haelt die Titelerkennung stabil.
     */
    private static final String SPRACHEN = "deu+eng";

    static boolean germanRecognizerInstalled() {
        // isAvailable() prueft beide Sprachen einzeln - fehlt eine, laeuft der Test nicht an.
        return recognizer().isAvailable();
    }

    private static TesseractCliRecognizer recognizer() {
        return new TesseractCliRecognizer("tesseract", SPRACHEN, System.getenv("TESSDATA_PREFIX"),
                3, SCAN_DPI, Duration.ofSeconds(120));
    }

    private static SourceDocument scanOf(String beleg) {
        return new SourceDocument("scan.pdf", Scans.flatten(Belege.bytes(beleg), SCAN_DPI));
    }

    private static ExtractionService scanningExtraction() {
        return ExtractionService.standard(new OcrPdfTextSource(new PdfBoxTextSource(),
                new PdfPageRenderer(SCAN_DPI, 5), recognizer(), 40));
    }

    private ExtractedInvoice extractScanOf(String beleg) {
        return scanningExtraction().extract(scanOf(beleg));
    }

    @Test
    void readsTheHeaderDataOfAScannedIncomingInvoice() {
        ExtractedInvoice invoice = extractScanOf("01_PDF_Klaus_Furniere.pdf");

        assertThat(invoice.sourceFormat()).isEqualTo(SourceFormat.SCAN);
        assertThat(invoice.documentTitle()).isEqualTo("Rechnung");
        assertThat(invoice.invoiceNumber()).isEqualTo("5007");
        assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(invoice.totals().net()).isEqualByComparingTo("404.00");
        assertThat(invoice.totals().tax()).isEqualByComparingTo("76.76");
        assertThat(invoice.totals().gross()).isEqualByComparingTo("480.76");
    }

    @Test
    void keepsTheColumnLayoutSoSellerAndBuyerStaySeparable() {
        ExtractedInvoice invoice = extractScanOf("01_PDF_Klaus_Furniere.pdf");

        assertThat(invoice.seller().name()).isEqualTo("Friedrich Klausmuster");
        assertThat(invoice.buyer().name()).isEqualTo("Muster zu Testzwecken GmbH");
    }

}
