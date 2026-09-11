package de.pegasus.invoicematcher.extraction.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Zeichenerkennung ueber die Gemini-Vision-API - der Ausweichweg fuer Seiten, deren
 * handschriftliche Ueberschrift Tesseract nicht lesen konnte.
 *
 * <p>Bewusst nicht die primaere Erkennung: das Belegbild verlaesst dafuer den eigenen Server an
 * einen Cloud-Dienst, was einen Auftragsverarbeitungsvertrag mit dem Anbieter voraussetzt und
 * laufende Kosten verursacht. {@link FallbackOcrTextRecognizer} fragt diese Klasse deshalb nur
 * an, wenn die erste Erkennung keinen bekannten Belegtitel gefunden hat.
 *
 * <p>Aufgerufen wird die REST-Schnittstelle direkt statt ueber ein SDK - das Projekt haengt
 * damit an keiner zusaetzlichen Bibliothek, nur an {@code HttpClient} und Jackson, die ueber
 * {@code spring-boot-starter-web} ohnehin vorhanden sind.
 */
public class GeminiVisionRecognizer implements OcrTextRecognizer {

    private static final Logger log = LoggerFactory.getLogger(GeminiVisionRecognizer.class);

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String PROMPT = """
            Lies den Text auf diesem Belegbild vollstaendig und wortgetreu, auch wenn er von Hand
            geschrieben ist. Gib ausschliesslich den erkannten Text zurueck, keine Erklaerung und
            keine Formatierung. Erhalte die Zeilenumbrueche des Originals. Stehen mehrere
            Textbloecke nebeneinander (z.B. Spalten oder Labels und Werte), trenne sie in derselben
            Zeile durch mindestens drei Leerzeichen, damit die Spalten erkennbar bleiben.
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;

    public GeminiVisionRecognizer(String apiKey, String model, Duration timeout) {
        this(apiKey, model, DEFAULT_BASE_URL, timeout);
    }

    public GeminiVisionRecognizer(String apiKey, String model, String baseUrl, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? "gemini-flash-latest" : model;
        this.baseUrl = baseUrl;
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String recognize(BufferedImage page) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + model + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody(page)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new OcrException("Gemini-Zeichenerkennung meldet Code " + response.statusCode()
                        + ": " + response.body());
            }
            return extractText(response.body());
        } catch (IOException e) {
            throw new OcrException("Gemini-Zeichenerkennung fehlgeschlagen: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("Gemini-Zeichenerkennung wurde unterbrochen", e);
        }
    }

    private byte[] requestBody(BufferedImage page) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", PROMPT);
        ObjectNode inlineData = parts.addObject().putObject("inline_data");
        inlineData.put("mime_type", "image/png");
        inlineData.put("data", encode(page));
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String encode(BufferedImage page) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(page, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = JSON.readTree(responseBody);
        JsonNode text = root.at("/candidates/0/content/parts/0/text");
        if (text.isMissingNode()) {
            throw new OcrException("Gemini-Zeichenerkennung lieferte keinen Text: " + responseBody);
        }
        return text.asText();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "GeminiVisionRecognizer[%s]", model);
    }
}
