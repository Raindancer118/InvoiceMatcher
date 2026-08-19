package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.support.DatevSchema;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentXmlWriterTest {

    private final DocumentXmlWriter writer = Kontierung.standardDocumentXmlWriter();

    private static DocumentEntry mitBuchungsdaten() {
        return new DocumentEntry("01_PDF_Klaus_Furniere.pdf", "Rechnungsdaten_5007.xml",
                DocumentEntry.LedgerKind.EINGANG, LocalDate.of(2026, 1, 4));
    }

    private static DocumentEntry nurBelegbild() {
        return new DocumentEntry("18_Sonstige_Mietvertrag.pdf", null, null, null);
    }

    @Test
    void writesASchemaValidArchive() {
        String xml = writer.write(List.of(mitBuchungsdaten(), nurBelegbild()));

        DatevSchema.assertValid(DatevSchema.Kind.DOCUMENT, xml);
        assertThat(xml).contains("xmlns=\"http://xml.datev.de/bedi/tps/document/v06.0\"")
                .contains("version=\"6.0\"")
                .contains("generatingSystem=\"InvoiceMatcher\"");
    }

    @Test
    void linksBookingDataAndDocumentImageForABookableDocument() {
        String xml = writer.write(List.of(mitBuchungsdaten()));

        assertThat(xml)
                .contains("xsi:type=\"accountsPayableLedger\" datafile=\"Rechnungsdaten_5007.xml\"")
                .contains("<property value=\"2026-01\" key=\"1\"></property>")
                .contains("<property value=\"Eingangsrechnungen\" key=\"3\"></property>")
                .contains("xsi:type=\"File\" name=\"01_PDF_Klaus_Furniere.pdf\"");
    }

    @Test
    void aDocumentWithoutBookingDataIsFiledAsImageOnly() {
        String xml = writer.write(List.of(nurBelegbild()));

        DatevSchema.assertValid(DatevSchema.Kind.DOCUMENT, xml);
        assertThat(xml).contains("xsi:type=\"File\" name=\"18_Sonstige_Mietvertrag.pdf\"")
                .doesNotContain("Ledger");
    }

    @Test
    void usesTheOutgoingLedgerTypeForOutgoingInvoices() {
        String xml = writer.write(List.of(new DocumentEntry("16_AR_Laub.pdf", "Rechnungsdaten_20JJ03046.xml",
                DocumentEntry.LedgerKind.AUSGANG, LocalDate.of(2026, 1, 3))));

        DatevSchema.assertValid(DatevSchema.Kind.DOCUMENT, xml);
        assertThat(xml).contains("xsi:type=\"accountsReceivableLedger\"")
                .contains("<property value=\"Ausgangsrechnungen\" key=\"3\"></property>");
    }
}
