package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AiProperties;
import io.github.raindancer118.revolver.ChamberStats;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Sammelt den Zustand aller hinterlegten KI-Schluessel fuer die Schluesselansicht.
 *
 * <p>Zwei Genauigkeiten, bewusst getrennt: {@link #status()} liest nur, was der laufende Pool
 * ohnehin weiss (Nutzungen, Fehler, laufende Sperren) und kostet nichts. {@link #pruefe()} fragt
 * zusaetzlich jeden Schluessel einzeln beim Anbieter an - das kostet bei Groq ein paar Token pro
 * Schluessel und beantwortet dafuer die Frage, wie viel Kontingent wirklich noch da ist und wann
 * es zurueckkommt. Deshalb laeuft es nur auf ausdrueckliche Anforderung, nie nebenbei.
 */
@Service
public class AiKeyStatusService {

    private final Supplier<AiProperties> settings;
    private final AiKeyPool pool;
    private final AiKeyProbe probe;

    public AiKeyStatusService(Supplier<AiProperties> settings, AiKeyPool pool, AiKeyProbe probe) {
        this.settings = settings;
        this.pool = pool;
        this.probe = probe;
    }

    /** Der Zustand aus dem laufenden Pool - ohne eine einzige Anfrage an einen Anbieter. */
    public List<AiKeyStatus> status() {
        return sammle(false);
    }

    /** Derselbe Zustand, zusaetzlich mit einer Lebendpruefung je Schluessel. */
    public List<AiKeyStatus> pruefe() {
        return sammle(true);
    }

    private List<AiKeyStatus> sammle(boolean mitProbe) {
        AiProperties aktuell = settings.get();
        List<AiKeyStatus> alle = new ArrayList<>();
        alle.addAll(fuerStufe(AiKeyPool.Stufe.FAST, "fast", aktuell.fast(), mitProbe));
        alle.addAll(fuerStufe(AiKeyPool.Stufe.SMART, "smart", aktuell.smart(), mitProbe));
        return alle;
    }

    private List<AiKeyStatus> fuerStufe(AiKeyPool.Stufe stufe, String name, AiProperties.Tier tier,
                                        boolean mitProbe) {
        List<String> schluessel = tier.apiKeys();
        Map<String, ChamberStats> poolZustand = pool.status(stufe, schluessel).stream()
                .collect(Collectors.toMap(ChamberStats::key, eintrag -> eintrag, (a, b) -> a));

        List<AiKeyStatus> zeilen = new ArrayList<>(schluessel.size());
        for (int i = 0; i < schluessel.size(); i++) {
            String key = schluessel.get(i);
            ChamberStats zustand = poolZustand.get(key);
            zeilen.add(new AiKeyStatus(name, stufe.provider(), i + 1, AiKeyStatus.maskiere(key),
                    zustand == null || zustand.available(),
                    zustand == null ? null : sperreAb(zustand),
                    zustand == null ? 0 : zustand.usages(),
                    zustand == null ? 0 : zustand.successes(),
                    zustand == null ? 0 : zustand.errors(),
                    zustand == null ? 0 : zustand.rateLimitHits(),
                    zustand == null ? 1.0 : zustand.confidence(),
                    mitProbe ? pruefe(stufe, tier.model(), key) : null));
        }
        return zeilen;
    }

    /** Eine Sperre in der Vergangenheit ist keine - der Schluessel ist dann laengst wieder frei. */
    private Instant sperreAb(ChamberStats zustand) {
        Instant gesperrtBis = zustand.lockedUntil();
        return gesperrtBis == null || gesperrtBis.isBefore(Instant.now()) ? null : gesperrtBis;
    }

    private AiKeyStatus.Probe pruefe(AiKeyPool.Stufe stufe, String model, String apiKey) {
        AiKeyProbe.Ergebnis ergebnis = stufe == AiKeyPool.Stufe.FAST
                ? probe.groq(model, apiKey)
                : probe.gemini(apiKey);
        return new AiKeyStatus.Probe(ergebnis.ok(), ergebnis.meldung(), ergebnis.dauerMs(),
                ergebnis.restAnfragen(), ergebnis.restToken(), ergebnis.kontingentZurueckAb());
    }
}
