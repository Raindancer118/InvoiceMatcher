package de.pegasus.invoicematcher.classification.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Gegenprobe zur Schluesselansicht: laufen die Kontingentangaben, die dort stehen,
 * wirklich so aus der Antwort des Anbieters heraus? Die Header-Namen sind sonst nur eine
 * Annahme aus der Dokumentation.
 *
 * <p>Wie {@link LiveAiClassificationTest} nur mit {@code INVOICEMATCHER_LIVE_AI_TEST=true}.
 */
@EnabledIfEnvironmentVariable(named = "INVOICEMATCHER_LIVE_AI_TEST", matches = "true")
class LiveAiKeyProbeTest {

    private static String key(String name) {
        String wert = System.getenv(name);
        return wert == null || wert.isBlank() ? null : wert;
    }

    @Test
    void readsTheRealRemainingQuotaFromGroq() {
        String schluessel = key("GROQ_API_KEY_1");
        assertThat(schluessel).as("GROQ_API_KEY_1 muss gesetzt sein").isNotNull();

        AiKeyProbe.Ergebnis ergebnis = new AiKeyProbe().groq("openai/gpt-oss-120b", schluessel);

        System.out.println("Groq-Schluessel 1: " + ergebnis);
        assertThat(ergebnis.ok()).as("Meldung: " + ergebnis.meldung()).isTrue();
        assertThat(ergebnis.restAnfragen()).as("Restanfragen aus dem Header").isNotNull();
        assertThat(ergebnis.restToken()).as("Resttoken aus dem Header").isNotNull();
    }

    @Test
    void reachesGeminiWithoutSpendingQuota() {
        String schluessel = key("GOOGLE_API_KEY_1");
        assertThat(schluessel).as("GOOGLE_API_KEY_1 muss gesetzt sein").isNotNull();

        AiKeyProbe.Ergebnis ergebnis = new AiKeyProbe().gemini(schluessel);

        System.out.println("Gemini-Schluessel 1: " + ergebnis);
        assertThat(ergebnis.ok()).as("Meldung: " + ergebnis.meldung()).isTrue();
    }
}
