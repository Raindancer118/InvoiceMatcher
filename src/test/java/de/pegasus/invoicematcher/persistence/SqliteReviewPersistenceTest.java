package de.pegasus.invoicematcher.persistence;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewRepository;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.review.ReviewStatus;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Ablage muss einen Neustart ueberstehen - sonst waeren freigegebene Belege nach jedem
 * Deployment verloren und der Exportvermerk wertlos.
 */
@DisplayName("SQLite-Ablage")
class SqliteReviewPersistenceTest {

    @TempDir
    Path verzeichnis;

    private Kontierung.Persistence persistence() {
        return Kontierung.sqlitePersistence(verzeichnis.resolve("invoicematcher.db"));
    }

    @Test
    void storesAndReloadsACompleteReviewCase() {
        ReviewCase submitted;
        try (Kontierung.Persistence persistence = persistence()) {
            submitted = persistence.review().submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        }

        try (Kontierung.Persistence reopened = persistence()) {
            ReviewCase reloaded = reopened.review().find(submitted.id());

            assertThat(reloaded.id()).isEqualTo(submitted.id());
            assertThat(reloaded.status()).isEqualTo(ReviewStatus.AUTOMATISCH_FREIGEGEBEN);
            assertThat(reloaded.submittedAt())
                    .isCloseTo(submitted.submittedAt(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
            assertThat(reloaded.report().filename()).isEqualTo("01_PDF_Klaus_Furniere.pdf");
        }
    }

    @Test
    void theStoredResultIsIdenticalDownToEveryBookingLine() {
        ReviewCase submitted;
        try (Kontierung.Persistence persistence = persistence()) {
            submitted = persistence.review().submit(Belege.document("13_XRechnung_Spanexempel_gemischt.xml"));
        }

        try (Kontierung.Persistence reopened = persistence()) {
            ClassificationReport reloaded = reopened.review().find(submitted.id()).report();

            assertThat(reloaded.result()).isEqualTo(submitted.report().result());
            assertThat(reloaded.result().documentType()).isEqualTo(submitted.report().result().documentType());
            assertThat(reloaded.result().buchungszeilen()).hasSize(2);
            assertThat(reloaded.source()).isEqualTo(submitted.report().source());
            assertThat(reloaded.findings()).isEqualTo(submitted.report().findings());
        }
    }

    @Test
    void keepsTheOriginalDocumentImageForTheDatevHandover() {
        ReviewCase submitted;
        try (Kontierung.Persistence persistence = persistence()) {
            submitted = persistence.review().submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        }

        try (Kontierung.Persistence reopened = persistence()) {
            SourceDocument document = reopened.documents().load(submitted.id()).orElseThrow();

            assertThat(document.filename()).isEqualTo("01_PDF_Klaus_Furniere.pdf");
            assertThat(document.content()).isEqualTo(Belege.bytes("01_PDF_Klaus_Furniere.pdf"));
        }
    }

    @Test
    void aDecisionSurvivesARestart() {
        ReviewCase open;
        try (Kontierung.Persistence persistence = persistence()) {
            open = persistence.review().submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
            persistence.review().reject(open.id(), "tom", "Beleg neu anfordern");
        }

        try (Kontierung.Persistence reopened = persistence()) {
            ReviewCase reloaded = reopened.review().find(open.id());

            assertThat(reloaded.status()).isEqualTo(ReviewStatus.ABGELEHNT);
            assertThat(reloaded.decidedBy()).isEqualTo("tom");
            assertThat(reloaded.decisionNote()).isEqualTo("Beleg neu anfordern");
            assertThat(reloaded.decidedAt()).isNotNull();
        }
    }

    @Test
    void anExportedDocumentStaysExportedAfterARestart() {
        ReviewCase submitted;
        try (Kontierung.Persistence persistence = persistence()) {
            submitted = persistence.review().submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
            persistence.export().exportPending();
        }

        try (Kontierung.Persistence reopened = persistence()) {
            assertThat(reopened.review().find(submitted.id()).exportedAt()).isNotNull();
            assertThat(reopened.review().exportableCases()).isEmpty();
        }
    }

    @Test
    void listsOpenCasesNewestFirst() {
        try (Kontierung.Persistence persistence = persistence()) {
            ReviewRepository repository = persistence.repository();
            ReviewCase older = persistence.review().submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
            ReviewCase newer = persistence.review().submit(Belege.document("29_konstruiert_Rechenfehler.pdf"));

            assertThat(repository.findByStatus(ReviewStatus.OFFEN))
                    .extracting(ReviewCase::id)
                    .containsExactly(newer.id(), older.id());
        }
    }

    @Test
    void anUnknownCaseIsSimplyAbsent() {
        try (Kontierung.Persistence persistence = persistence()) {
            assertThat(persistence.repository().findById("gibt-es-nicht")).isEmpty();
            assertThat(persistence.documents().load("gibt-es-nicht")).isEmpty();
        }
    }

    @Test
    void savingTheSameCaseTwiceUpdatesItInsteadOfDuplicating() {
        try (Kontierung.Persistence persistence = persistence()) {
            ReviewCase open = persistence.review().submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

            persistence.review().approve(open.id(), "tom", null);
            persistence.review().markExported(open.id(), Instant.now());

            List<ReviewCase> all = persistence.repository().findAll();
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().status()).isEqualTo(ReviewStatus.FREIGEGEBEN);
            assertThat(all.getFirst().exportedAt()).isNotNull();
        }
    }
}
