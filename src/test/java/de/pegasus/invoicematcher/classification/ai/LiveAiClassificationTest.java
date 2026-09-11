package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.config.AiProperties;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die echte Gegenprobe: ein realer Beleg gegen die echte Gemini/Groq-API, ueber den
 * geteilten Projekt-Vault-Schluessel-Pool. Laeuft nur, wenn
 * {@code INVOICEMATCHER_LIVE_AI_TEST=true} gesetzt ist (z.B. via vault-mcp
 * {@code vault_project_run_command}) - in der normalen CI/lokalen Testsuite ausgeschaltet,
 * damit kein Testlauf versehentlich echte API-Kosten verursacht.
 */
@EnabledIfEnvironmentVariable(named = "INVOICEMATCHER_LIVE_AI_TEST", matches = "true")
class LiveAiClassificationTest {

    private static List<String> keysFrom(String... envNames) {
        List<String> keys = new ArrayList<>();
        for (String name : envNames) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                keys.add(value);
            }
        }
        return keys;
    }

    private static ClassificationService liveService() {
        List<String> groq = keysFrom("GROQ_API_KEY_1", "GROQ_API_KEY_2", "GROQ_API_KEY_3", "GROQ_API_KEY_4");
        List<String> gemini = keysFrom("GOOGLE_API_KEY_1", "GOOGLE_API_KEY_2", "GOOGLE_API_KEY_3", "GOOGLE_API_KEY_4");
        AiProperties properties = new AiProperties(
                new AiProperties.Tier("openai/gpt-oss-120b", groq),
                new AiProperties.Tier("gemini-flash-latest", gemini));
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(() -> properties);
        return Kontierung.classificationServiceWith(ExtractionService.standard(), classifier);
    }

    @Test
    void classifiesARealStructuredInvoiceAsRechnungseingang() {
        ClassificationReport report = liveService().classify(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        System.out.println("Live-KI-Ergebnis: " + report.result());
        System.out.println("Findings: " + report.findings());

        assertThat(report.result().documentType().carriesBookingData()
                || report.result().documentType().isReview()).isTrue();
        assertThat(report.result().buchungszeilen()).isNotNull();
    }
}
