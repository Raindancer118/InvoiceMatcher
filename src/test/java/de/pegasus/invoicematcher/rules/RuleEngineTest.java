package de.pegasus.invoicematcher.rules;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final RuleEngine engine = Kontierung.standardRuleEngine();

    private static ExtractedInvoice.Builder eingangsrechnung() {
        return ExtractedInvoice.builder(SourceFormat.PDF)
                .documentTitle("Rechnung")
                .invoiceNumber("5007")
                .invoiceDate(LocalDate.of(2026, 1, 4))
                .seller(new Party("Friedrich Klausmuster", "DE 404131066", "156/290/44989"))
                .buyer(new Party("Muster zu Testzwecken GmbH", null, null))
                .totals(new MonetaryTotals(new BigDecimal("404.00"), new BigDecimal("76.76"), new BigDecimal("480.76")))
                .lines(List.of(line("Echtholzfurnierkanten Ahorn 24 mm", "0.77", "38.50", "19"),
                        line("Furnier Ahorn 80 cm", "31.50", "315.00", "19"),
                        line("Arbeitsplatte Eiche 60 cm", "50.50", "50.50", "19")));
    }

    private static InvoiceLine line(String description, String unitPrice, String net, String vatRate) {
        return new InvoiceLine(description, BigDecimal.ONE, new BigDecimal(unitPrice),
                new BigDecimal(net), new BigDecimal(vatRate));
    }

    private static List<String> ruleIdsOf(ClassificationContext context) {
        return context.findings().stream().map(Finding::ruleId).toList();
    }

    @Nested
    @DisplayName("Regel 1.1 - Belegart")
    class Title {

        @Test
        void acceptsInvoices() {
            assertThat(engine.evaluate(eingangsrechnung().build()).documentType())
                    .isEqualTo(DocumentType.RECHNUNGSEINGANG);
        }

        @Test
        void acceptsCreditNotesExactlyLikeInvoices() {
            assertThat(engine.evaluate(eingangsrechnung().documentTitle("Gutschrift").build()).documentType())
                    .isEqualTo(DocumentType.RECHNUNGSEINGANG);
        }

        @Test
        void classifiesEverythingElseAsSonstigeAndStopsImmediately() {
            ClassificationContext context = engine.evaluate(
                    eingangsrechnung().documentTitle("Lieferschein Nr. 16849").build());

            assertThat(context.documentType()).isEqualTo(DocumentType.SONSTIGE);
            assertThat(context.bookingLines()).isEmpty();
            assertThat(context.personenkonto()).isNull();
            assertThat(ruleIdsOf(context)).containsExactly("1.1");
        }

        @Test
        void aMissingTitleIsAlsoSonstige() {
            assertThat(engine.evaluate(eingangsrechnung().documentTitle(null).build()).documentType())
                    .isEqualTo(DocumentType.SONSTIGE);
        }

        @Test
        void doesNotAcceptTheWordRechnungInsideACompoundLikeRechnungsdatum() {
            assertThat(engine.evaluate(eingangsrechnung().documentTitle("Grundabgabenbescheid 2026").build())
                    .documentType()).isEqualTo(DocumentType.SONSTIGE);
        }
    }

    @Nested
    @DisplayName("Belegrichtung")
    class DirectionDetection {

        @Test
        void aDocumentAddressedToUsIsAnIncomingInvoice() {
            assertThat(engine.evaluate(eingangsrechnung().build()).direction()).isEqualTo(Direction.EINGANG);
        }

        @Test
        void aDocumentIssuedByUsIsAnOutgoingInvoice() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .seller(new Party("Muster zu Testzwecken GmbH", "DE 815000010", "24112345678"))
                    .buyer(new Party("Erwin Laubangermuster", null, null))
                    .lines(List.of(line("Spüle 70cm", "63.11", "315.55", "19")))
                    .totals(new MonetaryTotals(new BigDecimal("315.55"), new BigDecimal("59.95"), new BigDecimal("375.50")))
                    .build());

            assertThat(context.direction()).isEqualTo(Direction.AUSGANG);
            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSAUSGANG);
            assertThat(context.personenkonto()).isEqualTo(10101);
        }
    }

    @Nested
    @DisplayName("Regel 1.2 - Pflichtangaben")
    class MandatoryFields {

        @Test
        void missingInvoiceNumberSendsTheDocumentToReview() {
            ClassificationContext context = engine.evaluate(eingangsrechnung().invoiceNumber(null).build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
            assertThat(context.bookingLines()).isEmpty();
            assertThat(ruleIdsOf(context)).contains("1.2");
        }

        @Test
        void headerDataStaysAvailableForTheReviewerEvenWithoutBookingLines() {
            ClassificationContext context = engine.evaluate(eingangsrechnung().invoiceDate(null).build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
            assertThat(context.belegfeld1()).isEqualTo("5007");
            assertThat(context.personenkonto()).isEqualTo(70101);
        }

        @Test
        void eitherVatIdOrTaxNumberIsEnough() {
            assertThat(engine.evaluate(eingangsrechnung()
                    .seller(new Party("Friedrich Klausmuster", null, "156/290/44989")).build()).documentType())
                    .isEqualTo(DocumentType.RECHNUNGSEINGANG);
        }

        @Test
        void missingBothTaxIdentifiersSendsTheDocumentToReview() {
            assertThat(engine.evaluate(eingangsrechnung()
                    .seller(new Party("Jochen Redloffbeispiel", null, null)).build()).documentType())
                    .isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        }

        @Test
        void missingNetOrTaxAmountSendsTheDocumentToReview() {
            assertThat(engine.evaluate(eingangsrechnung()
                    .totals(new MonetaryTotals(null, null, new BigDecimal("1070.99"))).build()).documentType())
                    .isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
            assertThat(engine.evaluate(eingangsrechnung()
                    .totals(new MonetaryTotals(new BigDecimal("1036.87"), null, new BigDecimal("1036.87"))).build())
                    .documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        }
    }

    @Nested
    @DisplayName("Regel 1.3 - Rechenprobe")
    class Arithmetic {

        @Test
        void acceptsDifferencesWithinOneCent() {
            assertThat(engine.evaluate(eingangsrechnung()
                    .totals(new MonetaryTotals(new BigDecimal("404.00"), new BigDecimal("76.76"), new BigDecimal("480.77")))
                    .build()).documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        }

        @Test
        void rejectsLargerDeviations() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .totals(new MonetaryTotals(new BigDecimal("404.00"), new BigDecimal("76.76"), new BigDecimal("500.00")))
                    .build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
            assertThat(ruleIdsOf(context)).contains("1.3");
        }
    }

    @Nested
    @DisplayName("Regel 1.4 - GWG-Grenze")
    class FixedAssets {

        @Test
        void anEquipmentPositionAboveEightHundredEuroGoesToReviewWithoutGuessingAnAccount() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .lines(List.of(line("Digitalkopiergerät HS-1233", "899.99", "899.99", "19")))
                    .totals(new MonetaryTotals(new BigDecimal("899.99"), new BigDecimal("171.00"), new BigDecimal("1070.99")))
                    .build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
            assertThat(context.bookingLines()).isEmpty();
            assertThat(ruleIdsOf(context)).contains("1.4");
        }

        @Test
        void theSameCheckDoesNotApplyToOutgoingInvoices() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .seller(new Party("Muster zu Testzwecken GmbH", "DE 815000010", null))
                    .buyer(new Party("Erwin Laubangermuster", null, null))
                    .lines(List.of(line("Werkbank massiv", "1200.00", "1200.00", "19")))
                    .totals(new MonetaryTotals(new BigDecimal("1200.00"), new BigDecimal("228.00"), new BigDecimal("1428.00")))
                    .build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSAUSGANG);
        }
    }

    @Nested
    @DisplayName("Punkt 5 - Personenkonto")
    class Personenkonto {

        @Test
        void takesTheCreditorFromTheMasterData() {
            ClassificationContext context = engine.evaluate(eingangsrechnung().build());

            assertThat(context.personenkonto()).isEqualTo(70101);
            assertThat(context.buchungstextKopf()).isNull();
        }

        @Test
        void anUnknownPartnerGetsADiverseAccountAndAHeaderNoteButIsNotAnError() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .seller(new Party("Dietrich Tonexempel", "DE 801763659", "207/116/11496")).build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
            assertThat(context.personenkonto()).isEqualTo(70004);
            assertThat(context.buchungstextKopf())
                    .isEqualTo("Kreditor unbekannt – Stammdaten prüfen: Dietrich Tonexempel");
            assertThat(context.bookingLines()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Punkt 7 - Buchungszeilen")
    class BookingLines {

        @Test
        void mergesPositionsSharingAccountAndTaxRate() {
            ClassificationContext context = engine.evaluate(eingangsrechnung().build());

            assertThat(context.bookingLines()).hasSize(2);
            assertThat(context.bookingLines()).anySatisfy(booking -> {
                assertThat(booking.sachkonto()).isEqualTo(3401);
                assertThat(booking.umsatz()).isEqualByComparingTo("353.50");
                assertThat(booking.sollHabenKennzeichen()).isEqualTo("S");
                assertThat(booking.steuerschluessel()).isEqualTo(9);
            });
            assertThat(context.bookingLines()).anySatisfy(booking -> {
                assertThat(booking.sachkonto()).isEqualTo(3403);
                assertThat(booking.umsatz()).isEqualByComparingTo("50.50");
            });
        }

        @Test
        void keepsPositionsApartWhenTheTaxRateDiffers() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .lines(List.of(line("Furnier Ahorn 80 cm", "100.00", "100.00", "19"),
                            line("Furnier Eiche 80 cm", "100.00", "100.00", "7")))
                    .totals(new MonetaryTotals(new BigDecimal("200.00"), new BigDecimal("26.00"), new BigDecimal("226.00")))
                    .build());

            assertThat(context.bookingLines()).hasSize(2)
                    .extracting(booking -> booking.steuerschluessel()).containsExactlyInAnyOrder(9, 8);
        }

        @Test
        void outgoingInvoicesAreSplitByTaxRateOnly() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .seller(new Party("Muster zu Testzwecken GmbH", "DE 815000010", null))
                    .buyer(new Party("Erwin Laubangermuster", null, null))
                    .lines(List.of(line("Spüle 70cm", "63.11", "215.55", "19"),
                            line("Armatur chrom", "50.00", "100.00", "19")))
                    .totals(new MonetaryTotals(new BigDecimal("315.55"), new BigDecimal("59.95"), new BigDecimal("375.50")))
                    .build());

            assertThat(context.bookingLines()).singleElement().satisfies(booking -> {
                assertThat(booking.sachkonto()).isEqualTo(8400);
                assertThat(booking.sollHabenKennzeichen()).isEqualTo("H");
                assertThat(booking.steuerschluessel()).isNull();
                assertThat(booking.umsatz()).isEqualByComparingTo("315.55");
            });
        }

        @Test
        void fallsBackToTheDeclaredNetTotalWhenNoPositionsCouldBeRead() {
            ClassificationContext context = engine.evaluate(eingangsrechnung().lines(List.of()).build());

            assertThat(context.bookingLines()).singleElement()
                    .satisfies(booking -> assertThat(booking.umsatz()).isEqualByComparingTo("404.00"));
            assertThat(ruleIdsOf(context)).contains("3.1");
        }

        @Test
        void reportsWhenThePositionsDoNotAddUpToTheDeclaredNetTotal() {
            ClassificationContext context = engine.evaluate(eingangsrechnung()
                    .lines(List.of(line("Furnier Ahorn 80 cm", "10.00", "10.00", "19"))).build());

            assertThat(context.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
            assertThat(ruleIdsOf(context)).contains("3.2");
        }
    }
}
