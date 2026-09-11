package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Modellwahl fuer die KI-Klassifikation - zwei Eskalationsstufen.
 *
 * <p>{@code fast} (Groq) wird zuerst versucht: schnell und guenstig, aber ohne Bildeingabe.
 * {@code smart} (Gemini, vision-faehig) uebernimmt gescannte Belege direkt und alles, wofuer
 * {@code fast} keine brauchbare Antwort liefert. Beide Stufen haben einen eigenen Schluessel-Pool
 * (rotierend ueber {@code ai-gateway}/{@code Revolver}), damit ein einzelnes Tageskontingent die
 * Klassifikation nicht fuer den Rest des Tages lahmlegt.
 *
 * <p>Nur der Vorgabewert fuer den allerersten Start (siehe {@code SettingsSeeder}); danach ist
 * die Einstellungen-Oberflaeche massgeblich ({@code AiSettingsService}) - Modell und Schluessel
 * sind jederzeit ohne Neustart austauschbar.
 */
@ConfigurationProperties("invoicematcher.ai")
public record AiProperties(Tier fast, Tier smart) {

    public AiProperties {
        fast = fast == null ? new Tier("llama-3.3-70b-versatile", List.of()) : fast;
        smart = smart == null ? new Tier("gemini-2.5-pro", List.of()) : smart;
    }

    public record Tier(String model, List<String> apiKeys) {
        public Tier {
            apiKeys = apiKeys == null ? List.of()
                    : apiKeys.stream().filter(key -> key != null && !key.isBlank()).toList();
        }
    }
}
