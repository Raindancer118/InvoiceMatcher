package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.export.InMemoryDocumentStore;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceTest {

    private final List<ReviewCase> notified = new ArrayList<>();
    private ReviewService service;

    @BeforeEach
    void setUp() {
        ClassificationService classification = Kontierung.standardClassificationService();
        ReviewProperties properties = new ReviewProperties(new BigDecimal("0.85"), null);
        service = new ReviewService(classification, new InMemoryReviewRepository(), properties, notified::add,
                new InMemoryDocumentStore());
    }

    @Test
    void aCleanStructuredInvoiceIsApprovedAutomatically() {
        ReviewCase reviewCase = service.submit(Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf"));

        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.AUTOMATISCH_FREIGEGEBEN);
        assertThat(service.openCases()).isEmpty();
        assertThat(notified).isEmpty();
    }

    @Test
    void aDocumentFlaggedByTheRulesWaitsForAHuman() {
        ReviewCase reviewCase = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        assertThat(reviewCase.report().result().documentType())
                .isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.OFFEN);
        assertThat(service.openCases()).containsExactly(reviewCase);
        assertThat(notified).containsExactly(reviewCase);
    }

    @Test
    void aLowConfidenceResultAlsoWaitsEvenThoughNoRuleWasViolated() {
        ReviewCase reviewCase = service.submit(Belege.document("20_PDF_Ton_unbekannterPartner.pdf"));

        assertThat(reviewCase.report().result().documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(reviewCase.report().result().konfidenzScore()).isLessThan(new BigDecimal("0.85"));
        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.OFFEN);
    }

    @Test
    void approvingClosesTheCaseAndRecordsWhoDidIt() {
        ReviewCase open = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        ReviewCase approved = service.approve(open.id(), "buchhaltung@volantic.de", null);

        assertThat(approved.status()).isEqualTo(ReviewStatus.FREIGEGEBEN);
        assertThat(approved.decidedBy()).isEqualTo("buchhaltung@volantic.de");
        assertThat(approved.decidedAt()).isNotNull();
        assertThat(service.openCases()).isEmpty();
    }

    @Test
    void approvingCanReplaceTheProposedResultWithACorrectedOne() {
        ReviewCase open = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
        List<BookingLine> nachkontiert = List.of(new BookingLine(4930, BookingLine.SOLL, 9,
                new BigDecimal("899.99"), "Digitalkopiergeraet HS-1233"));

        ReviewCase approved = service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.RECHNUNGSEINGANG, nachkontiert));

        assertThat(approved.report().result().documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(approved.report().result().buchungszeilen()).isEqualTo(nachkontiert);
    }

    @Test
    void aBookableDocumentTypeWithoutBookingLinesIsRefusedInsteadOfSilentlyStayingUnbooked() {
        ReviewCase open = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        assertThatThrownBy(() -> service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.RECHNUNGSEINGANG, null)))
                .isInstanceOf(ReviewStateException.class)
                .hasMessageContaining("keine Buchungszeilen");
    }

    @Test
    void aDocumentTypeWithoutBookingDataMayKeepEmptyBookingLines() {
        ReviewCase open = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        ReviewCase approved = service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.SONSTIGE, null));

        assertThat(approved.report().result().documentType()).isEqualTo(DocumentType.SONSTIGE);
    }

    @Test
    void rejectingKeepsTheReasonForTheAuditTrail() {
        ReviewCase open = service.submit(Belege.document("29_konstruiert_Rechenfehler.pdf"));

        ReviewCase rejected = service.reject(open.id(), "tom", "Beleg beim Lieferanten neu anfordern");

        assertThat(rejected.status()).isEqualTo(ReviewStatus.ABGELEHNT);
        assertThat(rejected.decisionNote()).isEqualTo("Beleg beim Lieferanten neu anfordern");
    }

    @Test
    void aCaseCanOnlyBeDecidedOnce() {
        ReviewCase open = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
        service.approve(open.id(), "tom", null);

        assertThatThrownBy(() -> service.reject(open.id(), "tom", "zu spät"))
                .isInstanceOf(ReviewStateException.class)
                .hasMessageContaining("bereits entschieden");
    }

    @Test
    void anUnknownCaseIsReportedAsSuch() {
        assertThatThrownBy(() -> service.approve("gibt-es-nicht", "tom", null))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
