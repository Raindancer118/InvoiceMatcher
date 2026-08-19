package de.pegasus.invoicematcher.extraction;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.text.DocumentText;
import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.support.Belege;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionServiceTest {

    private final ExtractionService service = ExtractionService.standard();

    @Nested
    @DisplayName("Layouterhaltende PDF-Textextraktion")
    class LayoutText {

        private final PdfBoxTextSource source = new PdfBoxTextSource();

        @Test
        void keepsHorizontalGapsSoColumnsStaySeparable() {
            DocumentText text = DocumentText.of(source.extractText(Belege.bytes("01_PDF_Klaus_Furniere.pdf")));

            assertThat(text.findFirstLine(line -> line.matches("Firma\\s{3,}Steuernr\\..*"))).isPresent();
            assertThat(text.lines()).anySatisfy(line -> assertThat(line).matches("Rechnung\\s{3,}Rechnungsdatum.*"));
        }

        @Test
        void reportsUnreadableInputInsteadOfReturningEmptyText() {
            assertThatThrownBy(() -> source.extractText("kein pdf".getBytes()))
                    .isInstanceOf(DocumentExtractionException.class);
        }
    }

    @Nested
    @DisplayName("Formatweiche")
    class FormatDispatch {

        @Test
        void readsStandaloneXRechnung() {
            ExtractedInvoice invoice = service.extract(Belege.document("13_XRechnung_Spanexempel_gemischt.xml"));

            assertThat(invoice.sourceFormat()).isEqualTo(SourceFormat.XRECHNUNG);
            assertThat(invoice.invoiceNumber()).isEqualTo("7547");
            assertThat(invoice.lines()).hasSize(3);
        }

        @Test
        void prefersEmbeddedXmlOverTextForZugferdPdf() {
            ExtractedInvoice invoice = service.extract(Belege.document("07_ZUGFeRD_Kreditortestrium_Furniere.pdf"));

            assertThat(invoice.sourceFormat()).isEqualTo(SourceFormat.ZUGFERD);
            assertThat(invoice.seller().name()).isEqualTo("Kreditortestrium GmbH");
            assertThat(invoice.totals().gross()).isEqualByComparingTo("399.34");
        }

        @Test
        void fallsBackToTextExtractionForPlainPdf() {
            ExtractedInvoice invoice = service.extract(Belege.document("01_PDF_Klaus_Furniere.pdf"));

            assertThat(invoice.sourceFormat()).isEqualTo(SourceFormat.PDF);
            assertThat(invoice.documentTitle()).isEqualTo("Rechnung");
            assertThat(invoice.invoiceNumber()).isEqualTo("5007");
            assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 4));
            assertThat(invoice.seller().name()).isEqualTo("Friedrich Klausmuster");
            assertThat(invoice.seller().vatId()).isEqualTo("DE 404131066");
            assertThat(invoice.buyer().name()).isEqualTo("Muster zu Testzwecken GmbH");
            assertThat(invoice.totals().net()).isEqualByComparingTo("404.00");
            assertThat(invoice.totals().tax()).isEqualByComparingTo("76.76");
            assertThat(invoice.totals().gross()).isEqualByComparingTo("480.76");
            assertThat(invoice.lines()).hasSize(4);
        }

        @Test
        void readsBuyerOfAnOutgoingInvoiceFromTheAddressBlock() {
            ExtractedInvoice invoice = service.extract(Belege.document("16_AR_Laub.pdf"));

            assertThat(invoice.seller().name()).isEqualTo("Muster zu Testzwecken GmbH");
            assertThat(invoice.buyer().name()).isEqualTo("Erwin Laubangermuster");
        }

        @Test
        void rejectsUnsupportedFileTypes() {
            assertThatThrownBy(() -> service.extract(new SourceDocument("notiz.txt", "Hallo".getBytes())))
                    .isInstanceOf(DocumentExtractionException.class)
                    .hasMessageContaining("notiz.txt");
        }
    }
}
