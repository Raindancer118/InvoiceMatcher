package de.pegasus.invoicematcher.classification;

import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der fachliche Abnahmetest: die 31 Testbelege gegen das Baseline-Regelwerk.
 *
 * <p>Deckt jede Regel mindestens einmal ab - 15 regulaere Eingangsrechnungen ueber drei Formate,
 * zwei Ausgangsrechnungen, zwei echte "Sonstige", einen unbekannten Partner sowie die
 * konstruierten Grenzfaelle.
 */
class BelegAkzeptanzTest {

    private final ClassificationService service = Kontierung.standardClassificationService();

    private ClassificationResult classify(String file) {
        return service.classify(Belege.document(file)).result();
    }

    @Test
    @DisplayName("Jeder mitgelieferte Beleg wird ohne Ausnahme verarbeitet")
    void processesEveryTestDocument() {
        assertThat(Belege.all()).hasSize(31).allSatisfy(file ->
                assertThat(classify(file).documentType()).isNotNull());
    }

    @Nested
    @DisplayName("Belegtyp je Testfall")
    class DocumentTypes {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "01_PDF_Klaus_Furniere.pdf,                          RECHNUNGSEINGANG",
                "02_PDF_Saege_Platte.pdf,                            RECHNUNGSEINGANG",
                "03_PDF_Redloff_Plattenwerkstoff.pdf,                RECHNUNGSEINGANG",
                "04_PDF_Farben_Lack.pdf,                             RECHNUNGSEINGANG",
                "05_PDF_Buerobedarf_Kopierer.pdf,                    RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "06_PDF_Tankstelle_Diesel.pdf,                       RECHNUNGSEINGANG",
                "07_ZUGFeRD_Kreditortestrium_Furniere.pdf,           RECHNUNGSEINGANG",
                "08_ZUGFeRD_Saegewerk_Testtanne.pdf,                 RECHNUNGSEINGANG",
                "09_ZUGFeRD_Unkastester_Furniere.pdf,                RECHNUNGSEINGANG",
                "10_ZUGFeRD_Farben_Lack.pdf,                         RECHNUNGSEINGANG",
                "11_ZUGFeRD_Redloff_Platte.pdf,                      RECHNUNGSEINGANG",
                "12_ZUGFeRD_Schmitt_Platte.pdf,                      RECHNUNGSEINGANG",
                "13_XRechnung_Spanexempel_gemischt.xml,              RECHNUNGSEINGANG",
                "14_XRechnung_Saegewerk_Testtanne.xml,               RECHNUNGSEINGANG",
                "15_XRechnung_Farben_Lack.xml,                       RECHNUNGSEINGANG",
                "16_AR_Laub.pdf,                                     RECHNUNGSAUSGANG",
                "17_AR_Beispiel.pdf,                                 RECHNUNGSAUSGANG",
                "18_Sonstige_Mietvertrag.pdf,                        SONSTIGE",
                "19_Sonstige_Grundabgabenbescheid.pdf,               SONSTIGE",
                "20_PDF_Ton_unbekannterPartner.pdf,                  RECHNUNGSEINGANG",
                "21_konstruiert_kein_Titel_Auftragsbestaetigung.pdf, SONSTIGE",
                "22_konstruiert_kein_Titel_Lieferschein.pdf,         SONSTIGE",
                "23_konstruiert_kein_Titel_Angebot.pdf,              SONSTIGE",
                "24_konstruiert_fehlende_Rechnungsnr.pdf,            RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "25_konstruiert_fehlendes_Datum.pdf,                 RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "26_konstruiert_fehlende_UStIdNr.pdf,                RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "27_konstruiert_fehlender_Nettobetrag.pdf,           RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "28_konstruiert_fehlender_Steuerbetrag.pdf,          RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "29_konstruiert_Rechenfehler.pdf,                    RECHNUNGSEINGANG_ZUR_PRUEFUNG",
                "30_konstruiert_Gutschrift_positiv.pdf,              RECHNUNGSEINGANG",
                "31_konstruiert_7_Prozent_USt.pdf,                   RECHNUNGSEINGANG"})
        void matchesTheBaselineRuleSet(String file, DocumentType expected) {
            assertThat(classify(file).documentType()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Personenkonto je Testfall")
    class Personenkonten {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "01_PDF_Klaus_Furniere.pdf,                70101",
                "02_PDF_Saege_Platte.pdf,                  70102",
                "03_PDF_Redloff_Plattenwerkstoff.pdf,      70103",
                "04_PDF_Farben_Lack.pdf,                   70104",
                "06_PDF_Tankstelle_Diesel.pdf,             70106",
                "08_ZUGFeRD_Saegewerk_Testtanne.pdf,       70107",
                "09_ZUGFeRD_Unkastester_Furniere.pdf,      70108",
                "14_XRechnung_Saegewerk_Testtanne.xml,     70107",
                "16_AR_Laub.pdf,                           10101",
                "17_AR_Beispiel.pdf,                       10102"})
        void takesKnownPartnersFromTheMasterData(String file, int expected) {
            assertThat(classify(file).personenkonto()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} -> Diverse {1}")
        @CsvSource({
                "07_ZUGFeRD_Kreditortestrium_Furniere.pdf, 70011",
                "12_ZUGFeRD_Schmitt_Platte.pdf,            70019",
                "13_XRechnung_Spanexempel_gemischt.xml,    70008",
                "20_PDF_Ton_unbekannterPartner.pdf,        70004"})
        void routesUnknownPartnersToTheDiverseAccountOfTheirInitial(String file, int expected) {
            ClassificationResult result = classify(file);

            assertThat(result.personenkonto()).isEqualTo(expected);
            assertThat(result.buchungstextKopf()).startsWith("Kreditor unbekannt – Stammdaten prüfen: ");
            assertThat(result.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        }
    }

    @Nested
    @DisplayName("Buchungszeilen")
    class Buchungszeilen {

        @Test
        void categorisesByPositionTextEvenWhenTheSupplierNameSuggestsOtherwise() {
            // Beleg 02, 08 und 14: Lieferant "Saegewerk", Position aber Plattenware
            assertThat(sachkontenOf("02_PDF_Saege_Platte.pdf")).containsExactly(3403);
            assertThat(sachkontenOf("08_ZUGFeRD_Saegewerk_Testtanne.pdf")).containsExactly(3403);
            assertThat(sachkontenOf("14_XRechnung_Saegewerk_Testtanne.xml")).containsExactly(3403);
        }

        @Test
        void splitsAMixedInvoiceIntoOneLinePerAccount() {
            List<BookingLine> lines = classify("13_XRechnung_Spanexempel_gemischt.xml").buchungszeilen();

            assertThat(lines).hasSize(2);
            assertThat(lines).anySatisfy(line -> {
                assertThat(line.sachkonto()).isEqualTo(3403);
                assertThat(line.umsatz()).isEqualByComparingTo("82.05");
            });
            assertThat(lines).anySatisfy(line -> {
                assertThat(line.sachkonto()).isEqualTo(3402);
                assertThat(line.umsatz()).isEqualByComparingTo("456.67");
            });
        }

        @Test
        void mergesPositionsOfTheSameCategoryIntoASingleLine() {
            List<BookingLine> lines = classify("04_PDF_Farben_Lack.pdf").buchungszeilen();

            assertThat(lines).singleElement().satisfies(line -> {
                assertThat(line.sachkonto()).isEqualTo(3404);
                assertThat(line.umsatz()).isEqualByComparingTo("1036.87");
                assertThat(line.steuerschluessel()).isEqualTo(9);
            });
        }

        @Test
        void bookingLinesAddUpToTheDeclaredNetTotal() {
            java.math.BigDecimal summe = classify("01_PDF_Klaus_Furniere.pdf").buchungszeilen().stream()
                    .map(BookingLine::umsatz)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            assertThat(summe).isEqualByComparingTo("404.00");
        }

        @Test
        void outgoingInvoicesBookToTheRevenueAccountOnTheCreditSide() {
            assertThat(classify("16_AR_Laub.pdf").buchungszeilen()).singleElement().satisfies(line -> {
                assertThat(line.sachkonto()).isEqualTo(8400);
                assertThat(line.sollHabenKennzeichen()).isEqualTo("H");
                assertThat(line.steuerschluessel()).isNull();
                assertThat(line.umsatz()).isEqualByComparingTo("315.55");
            });
        }

        @Test
        void reducedTaxRatePositionsCarryTaxKeyEight() {
            assertThat(classify("31_konstruiert_7_Prozent_USt.pdf").buchungszeilen())
                    .singleElement().satisfies(line -> {
                        assertThat(line.sachkonto()).isEqualTo(4940);
                        assertThat(line.steuerschluessel()).isEqualTo(8);
                    });
        }

        @Test
        void aCreditNoteIsBookedExactlyLikeAnInvoice() {
            ClassificationResult result = classify("30_konstruiert_Gutschrift_positiv.pdf");

            assertThat(result.belegfeld1()).isEqualTo("1104-G");
            assertThat(result.buchungszeilen()).singleElement()
                    .satisfies(line -> assertThat(line.sachkonto()).isEqualTo(3404));
        }

        private List<Integer> sachkontenOf(String file) {
            return classify(file).buchungszeilen().stream().map(BookingLine::sachkonto).toList();
        }
    }

    @Nested
    @DisplayName("Belege ohne Buchungsdaten")
    class WithoutBookingData {

        @ParameterizedTest
        @MethodSource("de.pegasus.invoicematcher.classification.BelegAkzeptanzTest#sonstigeBelege")
        void sonstigeCarryNoBookingDataAtAll(String file) {
            ClassificationResult result = classify(file);

            assertThat(result.documentType()).isEqualTo(DocumentType.SONSTIGE);
            assertThat(result.buchungszeilen()).isEmpty();
            assertThat(result.belegdatum()).isNull();
            assertThat(result.belegfeld1()).isNull();
            assertThat(result.personenkonto()).isNull();
        }

        @ParameterizedTest
        @MethodSource("de.pegasus.invoicematcher.classification.BelegAkzeptanzTest#pruefBelege")
        void documentsUnderReviewKeepTheirHeaderButLoseTheirBookingLines(String file) {
            ClassificationResult result = classify(file);

            assertThat(result.documentType().isReview()).isTrue();
            assertThat(result.buchungszeilen()).isEmpty();
            assertThat(result.personenkonto()).isNotNull();
            assertThat(result.konfidenzScore()).isLessThanOrEqualTo(new java.math.BigDecimal("0.50"));
        }
    }

    @Nested
    @DisplayName("Belegdatum im DATEV-Format")
    class Belegdatum {

        @Test
        void isFormattedAsDayMonthYearWithoutSeparators() {
            assertThat(classify("01_PDF_Klaus_Furniere.pdf").belegdatum()).isEqualTo("04012026");
            assertThat(classify("07_ZUGFeRD_Kreditortestrium_Furniere.pdf").belegdatum()).isEqualTo("12052026");
            assertThat(classify("13_XRechnung_Spanexempel_gemischt.xml").belegdatum()).isEqualTo("06052026");
        }
    }

    @Nested
    @DisplayName("Konfidenz")
    class Konfidenz {

        @Test
        void structuredInvoicesFromKnownPartnersScoreHighest() {
            assertThat(classify("08_ZUGFeRD_Saegewerk_Testtanne.pdf").konfidenzScore())
                    .isEqualByComparingTo("1.00");
            assertThat(classify("15_XRechnung_Farben_Lack.xml").konfidenzScore())
                    .isEqualByComparingTo("1.00");
        }

        @Test
        void anUnknownPartnerLowersTheScoreWithoutBlockingTheBooking() {
            assertThat(classify("20_PDF_Ton_unbekannterPartner.pdf").konfidenzScore())
                    .isLessThan(new java.math.BigDecimal("0.85"));
        }
    }

    static List<String> sonstigeBelege() {
        return List.of("18_Sonstige_Mietvertrag.pdf", "19_Sonstige_Grundabgabenbescheid.pdf",
                "21_konstruiert_kein_Titel_Auftragsbestaetigung.pdf",
                "22_konstruiert_kein_Titel_Lieferschein.pdf",
                "23_konstruiert_kein_Titel_Angebot.pdf");
    }

    static List<String> pruefBelege() {
        return List.of("05_PDF_Buerobedarf_Kopierer.pdf", "24_konstruiert_fehlende_Rechnungsnr.pdf",
                "25_konstruiert_fehlendes_Datum.pdf", "26_konstruiert_fehlende_UStIdNr.pdf",
                "27_konstruiert_fehlender_Nettobetrag.pdf", "28_konstruiert_fehlender_Steuerbetrag.pdf",
                "29_konstruiert_Rechenfehler.pdf");
    }
}
