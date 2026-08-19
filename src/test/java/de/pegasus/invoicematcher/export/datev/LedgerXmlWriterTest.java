package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.DatevSchema;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerXmlWriterTest {

    private final ClassificationService classification = Kontierung.standardClassificationService();
    private final LedgerXmlWriter writer = Kontierung.standardLedgerXmlWriter();

    private String ledgerFor(String beleg) {
        ClassificationReport report = classification.classify(Belege.document(beleg));
        return writer.write(report);
    }

    @Test
    void writesSchemaValidIncomingInvoiceLedger() {
        String xml = ledgerFor("01_PDF_Klaus_Furniere.pdf");

        DatevSchema.assertValid(DatevSchema.Kind.LEDGER, xml);
        assertThat(xml).contains("<accountsPayableLedger>")
                .doesNotContain("accountsReceivableLedger");
    }

    @Test
    void mapsTheInternalFormatOntoTheDatevFieldNames() {
        String xml = ledgerFor("01_PDF_Klaus_Furniere.pdf");

        assertThat(xml)
                .contains("<date>2026-01-04</date>")          // belegdatum
                .contains("<invoiceId>5007</invoiceId>")      // belegfeld_1
                .contains("<bpAccountNo>70101</bpAccountNo>") // personenkonto
                .contains("<accountNo>3401</accountNo>")      // sachkonto als Gegenkonto
                .contains("<buCode>9</buCode>")               // steuerschluessel
                .contains("<currencyCode>EUR</currencyCode>")
                .contains("<supplierName>Friedrich Klausmuster</supplierName>");
    }

    @Test
    void amountsAreGrossBecauseDatevExpectsTheInvoiceAmount() {
        String xml = ledgerFor("01_PDF_Klaus_Furniere.pdf");

        // 399,00 netto zu 19 % ergeben 474,81; 5,00 netto ergeben 5,95 - zusammen der Bruttobetrag 480,76
        assertThat(xml).contains("<amount>474.81</amount>").contains("<amount>5.95</amount>");
        assertThat(xml).contains("consolidatedAmount=\"480.76\"");
    }

    @Test
    void theConsolidatedAmountAlwaysEqualsTheSumOfTheLines() {
        for (String beleg : java.util.List.of("01_PDF_Klaus_Furniere.pdf", "13_XRechnung_Spanexempel_gemischt.xml",
                "04_PDF_Farben_Lack.pdf", "31_konstruiert_7_Prozent_USt.pdf")) {
            String xml = ledgerFor(beleg);
            BigDecimal consolidated = new BigDecimal(
                    xml.replaceAll("(?s).*consolidatedAmount=\"([^\"]+)\".*", "$1"));
            BigDecimal sum = java.util.regex.Pattern.compile("<amount>([^<]+)</amount>").matcher(xml).results()
                    .map(match -> new BigDecimal(match.group(1)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(sum).as(beleg).isEqualByComparingTo(consolidated);
        }
    }

    @Test
    void writesTheDeclaredTaxRatePerLine() {
        assertThat(ledgerFor("31_konstruiert_7_Prozent_USt.pdf"))
                .contains("<tax>7.00</tax>").contains("<buCode>8</buCode>");
    }

    @Test
    void writesSchemaValidOutgoingInvoiceLedgerWithCustomerFields() {
        String xml = ledgerFor("16_AR_Laub.pdf");

        DatevSchema.assertValid(DatevSchema.Kind.LEDGER, xml);
        assertThat(xml).contains("<accountsReceivableLedger>")
                .contains("<bpAccountNo>10101</bpAccountNo>")
                .contains("<accountNo>8400</accountNo>")
                .contains("<customerName>Erwin Laubangermuster</customerName>")
                .doesNotContain("<buCode>"); // Automatikkonto steuert den Schluessel selbst
    }

    @Test
    void carriesTheUnknownPartnerNoteIntoTheInformationField() {
        assertThat(ledgerFor("20_PDF_Ton_unbekannterPartner.pdf"))
                .contains("<information>Kreditor unbekannt – Stammdaten prüfen: Dietrich Tonexempel</information>");
    }

    @Test
    void writesTheVatIdWithoutTheSpacesTheDocumentPrints() {
        assertThat(ledgerFor("01_PDF_Klaus_Furniere.pdf")).contains("<vatId>DE404131066</vatId>");
    }

    @Test
    void leavesTheVatIdEmptyRatherThanExceedingTheFieldLength() {
        assertThat(LedgerXmlWriter.normalizeVatId("DE 4041310661234567")).isNull();
        assertThat(LedgerXmlWriter.normalizeVatId("  ")).isNull();
        assertThat(LedgerXmlWriter.normalizeVatId(null)).isNull();
    }

    @Test
    void escapesSpecialCharactersInFreeTextFields() {
        assertThat(ledgerFor("09_ZUGFeRD_Unkastester_Furniere.pdf"))
                .doesNotContain("& ")
                .satisfies(xml -> DatevSchema.assertValid(DatevSchema.Kind.LEDGER, xml));
    }
}
