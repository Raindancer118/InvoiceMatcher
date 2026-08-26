package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
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
 */
@EnabledIf("germanRecognizerInstalled")
class ScannedBelegOcrTest {

    private static final int SCAN_DPI = 300;

    static boolean germanRecognizerInstalled() {
        return recognizer().isAvailable();
    }

    private static TesseractCliRecognizer recognizer() {
        return new TesseractCliRecognizer("tesseract", "deu", System.getenv("TESSDATA_PREFIX"),
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

    @Test
    void kontiertEinenGescanntenBelegWieDasOriginal() {
        ClassificationReport report = Kontierung.classificationServiceWith(scanningExtraction())
                .classify(scanOf("01_PDF_Klaus_Furniere.pdf"));

        assertThat(report.result().documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(report.result().belegdatum()).isEqualTo("04012026");
        assertThat(report.result().belegfeld1()).isEqualTo("5007");
        assertThat(report.result().personenkonto()).isEqualTo(70101);

        // Kontiert wird ueber den Positionstext. Er muss den Scan so lesbar ueberstehen, dass
        // dieselben Konten herauskommen wie aus dem Original - sonst faende der Beleg nur das
        // Sammelkonto, und die Zeichenerkennung waere ihren Aufwand nicht wert. Verglichen wird
        // ohne den Buchungstext: ein verlesener Buchstabe darin aendert keine Buchung.
        ClassificationReport original = Kontierung.standardClassificationService()
                .classify(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        assertThat(report.result().buchungszeilen())
                .isNotEmpty()
                .zipSatisfy(original.result().buchungszeilen(), (scan, erwartet) -> {
                    assertThat(scan.sachkonto()).isEqualTo(erwartet.sachkonto());
                    assertThat(scan.sollHabenKennzeichen()).isEqualTo(erwartet.sollHabenKennzeichen());
                    assertThat(scan.steuerschluessel()).isEqualTo(erwartet.steuerschluessel());
                    assertThat(scan.umsatz()).isEqualByComparingTo(erwartet.umsatz());
                });
    }

    @Test
    void schicktEinenGescanntenBelegTrotzSauberemErgebnisZurPruefung() {
        ClassificationReport report = Kontierung.classificationServiceWith(scanningExtraction())
                .classify(scanOf("01_PDF_Klaus_Furniere.pdf"));

        // Erkannter Text kann sich verlesen haben - das darf niemand ungeprueft buchen.
        assertThat(report.result().konfidenzScore()).isLessThan(new java.math.BigDecimal("0.85"));
        assertThat(new ReviewProperties(new java.math.BigDecimal("0.85"), null)
                .needsReview(report.result())).isTrue();
    }
}
