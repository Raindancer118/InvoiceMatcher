package de.pegasus.invoicematcher.classification.ai;

import io.github.raindancer118.aigateway.AiProvider;
import io.github.raindancer118.aigateway.http.HttpTransport;
import io.github.raindancer118.aigateway.http.JdkHttpTransport;
import io.github.raindancer118.aigateway.provider.GoogleGeminiProvider;
import io.github.raindancer118.aigateway.provider.OpenAiCompatibleProvider;
import io.github.raindancer118.revolver.ChamberStats;
import io.github.raindancer118.revolver.Persister;
import io.github.raindancer118.revolver.Revolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Haelt je Eskalationsstufe einen langlebigen Schluessel-Pool.
 *
 * <p>Vorher baute jede Klassifikation ihren eigenen {@code Revolver}: ein Schluessel, der in ein
 * Kontingentlimit gelaufen war, galt beim naechsten Beleg wieder als frisch, und die Sperrzeit
 * des Anbieters war vergessen. Ein gemeinsamer Pool merkt sich Sperren und Trefferquoten ueber
 * Belege hinweg - und erst dadurch gibt es ueberhaupt einen Schluesselstatus, den man anzeigen
 * kann ({@link AiKeyStatusService}).
 *
 * <p>Der Pool wird neu aufgebaut, sobald sich die Schluesselliste in den Einstellungen aendert;
 * das Modell darf sich frei aendern, es haengt an der Anfrage, nicht am Pool.
 */
public class AiKeyPool {

    private static final Logger log = LoggerFactory.getLogger(AiKeyPool.class);

    /** Die beiden Eskalationsstufen mit dem Anbieter, der dahinter steht. */
    public enum Stufe {
        FAST("groq"),
        SMART("google-gemini");

        private final String provider;

        Stufe(String provider) {
            this.provider = provider;
        }

        public String provider() {
            return provider;
        }
    }

    private record Eintrag(List<String> apiKeys, AiProvider provider) {
    }

    private final HttpTransport transport;
    private final ZustandsAblage ablage;
    private final Map<Stufe, Eintrag> pools = new EnumMap<>(Stufe.class);

    public AiKeyPool() {
        this(new JdkHttpTransport(), ZustandsAblage.ohne());
    }

    public AiKeyPool(HttpTransport transport, ZustandsAblage ablage) {
        this.transport = transport;
        this.ablage = ablage;
    }

    /**
     * Der Anbieter zur Stufe, mit dem gemeinsamen Schluessel-Pool dahinter.
     *
     * @throws IOException wenn der Pool nicht aufgebaut werden kann (z.B. unlesbarer Zustand)
     */
    public synchronized AiProvider provider(Stufe stufe, List<String> apiKeys) throws IOException {
        Eintrag vorhanden = pools.get(stufe);
        if (vorhanden != null && vorhanden.apiKeys().equals(apiKeys)) {
            return vorhanden.provider();
        }
        Revolver revolver = baueRevolver(stufe, apiKeys);
        AiProvider provider = stufe == Stufe.FAST
                ? OpenAiCompatibleProvider.groq(revolver, transport)
                : new GoogleGeminiProvider(revolver, transport);
        pools.put(stufe, new Eintrag(List.copyOf(apiKeys), provider));
        return provider;
    }

    /**
     * Der Zustand jedes Schluessels der Stufe - ohne eine einzige Anfrage an den Anbieter.
     *
     * @return leer, wenn kein Schluessel hinterlegt ist oder der Pool nicht aufgebaut werden kann
     */
    public synchronized List<ChamberStats> status(Stufe stufe, List<String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return List.of();
        }
        try {
            return provider(stufe, apiKeys).keyPoolStatus();
        } catch (IOException | RuntimeException e) {
            log.warn("Schluesselstatus fuer Stufe {} nicht abrufbar: {}", stufe, e.getMessage());
            return List.of();
        }
    }

    private Revolver baueRevolver(Stufe stufe, List<String> apiKeys) throws IOException {
        Revolver.Builder builder = Revolver.builder(apiKeys);
        Optional<Persister> persister = ablage.persisterFuer(stufe);
        persister.ifPresent(builder::persister);
        return builder.load();
    }
}
