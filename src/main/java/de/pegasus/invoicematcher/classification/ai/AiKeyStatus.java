package de.pegasus.invoicematcher.classification.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Der Zustand eines einzelnen KI-Schluessels, wie ihn die Schluesselansicht zeigt.
 *
 * <p>{@code schluessel} ist immer nur die Maske ("gsk_…a1b2"): der Klartext verlaesst den
 * Server nicht, auch nicht fuer eine Statusanzeige. Die Werte vor {@code probe} stammen aus dem
 * laufenden Pool und kosten keine Anfrage; {@code probe} steht nur dann drin, wenn der Nutzer
 * ausdruecklich eine Lebendpruefung ausgeloest hat.
 *
 * @param stufe    "fast" oder "smart"
 * @param position Platz in der Schluesselliste, wie in den Einstellungen eingetragen (ab 1)
 */
public record AiKeyStatus(@JsonProperty("stufe") String stufe,
                          @JsonProperty("anbieter") String anbieter,
                          @JsonProperty("position") int position,
                          @JsonProperty("schluessel") String schluessel,
                          @JsonProperty("verfuegbar") boolean verfuegbar,
                          @JsonProperty("gesperrtBis") Instant gesperrtBis,
                          @JsonProperty("nutzungen") long nutzungen,
                          @JsonProperty("erfolge") long erfolge,
                          @JsonProperty("fehler") long fehler,
                          @JsonProperty("kontingenttreffer") long kontingenttreffer,
                          @JsonProperty("zuverlaessigkeit") double zuverlaessigkeit,
                          @JsonProperty("probe") Probe probe) {

    /**
     * Das Ergebnis einer Lebendpruefung. {@code restAnfragen}/{@code restToken} bleiben bei
     * Gemini {@code null} - der Anbieter meldet keine Restkontingente zurueck.
     *
     * <p>{@code geprueftAm} gehoert dazu, weil das Ergebnis den Seitenaufruf ueberdauert: ohne
     * den Zeitpunkt waere nicht erkennbar, ob die Zahlen von eben oder von vorgestern sind.
     */
    public record Probe(@JsonProperty("ok") boolean ok,
                        @JsonProperty("meldung") String meldung,
                        @JsonProperty("dauerMs") long dauerMs,
                        @JsonProperty("restAnfragen") Integer restAnfragen,
                        @JsonProperty("restToken") Integer restToken,
                        @JsonProperty("kontingentZurueckAb") Instant kontingentZurueckAb,
                        @JsonProperty("geprueftAm") Instant geprueftAm) {
    }

    /**
     * "gsk_abcdefgh1234" wird zu "gsk_…1234" - genug zum Wiedererkennen, zu wenig zum Benutzen.
     *
     * <p>Immer vier Zeichen vorn und vier hinten, unabhaengig vom Aufbau des Schluessels: eine
     * am Anbieterpraefix ausgerichtete Maske wuerde je nach Schluessel unterschiedlich lang
     * ausfallen und die Liste damit schwerer vergleichbar machen.
     */
    public static String maskiere(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "—";
        }
        String schluessel = apiKey.strip();
        if (schluessel.length() <= 8) {
            return "…" + schluessel.substring(Math.max(0, schluessel.length() - 2));
        }
        return schluessel.substring(0, 4) + "…" + schluessel.substring(schluessel.length() - 4);
    }
}
