package de.pegasus.invoicematcher.classification;

import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.CannedAiResponses;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jeder kontierte Beleg fuehrt die Gedanken der KI mit - das ist der Teil des Berichts, den die
 * Pruefliste anzeigt, um eine Entscheidung nachvollziehbar zu machen.
 */
class ClassificationReportRationaleTest {

    @Test
    void carriesTheRationaleOfTheSecondAiCall() {
        ClassificationReport report = Kontierung.standardClassificationService()
                .classify(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        ClassificationRationale rationale = report.gedanken();

        assertThat(rationale).isNotNull();
        assertThat(rationale.kurzfassung()).isNotBlank();
        assertThat(rationale.schritte()).isNotEmpty();
    }

    /** Eine ausgefallene Erlaeuterung darf die Kontierung nicht mitreissen. */
    @Test
    void keepsTheBookingWhenTheSecondCallFails() {
        InvoiceClassifier classification = CannedAiResponses.classifier();
        InvoiceClassifier onlyClassification = request -> request.messages().get(0).content()
                .contains("bereits getroffene") ? Optional.empty() : classification.classify(request);

        ClassificationReport report = Kontierung.classificationServiceWith(
                        de.pegasus.invoicematcher.extraction.ExtractionService.standard(), onlyClassification)
                .classify(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        assertThat(report.gedanken()).isNull();
        assertThat(report.result().buchungszeilen()).isNotEmpty();
    }
}
