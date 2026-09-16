package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.export.InMemoryDocumentStore;
import de.pegasus.invoicematcher.classification.ai.AiClassificationResponseParser;
import de.pegasus.invoicematcher.classification.ai.ClassificationRationaleService;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.extraction.DocumentExtractionException;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.support.CannedAiResponses;
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
    private final List<Runnable> wartend = new ArrayList<>();
    private ReviewService service;

    @BeforeEach
    void setUp() {
        ClassificationService classification = Kontierung.standardClassificationService();
        ReviewProperties properties = new ReviewProperties(new BigDecimal("0.85"), null);
        // Der Hintergrundlauf sofort im Test-Faden: dieselbe Reihenfolge, aber vorhersagbar.
        service = new ReviewService(classification, new InMemoryReviewRepository(), properties, notified::add,
                new InMemoryDocumentStore(), Runnable::run);
    }

    /** Einreichen und den Vorgang lesen, nachdem die Klassifikation durch ist. */
    private ReviewCase eingereicht(String beleg) {
        return service.find(service.submit(Belege.document(beleg)).id());
    }

    @Test
    void aCleanStructuredInvoiceIsApprovedAutomatically() {
        ReviewCase reviewCase = eingereicht("08_ZUGFeRD_Saegewerk_Testtanne.pdf");

        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.AUTOMATISCH_FREIGEGEBEN);
        assertThat(service.openCases()).isEmpty();
        assertThat(notified).isEmpty();
    }

    @Test
    void aDocumentFlaggedByTheRulesWaitsForAHuman() {
        ReviewCase reviewCase = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");

        assertThat(reviewCase.report().result().documentType())
                .isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.OFFEN);
        assertThat(service.openCases()).containsExactly(reviewCase);
        assertThat(notified).singleElement().extracting(ReviewCase::id).isEqualTo(reviewCase.id());
    }

    @Test
    void aLowConfidenceResultAlsoWaitsEvenThoughNoRuleWasViolated() {
        ReviewCase reviewCase = eingereicht("20_PDF_Ton_unbekannterPartner.pdf");

        assertThat(reviewCase.report().result().documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(reviewCase.report().result().konfidenzScore()).isLessThan(new BigDecimal("0.85"));
        assertThat(reviewCase.status()).isEqualTo(ReviewStatus.OFFEN);
    }

    @Test
    void approvingClosesTheCaseAndRecordsWhoDidIt() {
        ReviewCase open = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");

        ReviewCase approved = service.approve(open.id(), "buchhaltung@volantic.de", null);

        assertThat(approved.status()).isEqualTo(ReviewStatus.FREIGEGEBEN);
        assertThat(approved.decidedBy()).isEqualTo("buchhaltung@volantic.de");
        assertThat(approved.decidedAt()).isNotNull();
        assertThat(service.openCases()).isEmpty();
    }

    @Test
    void approvingCanReplaceTheProposedResultWithACorrectedOne() {
        ReviewCase open = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");
        List<BookingLine> nachkontiert = List.of(new BookingLine(4930, BookingLine.SOLL, 9,
                new BigDecimal("899.99"), "Digitalkopiergeraet HS-1233"));

        ReviewCase approved = service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.RECHNUNGSEINGANG, nachkontiert));

        assertThat(approved.report().result().documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(approved.report().result().buchungszeilen()).isEqualTo(nachkontiert);
    }

    @Test
    void aBookableDocumentTypeWithoutBookingLinesIsRefusedInsteadOfSilentlyStayingUnbooked() {
        ReviewCase open = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");

        assertThatThrownBy(() -> service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.RECHNUNGSEINGANG, null)))
                .isInstanceOf(ReviewStateException.class)
                .hasMessageContaining("keine Buchungszeilen");
    }

    @Test
    void aDocumentTypeWithoutBookingDataMayKeepEmptyBookingLines() {
        ReviewCase open = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");

        ReviewCase approved = service.approve(open.id(), "tom",
                new ReviewDecision.Correction(DocumentType.SONSTIGE, null));

        assertThat(approved.report().result().documentType()).isEqualTo(DocumentType.SONSTIGE);
    }

    @Test
    void rejectingKeepsTheReasonForTheAuditTrail() {
        ReviewCase open = eingereicht("29_konstruiert_Rechenfehler.pdf");

        ReviewCase rejected = service.reject(open.id(), "tom", "Beleg beim Lieferanten neu anfordern");

        assertThat(rejected.status()).isEqualTo(ReviewStatus.ABGELEHNT);
        assertThat(rejected.decisionNote()).isEqualTo("Beleg beim Lieferanten neu anfordern");
    }

    @Test
    void aCaseCanOnlyBeDecidedOnce() {
        ReviewCase open = eingereicht("05_PDF_Buerobedarf_Kopierer.pdf");
        service.approve(open.id(), "tom", null);

        assertThatThrownBy(() -> service.reject(open.id(), "tom", "zu spät"))
                .isInstanceOf(ReviewStateException.class)
                .hasMessageContaining("bereits entschieden");
    }

    /**
     * Wer einreicht, soll nicht auf die KI warten: der Beleg ist mit der Annahme abgelegt und
     * vermerkt, die Klassifikation kommt hinterher.
     */
    @Test
    void takesTheDocumentBeforeTheClassificationEvenStarts() {
        ReviewService verzoegert = new ReviewService(Kontierung.standardClassificationService(),
                new InMemoryReviewRepository(), new ReviewProperties(new BigDecimal("0.85"), null),
                notified::add, new InMemoryDocumentStore(), wartend::add);

        ReviewCase angenommen = verzoegert.submit(Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf"));

        assertThat(angenommen.status()).isEqualTo(ReviewStatus.IN_ARBEIT);
        assertThat(angenommen.report().filename()).isEqualTo("08_ZUGFeRD_Saegewerk_Testtanne.pdf");
        assertThat(verzoegert.document(angenommen.id())).isNotNull();
        assertThat(notified).isEmpty();
        assertThat(wartend).hasSize(1);

        wartend.forEach(Runnable::run);

        assertThat(verzoegert.find(angenommen.id()).status()).isEqualTo(ReviewStatus.AUTOMATISCH_FREIGEGEBEN);
        assertThat(verzoegert.find(angenommen.id()).report().result().buchungszeilen()).isNotEmpty();
    }

    /** Solange die KI noch rechnet, gibt es nichts zu entscheiden - auch nicht versehentlich. */
    @Test
    void aCaseStillBeingClassifiedCannotBeDecided() {
        ReviewService verzoegert = new ReviewService(Kontierung.standardClassificationService(),
                new InMemoryReviewRepository(), new ReviewProperties(new BigDecimal("0.85"), null),
                notified::add, new InMemoryDocumentStore(), wartend::add);
        ReviewCase angenommen = verzoegert.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        assertThatThrownBy(() -> verzoegert.approve(angenommen.id(), "tom", null))
                .isInstanceOf(ReviewStateException.class)
                .hasMessageContaining("wird noch klassifiziert");
    }

    /**
     * Ein unlesbarer Beleg wird sofort abgewiesen, nicht erst im Hintergrund: die Antwort
     * gehoert an das Formular, an dem gerade jemand steht.
     */
    @Test
    void aDocumentThatCannotBeReadIsRefusedRightAtSubmission() {
        // Eine Extraktion ohne einen einzigen Extraktor: derselbe Fehlschlag wie bei einem
        // Beleg, den keiner lesen kann.
        ReviewService kaputt = new ReviewService(
                Kontierung.classificationServiceWith(new ExtractionService(List.of())),
                new InMemoryReviewRepository(), new ReviewProperties(new BigDecimal("0.85"), null),
                notified::add, new InMemoryDocumentStore(), wartend::add);

        assertThatThrownBy(() -> kaputt.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf")))
                .isInstanceOf(DocumentExtractionException.class);
        assertThat(kaputt.allCases()).isEmpty();
        assertThat(wartend).isEmpty();
    }

    /** Scheitert die Kontierung im Hintergrund, bleibt der Beleg als offener Vorgang liegen. */
    @Test
    void aFailingClassificationLeavesAnOpenCaseInsteadOfLosingTheDocument() {
        ClassificationService explodiert = new ClassificationService(ExtractionService.standard(),
                CannedAiResponses.classifier(), new AiClassificationResponseParser(),
                new ClassificationRationaleService(CannedAiResponses.classifier()),
                Kontierung::companyProperties, Kontierung::accountingProperties, Kontierung::partnerProperties) {
            @Override
            public ClassificationReport classify(SourceDocument document, ExtractedInvoice invoice) {
                throw new IllegalStateException("KI-Pfad kaputt");
            }
        };
        ReviewService service = new ReviewService(explodiert, new InMemoryReviewRepository(),
                new ReviewProperties(new BigDecimal("0.85"), null), notified::add,
                new InMemoryDocumentStore(), Runnable::run);

        ReviewCase angenommen = service.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
        ReviewCase danach = service.find(angenommen.id());

        assertThat(danach.status()).isEqualTo(ReviewStatus.OFFEN);
        assertThat(danach.report().findings()).anySatisfy(finding ->
                assertThat(finding.message()).contains("nicht verarbeitet werden"));
        assertThat(service.document(angenommen.id())).isNotNull();
    }

    @Test
    void anUnknownCaseIsReportedAsSuch() {
        assertThatThrownBy(() -> service.approve("gibt-es-nicht", "tom", null))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
