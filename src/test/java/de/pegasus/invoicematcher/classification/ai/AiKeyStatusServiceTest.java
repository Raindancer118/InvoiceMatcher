package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AiProperties;
import de.pegasus.invoicematcher.support.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiKeyStatusServiceTest {

    private static final String GROQ_OK = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";

    private AiProperties properties = new AiProperties(
            new AiProperties.Tier("openai/gpt-oss-120b", List.of("gsk_erster_schluessel_a1b2", "gsk_zweiter_c3d4")),
            new AiProperties.Tier("gemini-flash-latest", List.of("AIzaSyGeheimGeheim9876")));

    private AiKeyStatusService service(FakeHttpTransport probeTransport) {
        return new AiKeyStatusService(() -> properties,
                new AiKeyPool(new FakeHttpTransport(), ZustandsAblage.ohne()),
                new AiKeyProbe(probeTransport));
    }

    @Test
    void listsEveryConfiguredKeyOfBothTiersWithoutAskingAnyProvider() {
        FakeHttpTransport probeTransport = new FakeHttpTransport();

        List<AiKeyStatus> status = service(probeTransport).status();

        assertThat(status).hasSize(3);
        assertThat(status).extracting(AiKeyStatus::stufe).containsExactly("fast", "fast", "smart");
        assertThat(status).extracting(AiKeyStatus::anbieter).containsExactly("groq", "groq", "google-gemini");
        assertThat(status).extracting(AiKeyStatus::position).containsExactly(1, 2, 1);
        assertThat(status).allSatisfy(zeile -> assertThat(zeile.probe()).isNull());
        assertThat(probeTransport.receivedRequests()).isEmpty();
    }

    /** Ein Statusfenster darf kein Schluesselleak sein - nur Anfang und letzte vier Zeichen. */
    @Test
    void neverExposesAKeyInClearText() {
        List<AiKeyStatus> status = service(new FakeHttpTransport()).status();

        assertThat(status).extracting(AiKeyStatus::schluessel)
                .containsExactly("gsk_…a1b2", "gsk_…c3d4", "AIza…9876");
        assertThat(status).noneSatisfy(zeile ->
                assertThat(zeile.schluessel()).contains("erster_schluessel"));
    }

    @Test
    void addsTheRemainingQuotaPerKeyWhenAskedToProbe() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, GROQ_OK, Map.of("x-ratelimit-remaining-requests", "998",
                        "x-ratelimit-remaining-tokens", "29500"))
                .queue(429, "{\"error\":{\"message\":\"Rate limit reached\"}}", Map.of("retry-after", "30"))
                .queue(200, "{\"models\":[]}");

        List<AiKeyStatus> status = service(transport).pruefe();

        assertThat(status.get(0).probe().ok()).isTrue();
        assertThat(status.get(0).probe().restAnfragen()).isEqualTo(998);
        assertThat(status.get(0).probe().restToken()).isEqualTo(29500);
        assertThat(status.get(1).probe().ok()).isFalse();
        assertThat(status.get(1).probe().meldung()).contains("Kontingent");
        assertThat(status.get(1).probe().kontingentZurueckAb()).isNotNull();
        assertThat(status.get(2).probe().ok()).isTrue();
        assertThat(status.get(2).probe().restAnfragen()).isNull();
    }

    /**
     * Eine Pruefung kostet Kontingent - ihr Ergebnis darf deshalb nicht mit dem naechsten
     * Seitenaufruf verloren gehen, sonst muesste man sie nur zum Nachschauen wiederholen.
     */
    @Test
    void keepsTheLastProbeResultForLaterPageLoads() {
        AiKeyStatusService service = service(new FakeHttpTransport()
                .queue(200, GROQ_OK, Map.of("x-ratelimit-remaining-requests", "998"))
                .queue(200, GROQ_OK, Map.of("x-ratelimit-remaining-requests", "997"))
                .queue(200, "{\"models\":[]}"));

        service.pruefe();
        List<AiKeyStatus> spaeter = service.status();

        assertThat(spaeter.get(0).probe()).isNotNull();
        assertThat(spaeter.get(0).probe().restAnfragen()).isEqualTo(998);
        assertThat(spaeter.get(0).probe().geprueftAm()).isNotNull();
        assertThat(spaeter.get(2).probe().ok()).isTrue();
    }

    /** Ein neuer Schluessel hat noch keine Pruefung - und erbt auch keine fremde. */
    @Test
    void doesNotCarryAProbeOverToAnUncheckedKey() {
        AiKeyStatusService service = service(new FakeHttpTransport()
                .queue(200, GROQ_OK, Map.of("x-ratelimit-remaining-requests", "998"))
                .queue(429, "{}", Map.of("retry-after", "30"))
                .queue(200, "{\"models\":[]}"));
        service.pruefe();

        properties = new AiProperties(
                new AiProperties.Tier("openai/gpt-oss-120b",
                        List.of("gsk_erster_schluessel_a1b2", "gsk_ganz_neuer_e5f6")),
                new AiProperties.Tier("gemini-flash-latest", List.of("AIzaSyGeheimGeheim9876")));

        List<AiKeyStatus> status = service.status();

        assertThat(status.get(0).probe()).isNotNull();
        assertThat(status.get(1).schluessel()).isEqualTo("gsk_…e5f6");
        assertThat(status.get(1).probe()).isNull();
    }

    @Test
    void staysEmptyWhenNoKeysAreConfigured() {
        AiKeyStatusService leer = new AiKeyStatusService(
                () -> new AiProperties(new AiProperties.Tier("a", List.of()),
                        new AiProperties.Tier("b", List.of())),
                new AiKeyPool(new FakeHttpTransport(), ZustandsAblage.ohne()),
                new AiKeyProbe(new FakeHttpTransport()));

        assertThat(leer.status()).isEmpty();
    }
}
