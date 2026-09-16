package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.SecretCipher;
import de.pegasus.invoicematcher.config.SettingsKeyProperties;
import de.pegasus.invoicematcher.support.FakeHttpTransport;
import io.github.raindancer118.aigateway.AiProvider;
import io.github.raindancer118.aigateway.ChatRequest;
import io.github.raindancer118.revolver.ChamberStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der gemeinsame Schluessel-Pool: er ueberlebt einzelne Belege und - verschluesselt - auch den
 * Neustart. Ohne ihn waere jede Kontingentsperre nach einem Beleg wieder vergessen.
 */
class AiKeyPoolTest {

    private static final String GROQ_OK = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";

    private static SecretCipher cipher() {
        return new SecretCipher(new SettingsKeyProperties(
                Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void keepsOnePoolPerTierAsLongAsTheKeysStayTheSame() throws Exception {
        AiKeyPool pool = new AiKeyPool(new FakeHttpTransport(), ZustandsAblage.ohne());

        AiProvider erst = pool.provider(AiKeyPool.Stufe.FAST, List.of("a", "b"));
        AiProvider nochmal = pool.provider(AiKeyPool.Stufe.FAST, List.of("a", "b"));

        assertThat(nochmal).isSameAs(erst);
        assertThat(erst.name()).isEqualTo("groq");
    }

    @Test
    void buildsANewPoolWhenTheKeysChange() throws Exception {
        AiKeyPool pool = new AiKeyPool(new FakeHttpTransport(), ZustandsAblage.ohne());

        AiProvider erst = pool.provider(AiKeyPool.Stufe.SMART, List.of("a"));
        AiProvider nachAenderung = pool.provider(AiKeyPool.Stufe.SMART, List.of("a", "b"));

        assertThat(nachAenderung).isNotSameAs(erst);
        assertThat(nachAenderung.name()).isEqualTo("google-gemini");
    }

    @Test
    void reportsOneStatusEntryPerKeyWithoutAskingTheProvider() {
        FakeHttpTransport transport = new FakeHttpTransport();
        AiKeyPool pool = new AiKeyPool(transport, ZustandsAblage.ohne());

        List<ChamberStats> status = pool.status(AiKeyPool.Stufe.FAST, List.of("a", "b", "c"));

        assertThat(status).hasSize(3);
        assertThat(status).allSatisfy(eintrag -> assertThat(eintrag.available()).isTrue());
        assertThat(transport.receivedRequests()).isEmpty();
    }

    @Test
    void staysEmptyWithoutAnyKeys() {
        AiKeyPool pool = new AiKeyPool(new FakeHttpTransport(), ZustandsAblage.ohne());

        assertThat(pool.status(AiKeyPool.Stufe.FAST, List.of())).isEmpty();
    }

    /** Die Nutzung eines Schluessels zaehlt mit - das ist die Grundlage der Statusanzeige. */
    @Test
    void countsUsagePerKey() throws Exception {
        FakeHttpTransport transport = new FakeHttpTransport().queue(200, GROQ_OK);
        AiKeyPool pool = new AiKeyPool(transport, ZustandsAblage.ohne());

        pool.provider(AiKeyPool.Stufe.FAST, List.of("nur-ein-schluessel"))
                .chat("modell", ChatRequest.builder().user("hallo").build());

        assertThat(pool.status(AiKeyPool.Stufe.FAST, List.of("nur-ein-schluessel")))
                .singleElement()
                .satisfies(eintrag -> {
                    assertThat(eintrag.usages()).isEqualTo(1);
                    assertThat(eintrag.successes()).isEqualTo(1);
                });
    }

    @Test
    void writesItsStateEncryptedAndReadsItBackAfterARestart(@TempDir Path verzeichnis) throws Exception {
        ZustandsAblage ablage = ZustandsAblage.in(verzeichnis, cipher());
        AiKeyPool pool = new AiKeyPool(new FakeHttpTransport().queue(200, GROQ_OK), ablage);
        pool.provider(AiKeyPool.Stufe.FAST, List.of("gsk_geheimer_schluessel"))
                .chat("modell", ChatRequest.builder().user("hallo").build());

        Path datei = verzeichnis.resolve("ai-schluessel-fast.state");
        assertThat(datei).exists();
        assertThat(Files.readString(datei)).doesNotContain("gsk_geheimer_schluessel");

        AiKeyPool nachNeustart = new AiKeyPool(new FakeHttpTransport(), ablage);
        assertThat(nachNeustart.status(AiKeyPool.Stufe.FAST, List.of("gsk_geheimer_schluessel")))
                .singleElement()
                .satisfies(eintrag -> assertThat(eintrag.usages()).isEqualTo(1));
    }
}
