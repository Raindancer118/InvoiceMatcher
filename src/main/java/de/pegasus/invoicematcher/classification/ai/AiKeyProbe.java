package de.pegasus.invoicematcher.classification.ai;

import io.github.raindancer118.aigateway.http.HttpTransport;
import io.github.raindancer118.aigateway.http.JdkHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueft einen einzelnen KI-Schluessel und liest dabei aus, wie viel Kontingent er noch hat.
 *
 * <p>Bewusst rohes HTTP statt des Wegs ueber {@code ai-gateway}: die Antwort des Anbieters
 * traegt die Kontingentangaben in den Headern ({@code x-ratelimit-remaining-*},
 * {@code retry-after}), und der Gateway-Pfad reicht nur den Antworttext durch. Ohne diese
 * Header liesse sich die Frage "hat der Schluessel noch Luft, und wenn nein, ab wann wieder"
 * nicht beantworten - nur "geht gerade" oder "geht gerade nicht".
 *
 * <p>Groq wird mit einer Ein-Token-Anfrage geprueft (billigster Weg, der echte Kontingentzahlen
 * zurueckgibt), Gemini ueber die Modellliste - die kostet gar kein Kontingent, liefert dafuer
 * aber auch keine Restangaben. Das ist kein Versehen, sondern die Auskunft, die der Anbieter
 * hergibt; die Oberflaeche sagt das so.
 */
@Component
public class AiKeyProbe {

    private static final Logger log = LoggerFactory.getLogger(AiKeyProbe.class);

    private static final URI GROQ_CHAT = URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final String GEMINI_MODELLE = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=";
    private static final Duration ZEITLIMIT = Duration.ofSeconds(20);

    /** Groq gibt Zurueckstellzeiten als "7.66s", "2m59.56s" oder "18ms" an. */
    private static final Pattern DAUER = Pattern.compile("(?:(\\d+(?:\\.\\d+)?)h)?(?:(\\d+(?:\\.\\d+)?)m(?!s))?"
            + "(?:(\\d+(?:\\.\\d+)?)s)?(?:(\\d+(?:\\.\\d+)?)ms)?");

    /**
     * @param restAnfragen        verbleibende Anfragen im laufenden Fenster, {@code null} wenn der
     *                            Anbieter dazu nichts sagt (Gemini)
     * @param restToken           verbleibende Token im laufenden Fenster, sonst {@code null}
     * @param kontingentZurueckAb wann das Kontingent wieder aufgefuellt ist, {@code null} wenn
     *                            unbekannt oder gar nicht erschoepft
     */
    public record Ergebnis(boolean ok, String meldung, long dauerMs, Integer restAnfragen,
                           Integer restToken, Instant kontingentZurueckAb) {
    }

    private final HttpTransport transport;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public AiKeyProbe() {
        this(new JdkHttpTransport());
    }

    public AiKeyProbe(HttpTransport transport) {
        this.transport = transport;
    }

    public Ergebnis groq(String model, String apiKey) {
        String body = "{\"model\":\"" + model + "\",\"max_tokens\":1,\"messages\":"
                + "[{\"role\":\"user\",\"content\":\"OK\"}]}";
        HttpRequest request = HttpRequest.newBuilder(GROQ_CHAT)
                .timeout(ZEITLIMIT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request, this::deuteGroq);
    }

    public Ergebnis gemini(String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GEMINI_MODELLE + apiKey))
                .timeout(ZEITLIMIT)
                .GET()
                .build();
        return send(request, this::deuteGemini);
    }

    private Ergebnis send(HttpRequest request, Deuter deuter) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = transport.send(request);
            return deuter.deute(response, dauerMs(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Ergebnis(false, "Pruefung abgebrochen", dauerMs(start), null, null, null);
        } catch (Exception e) {
            log.info("Schluesselpruefung fehlgeschlagen: {}", e.getMessage());
            return new Ergebnis(false, e.getMessage(), dauerMs(start), null, null, null);
        }
    }

    private Ergebnis deuteGroq(HttpResponse<String> response, long dauerMs) {
        Integer restAnfragen = zahl(response, "x-ratelimit-remaining-requests");
        Integer restToken = zahl(response, "x-ratelimit-remaining-tokens");
        Instant zurueck = header(response, "retry-after")
                .map(AiKeyProbe::sekundenOderDauer)
                .or(() -> header(response, "x-ratelimit-reset-requests").flatMap(AiKeyProbe::dauer))
                .map(dauer -> Instant.now().plus(dauer))
                .orElse(null);
        if (response.statusCode() == 429) {
            return new Ergebnis(false, "Kontingent erschoepft" + zusatz(response), dauerMs,
                    restAnfragen, restToken, zurueck);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return new Ergebnis(false, "Schluessel wird abgelehnt" + zusatz(response), dauerMs, null, null, null);
        }
        if (response.statusCode() >= 300) {
            return new Ergebnis(false, "HTTP " + response.statusCode() + zusatz(response), dauerMs,
                    restAnfragen, restToken, null);
        }
        return new Ergebnis(true, "Erreichbar", dauerMs, restAnfragen, restToken, zurueck);
    }

    private Ergebnis deuteGemini(HttpResponse<String> response, long dauerMs) {
        if (response.statusCode() == 429) {
            Instant zurueck = retryDelay(response.body()).map(dauer -> Instant.now().plus(dauer)).orElse(null);
            return new Ergebnis(false, "Kontingent erschoepft" + zusatz(response), dauerMs, null, null, zurueck);
        }
        if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
            return new Ergebnis(false, "Schluessel wird abgelehnt" + zusatz(response), dauerMs, null, null, null);
        }
        if (response.statusCode() >= 300) {
            return new Ergebnis(false, "HTTP " + response.statusCode() + zusatz(response), dauerMs, null, null, null);
        }
        return new Ergebnis(true, "Erreichbar", dauerMs, null, null, null);
    }

    /** Die Meldung des Anbieters, gekuerzt - sie erklaert oft genauer, woran es liegt. */
    private String zusatz(HttpResponse<String> response) {
        try {
            JsonNode fehler = mapper.readTree(response.body()).path("error").path("message");
            if (fehler.isMissingNode() || fehler.isNull()) {
                return "";
            }
            String text = fehler.asText();
            return text.isBlank() ? "" : " - " + (text.length() > 180 ? text.substring(0, 180) + "…" : text);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private Optional<Duration> retryDelay(String body) {
        try {
            for (JsonNode detail : mapper.readTree(body).path("error").path("details")) {
                JsonNode delay = detail.path("retryDelay");
                if (!delay.isMissingNode() && !delay.isNull()) {
                    return dauer(delay.asText());
                }
            }
        } catch (RuntimeException e) {
            log.debug("retryDelay nicht lesbar: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<String> header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).filter(wert -> !wert.isBlank());
    }

    private static Integer zahl(HttpResponse<String> response, String name) {
        return header(response, name).map(wert -> {
            try {
                return Integer.valueOf(wert.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    /** {@code retry-after} ist entweder eine reine Sekundenzahl oder dasselbe Format wie die Reset-Header. */
    private static Duration sekundenOderDauer(String wert) {
        try {
            return Duration.ofSeconds(Long.parseLong(wert.trim()));
        } catch (NumberFormatException e) {
            return dauer(wert).orElse(Duration.ZERO);
        }
    }

    static Optional<Duration> dauer(String wert) {
        Matcher treffer = DAUER.matcher(wert.trim());
        if (!treffer.matches() || treffer.group(0).isEmpty()) {
            return Optional.empty();
        }
        double millis = teil(treffer, 1) * 3_600_000 + teil(treffer, 2) * 60_000
                + teil(treffer, 3) * 1_000 + teil(treffer, 4);
        return millis <= 0 ? Optional.empty() : Optional.of(Duration.ofMillis((long) millis));
    }

    private static double teil(Matcher treffer, int gruppe) {
        String wert = treffer.group(gruppe);
        return wert == null ? 0 : Double.parseDouble(wert);
    }

    private static long dauerMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @FunctionalInterface
    private interface Deuter {
        Ergebnis deute(HttpResponse<String> response, long dauerMs);
    }
}
