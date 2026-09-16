package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.support.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Einzelpruefung eines Schluessels - bewusst am ai-gateway vorbei ueber rohes HTTP, weil
 * nur die Antwort-Header sagen, wie viel Kontingent der Schluessel noch hat und wann es
 * zurueckkommt. Der Gateway-Pfad reicht die Header nicht durch.
 */
class AiKeyProbeTest {

    private static final String GROQ_OK = """
            {"choices":[{"message":{"content":"OK"}}]}
            """;

    @Test
    void readsTheRemainingGroqQuotaFromTheResponseHeaders() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(200, GROQ_OK, Map.of(
                "x-ratelimit-remaining-requests", "941",
                "x-ratelimit-remaining-tokens", "17532",
                "x-ratelimit-reset-requests", "2m59.56s"));
        AiKeyProbe probe = new AiKeyProbe(transport);

        AiKeyProbe.Ergebnis ergebnis = probe.groq("openai/gpt-oss-120b", "gsk_testschluessel");

        assertThat(ergebnis.ok()).isTrue();
        assertThat(ergebnis.restAnfragen()).isEqualTo(941);
        assertThat(ergebnis.restToken()).isEqualTo(17532);
        assertThat(ergebnis.kontingentZurueckAb()).isCloseTo(Instant.now().plusSeconds(179),
                within(Duration.ofSeconds(5)));
    }

    @Test
    void reportsAnExhaustedGroqKeyWithTheMomentItComesBack() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(429,
                "{\"error\":{\"message\":\"Rate limit reached\"}}",
                Map.of("retry-after", "42", "x-ratelimit-remaining-requests", "0"));
        AiKeyProbe probe = new AiKeyProbe(transport);

        AiKeyProbe.Ergebnis ergebnis = probe.groq("openai/gpt-oss-120b", "gsk_testschluessel");

        assertThat(ergebnis.ok()).isFalse();
        assertThat(ergebnis.meldung()).contains("Kontingent");
        assertThat(ergebnis.restAnfragen()).isZero();
        assertThat(ergebnis.kontingentZurueckAb()).isCloseTo(Instant.now().plusSeconds(42),
                within(Duration.ofSeconds(5)));
    }

    @Test
    void namesAnInvalidGroqKeyAsSuchInsteadOfAsAQuotaProblem() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(401,
                "{\"error\":{\"message\":\"Invalid API Key\"}}");

        AiKeyProbe.Ergebnis ergebnis = new AiKeyProbe(transport).groq("modell", "gsk_falsch");

        assertThat(ergebnis.ok()).isFalse();
        assertThat(ergebnis.meldung()).contains("Schluessel").doesNotContain("Kontingent");
        assertThat(ergebnis.kontingentZurueckAb()).isNull();
    }

    /** Die Modellliste kostet kein Kontingent - fuer eine reine Lebendpruefung genau richtig. */
    @Test
    void checksGeminiWithoutSpendingAnyTokens() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, "{\"models\":[{\"name\":\"models/gemini-flash-latest\"}]}");
        AiKeyProbe probe = new AiKeyProbe(transport);

        AiKeyProbe.Ergebnis ergebnis = probe.gemini("AIza_testschluessel");

        assertThat(ergebnis.ok()).isTrue();
        assertThat(transport.receivedRequests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri().toString()).contains("generativelanguage.googleapis.com");
        });
    }

    @Test
    void readsTheRetryDelayFromAnExhaustedGeminiKey() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded",
                 "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"27s"}]}}
                """);

        AiKeyProbe.Ergebnis ergebnis = new AiKeyProbe(transport).gemini("AIza_testschluessel");

        assertThat(ergebnis.ok()).isFalse();
        assertThat(ergebnis.meldung()).contains("Kontingent");
        assertThat(ergebnis.kontingentZurueckAb()).isCloseTo(Instant.now().plusSeconds(27),
                within(Duration.ofSeconds(5)));
    }

    /** Ein Netzwerkfehler ist kein Kontingentproblem - die Unterscheidung traegt die Anzeige. */
    @Test
    void survivesATransportFailure() {
        AiKeyProbe.Ergebnis ergebnis = new AiKeyProbe(request -> {
            throw new java.io.IOException("Verbindung abgelehnt");
        }).gemini("AIza_testschluessel");

        assertThat(ergebnis.ok()).isFalse();
        assertThat(ergebnis.meldung()).contains("Verbindung abgelehnt");
        assertThat(ergebnis.kontingentZurueckAb()).isNull();
    }

    private static org.assertj.core.data.TemporalUnitOffset within(Duration toleranz) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(toleranz.toMillis(),
                java.time.temporal.ChronoUnit.MILLIS);
    }
}
