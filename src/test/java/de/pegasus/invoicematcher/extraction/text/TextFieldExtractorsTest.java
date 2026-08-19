package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kalibriert an den 30 Testbelegen: die Snippets entsprechen wortgetreu der
 * layout-erhaltenden Textextraktion der jeweiligen Originaldatei.
 */
class TextFieldExtractorsTest {

    /** Beleg 01 - klassisches Briefkopf-Layout mit rechter Spalte. */
    private static final DocumentText BELEG_01 = DocumentText.of("""
                       Friedrich Klausmuster

            Friedrich Klausmuster, Am Muster 80, 87587 Kaufbeuren

            Firma                                          Steuernr.: 156/290/44989
            Muster zu Testzwecken GmbH                     USt-IdNr.: DE 404131066
            Musterhobelweg 5
            90451 Nürnberg

            Rechnung                                   Rechnungsdatum:          04.01.2026
                                                       Rechnungs-Nr.:                 5007

            Pos. Art.-Nr.     Artikelbezeichnung        Menge   Einheit Einzelpreis  Ust    Gesamt

              1    42-200 Echtholzfurnierkanten Ahorn 24 mm    50    Meter    0,77 €   19%    38,50 €
              3    70-105 Furnier Ahorn 80 cm                  10    Meter   31,50 €   19%   315,00 €

            Nettosumme                                                                      404,00 €
            19% Mehrwertsteuer                                                    76,76 €
            Gesamtsumme                                                                     480,76
            """);

    /** Beleg 19 - traegt Rechnungsdatum/-nummer, ist aber ein Bescheid. */
    private static final DocumentText BELEG_19 = DocumentText.of("""
            Stadt Musterhausen, Postfach 4578, 99999 Musterhausen

            Firma
            Muster zu Testzwecken GmbH                     Kassenzeichen: 01-023457123489-1-1

                                                           Rechnungsdatum:      02.01.2026
                                                           Rechnungsnummer:        2026001
            Grundabgabenbescheid 2026
            """);

    /** Beleg 30 - konstruierte Gutschrift im schlanken Layout. */
    private static final DocumentText BELEG_30 = DocumentText.of("""
            Farben-Fritzebeispiel
            Dammbeispielweg 9, 90409 Nuernberg
            Steuernr.: 238/815/50725
            USt-IdNr.: DE 799875869

            Muster zu Testzwecken GmbH
            Musterhobelweg 5

            Gutschrift
            Rechnungsdatum: 20.01.2026
            Rechnungs-Nr.: 1104-G

            Pos. Art.-Nr. Artikelbezeichnung                  Menge Einheit Einzelpreis USt Gesamt
            1   H-10200   Holzschutzlasur, Palisander (Retoure)   10   Liter   22,45 EUR   19% 224,50 EUR

            Nettosumme: 224,50 EUR
            Mehrwertsteuer 19%: 42,66 EUR
            Bruttobetrag: 267,16 EUR

            Gutschrift fuer Rechnung Nr. 1104
            """);

    @Nested
    @DisplayName("Titelerkennung (Basis fuer Regel 1.1)")
    class Titles {

        private final DocumentTitleExtractor extractor = new DocumentTitleExtractor();

        @Test
        void findsTitleInLeftColumnOfAMultiColumnLine() {
            assertThat(extractor.extract(BELEG_01)).contains("Rechnung");
        }

        @Test
        void doesNotMistakeFieldLabelsForATitle() {
            assertThat(extractor.extract(BELEG_19)).contains("Grundabgabenbescheid 2026");
        }

        @Test
        void takesTheFirstTitleSoTheClosingReferenceIsIgnored() {
            assertThat(extractor.extract(BELEG_30)).contains("Gutschrift");
        }

        @Test
        void recognisesNonInvoiceDocumentKinds() {
            assertThat(extractor.extract(DocumentText.of("Mietvertrag\n\nZwischen\n"))).contains("Mietvertrag");
            assertThat(extractor.extract(DocumentText.of("Lieferschein Nr. 16849\n"))).contains("Lieferschein Nr. 16849");
            assertThat(extractor.extract(DocumentText.of("Auftragsbestaetigung Nr. 1104\n")))
                    .contains("Auftragsbestaetigung Nr. 1104");
        }

        @Test
        void yieldsNothingWhenNoKnownDocumentKindAppears() {
            assertThat(extractor.extract(DocumentText.of("Guten Tag\nwir melden uns.\n"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Kopfdaten")
    class HeaderFields {

        @Test
        void readsInvoiceNumberFromTheRightColumn() {
            assertThat(new InvoiceNumberExtractor().extract(BELEG_01)).contains("5007");
            assertThat(new InvoiceNumberExtractor().extract(BELEG_19)).contains("2026001");
            assertThat(new InvoiceNumberExtractor().extract(BELEG_30)).contains("1104-G");
        }

        @Test
        void readsInvoiceDate() {
            assertThat(new InvoiceDateExtractor().extract(BELEG_01)).contains(LocalDate.of(2026, 1, 4));
            assertThat(new InvoiceDateExtractor().extract(BELEG_30)).contains(LocalDate.of(2026, 1, 20));
        }

        @Test
        void ignoresDeliveryDateWhenNoInvoiceDateIsPresent() {
            DocumentText lieferschein = DocumentText.of("Lieferschein Nr. 16849\nLieferdatum: 11.01.2026\n");

            assertThat(new InvoiceDateExtractor().extract(lieferschein)).isEmpty();
        }

        @Test
        void readsSellerFromTheSenderLineIncludingTaxIdentifiers() {
            Party seller = new SellerExtractor().extract(BELEG_01).orElseThrow();

            assertThat(seller.name()).isEqualTo("Friedrich Klausmuster");
            assertThat(seller.vatId()).isEqualTo("DE 404131066");
            assertThat(seller.taxNumber()).isEqualTo("156/290/44989");
        }

        @Test
        void fallsBackToTheFirstLineWhenThereIsNoCombinedSenderLine() {
            Party seller = new SellerExtractor().extract(BELEG_30).orElseThrow();

            assertThat(seller.name()).isEqualTo("Farben-Fritzebeispiel");
            assertThat(seller.vatId()).isEqualTo("DE 799875869");
        }

        @Test
        void reportsMissingTaxIdentifiersAsNull() {
            DocumentText ohneIds = DocumentText.of("""
                    Jochen Redloffbeispiel
                    Am Musterweg 17, 96364 Marktrodach
                    IBAN: DE64300104001234567890
                    """);

            Party seller = new SellerExtractor().extract(ohneIds).orElseThrow();

            assertThat(seller.vatId()).isNull();
            assertThat(seller.taxNumber()).isNull();
            assertThat(seller.hasTaxIdentifier()).isFalse();
        }

        @Test
        void readsBuyerFromTheAddressLabelBlock() {
            assertThat(new BuyerExtractor().extract(BELEG_01))
                    .map(Party::name).contains("Muster zu Testzwecken GmbH");
            assertThat(new BuyerExtractor().extract(DocumentText.of("""
                    Muster zu Testzwecken GmbH, Musterhobelweg 5, 90451 Nürnberg

                    Herrn
                    Erwin Laubangermuster                     Steuernr.: 24112345678
                    """))).map(Party::name).contains("Erwin Laubangermuster");
        }
    }

    @Nested
    @DisplayName("Summen (Basis fuer Regel 1.2 und 1.3)")
    class Totals {

        private final TotalsExtractor extractor = new TotalsExtractor();

        @Test
        void readsNetTaxAndGrossFromLabelledLines() {
            MonetaryTotals totals = extractor.extract(BELEG_01).orElseThrow();

            assertThat(totals.net()).isEqualByComparingTo("404.00");
            assertThat(totals.tax()).isEqualByComparingTo("76.76");
            assertThat(totals.gross()).isEqualByComparingTo("480.76");
        }

        @Test
        void handlesLeadingCurrencySymbolAndAlternativeLabels() {
            MonetaryTotals totals = extractor.extract(DocumentText.of("""
                            Netto-Rechnungsbetrag                                    € 899,99
                            zzgl. Mehrwertsteuer (19 %)                              € 171,00
                            Brutto-Rechnungsbetrag                                 € 1.070,99
                    """)).orElseThrow();

            assertThat(totals.net()).isEqualByComparingTo("899.99");
            assertThat(totals.tax()).isEqualByComparingTo("171.00");
            assertThat(totals.gross()).isEqualByComparingTo("1070.99");
        }

        @Test
        void leavesMissingTaxAmountNullInsteadOfDefaultingToZero() {
            MonetaryTotals totals = extractor.extract(DocumentText.of("""
                    Nettosumme: 1.036,87 EUR
                    Bruttobetrag: 1.036,87 EUR
                    """)).orElseThrow();

            assertThat(totals.net()).isEqualByComparingTo("1036.87");
            assertThat(totals.tax()).isNull();
            assertThat(totals.gross()).isEqualByComparingTo("1036.87");
            assertThat(totals.isComplete()).isFalse();
        }

        @Test
        void ignoresCashDiscountLinesBelowTheGrandTotal() {
            MonetaryTotals totals = extractor.extract(DocumentText.of("""
                    Nettosumme                            315,55 €
                    19% USt                                59,95 €
                    Gesamtsumme EUR                       375,50 €
                    Skonto 2%                              11,27 €
                    """)).orElseThrow();

            assertThat(totals.gross()).isEqualByComparingTo("375.50");
        }

        @Test
        void doesNotTreatTheVatIdLineAsATaxAmount() {
            MonetaryTotals totals = extractor.extract(DocumentText.of("""
                    USt-IdNr.: DE 404131066
                    Gesamtsumme: 1.070,99 EUR
                    """)).orElseThrow();

            assertThat(totals.tax()).isNull();
            assertThat(totals.net()).isNull();
            assertThat(totals.gross()).isEqualByComparingTo("1070.99");
        }
    }

    @Nested
    @DisplayName("Positionen")
    class LineItems {

        private final LineItemExtractor extractor = new LineItemExtractor();

        @Test
        void readsWideColumnLayout() {
            List<InvoiceLine> lines = extractor.extractAll(BELEG_01);

            assertThat(lines).hasSize(2);
            InvoiceLine first = lines.getFirst();
            assertThat(first.description()).isEqualTo("Echtholzfurnierkanten Ahorn 24 mm");
            assertThat(first.quantity()).isEqualByComparingTo("50");
            assertThat(first.unitNetPrice()).isEqualByComparingTo("0.77");
            assertThat(first.netAmount()).isEqualByComparingTo("38.50");
            assertThat(first.vatRate()).isEqualByComparingTo("19");
        }

        @Test
        void readsNarrowLayoutWithEuroWordAndCommaInDescription() {
            List<InvoiceLine> lines = extractor.extractAll(BELEG_30);

            assertThat(lines).singleElement().satisfies(line -> {
                assertThat(line.description()).isEqualTo("Holzschutzlasur, Palisander (Retoure)");
                assertThat(line.netAmount()).isEqualByComparingTo("224.50");
                assertThat(line.vatRate()).isEqualByComparingTo("19");
            });
        }

        @Test
        void readsFractionalVatRates() {
            List<InvoiceLine> lines = extractor.extractAll(DocumentText.of(
                    "  1    AE-60 Arbeitsplatte Eiche 60 cm, beschichtet     7      Meter       99,00 €   19,00%    693,00 €"));

            assertThat(lines).singleElement().extracting(InvoiceLine::vatRate)
                    .isEqualTo(new BigDecimal("19.00"));
        }

        @Test
        void skipsRowsWithoutPricesSuchAsDeliveryNotes() {
            List<InvoiceLine> lines = extractor.extractAll(DocumentText.of(
                    "1   AP280-E     beschichtete Platte 280x205 cm, Esche      6    m2      -       -     -"));

            assertThat(lines).isEmpty();
        }

        @Test
        void skipsTheTableHeader() {
            assertThat(extractor.extractAll(DocumentText.of(
                    "Pos. Art.-Nr. Artikelbezeichnung                  Menge Einheit Einzelpreis USt Gesamt")))
                    .isEmpty();
        }
    }
}
