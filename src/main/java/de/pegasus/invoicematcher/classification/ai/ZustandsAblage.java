package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.SecretCipher;
import io.github.raindancer118.revolver.ChamberState;
import io.github.raindancer118.revolver.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legt den Zustand der Schluessel-Pools (Sperrzeiten, Trefferquoten) verschluesselt auf Platte.
 *
 * <p>Verschluesselt, weil der Zustand die Schluessel selbst enthaelt - sie stehen in SQLite
 * ebenfalls nur verschluesselt ({@code SettingsRepository}), und eine Zustandsdatei im Klartext
 * daneben waere genau die Luecke, die das dort verhindern soll. Gleicher Schluessel, gleiches
 * Verfahren ({@link SecretCipher}, AES-256-GCM).
 *
 * <p>Ohne Verzeichnis ({@link #ohne()}) haelt der Pool seinen Zustand nur im Speicher - so
 * laufen Tests, ohne Dateien zu hinterlassen.
 */
public final class ZustandsAblage {

    private static final Logger log = LoggerFactory.getLogger(ZustandsAblage.class);

    private final Path verzeichnis;
    private final SecretCipher cipher;

    private ZustandsAblage(Path verzeichnis, SecretCipher cipher) {
        this.verzeichnis = verzeichnis;
        this.cipher = cipher;
    }

    public static ZustandsAblage in(Path verzeichnis, SecretCipher cipher) {
        return new ZustandsAblage(verzeichnis, cipher);
    }

    /** Ohne Ablage: der Pool vergisst seinen Zustand mit dem Prozess. */
    public static ZustandsAblage ohne() {
        return new ZustandsAblage(null, null);
    }

    Optional<Persister> persisterFuer(AiKeyPool.Stufe stufe) {
        if (verzeichnis == null) {
            return Optional.empty();
        }
        return Optional.of(new VerschluesselterPersister(
                verzeichnis.resolve("ai-schluessel-" + stufe.name().toLowerCase() + ".state")));
    }

    /** Ein Schluesselzustand in der Form, in der er auf Platte geht. */
    private record Zeile(String key, String lockedUntil, long usages, long successes, long errors,
                         long rateLimitHits, long sessionUsages, double capacityEma, int observations) {

        static Zeile von(ChamberState zustand) {
            return new Zeile(zustand.getKey(),
                    zustand.getLockedUntil() == null ? null : zustand.getLockedUntil().toString(),
                    zustand.getUsages(), zustand.getSuccesses(), zustand.getErrors(),
                    zustand.getRateLimitHits(), zustand.getSessionUsages(),
                    zustand.getCapacityEma(), zustand.getObservations());
        }

        ChamberState zuZustand() {
            return new ChamberState(key, lockedUntil == null ? null : Instant.parse(lockedUntil),
                    usages, successes, errors, rateLimitHits, sessionUsages, capacityEma, observations);
        }
    }

    private final class VerschluesselterPersister implements Persister {

        private final JsonMapper mapper = JsonMapper.builder().build();
        private final Path datei;

        private VerschluesselterPersister(Path datei) {
            this.datei = datei;
        }

        @Override
        public void save(List<ChamberState> zustaende) throws IOException {
            List<Zeile> zeilen = zustaende.stream().map(Zeile::von).toList();
            String verschluesselt = cipher.encrypt(mapper.writeValueAsString(zeilen));
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, verschluesselt, StandardCharsets.UTF_8);
        }

        @Override
        public List<ChamberState> load() throws IOException {
            if (!Files.isRegularFile(datei)) {
                return List.of();
            }
            try {
                String klartext = cipher.decrypt(Files.readString(datei, StandardCharsets.UTF_8).strip());
                List<Zeile> zeilen = mapper.readValue(klartext, new tools.jackson.core.type.TypeReference<>() {
                });
                List<ChamberState> zustaende = new ArrayList<>(zeilen.size());
                zeilen.forEach(zeile -> zustaende.add(zeile.zuZustand()));
                return zustaende;
            } catch (RuntimeException e) {
                // Ein unlesbarer Zustand ist kein Grund, die Klassifikation anzuhalten: die
                // Schluessel starten dann eben wieder als unbenutzt.
                log.warn("Schluesselzustand '{}' ist nicht lesbar, beginne mit leerem Pool: {}",
                        datei, e.getMessage());
                return List.of();
            }
        }
    }
}
