package de.pegasus.invoicematcher.extraction.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Die Cloud-Zeichenerkennung wird gegen ein eingebettetes HTTP-Testdouble geprueft statt gegen
 * die echte Gemini-API: keine Netzabhaengigkeit, kein API-Schluessel im Testlauf noetig, und der
 * Test bleibt deterministisch.
 */
class GeminiVisionRecognizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeminiVisionRecognizer recognizer(String apiKey) {
        return new GeminiVisionRecognizer(apiKey, "gemini-flash-latest", baseUrl, Duration.ofSeconds(5));
    }

    @Test
    void isNotAvailableWithoutApiKey() {
        assertThat(recognizer(null).isAvailable()).isFalse();
        assertThat(recognizer("  ").isAvailable()).isFalse();
        assertThat(recognizer("secret").isAvailable()).isTrue();
    }

    @Test
    void sendsThePageAndReturnsTheRecognisedText() throws IOException {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedApiKeyHeader = new AtomicReference<>();
        AtomicReference<JsonNode> receivedBody = new AtomicReference<>();

        server.createContext("/gemini-flash-latest:generateContent", exchange -> {
            receivedPath.set(exchange.getRequestURI().toString());
            receivedApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedBody.set(JSON.readTree(exchange.getRequestBody()));

            String response = """
                    {"candidates":[{"content":{"parts":[{"text":"Rechnung\\n\\nVon : Mir"}]}}]}""";
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        String text = recognizer("test-key").recognize(page());

        assertThat(text).isEqualTo("Rechnung\n\nVon : Mir");
        assertThat(receivedApiKeyHeader.get()).isEqualTo("test-key");
        assertThat(receivedPath.get()).isEqualTo("/gemini-flash-latest:generateContent");

        JsonNode parts = receivedBody.get().at("/contents/0/parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(1).at("/inline_data/mime_type").asText()).isEqualTo("image/png");
        assertThat(parts.get(1).at("/inline_data/data").asText()).isNotBlank();
    }

    @Test
    void failsWithAnOcrExceptionOnAnErrorResponse() {
        server.createContext("/gemini-flash-latest:generateContent", exchange -> {
            byte[] payload = "{\"error\":{\"message\":\"invalid key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        assertThatThrownBy(() -> recognizer("wrong-key").recognize(page()))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("403");
    }

    private static BufferedImage page() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }
}
