package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.support.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.http.HttpRequest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiVisionRecognizerTest {

    private static BufferedImage page() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    private static GeminiVisionRecognizer.TierConfig tier(String... keys) {
        return new GeminiVisionRecognizer.TierConfig("gemini-2.5-pro", List.of(keys));
    }

    @Test
    void isNotAvailableWithoutApiKeys() {
        GeminiVisionRecognizer recognizer = new GeminiVisionRecognizer(() -> tier(), new FakeHttpTransport());
        assertThat(recognizer.isAvailable()).isFalse();

        GeminiVisionRecognizer withKey = new GeminiVisionRecognizer(() -> tier("secret"), new FakeHttpTransport());
        assertThat(withKey.isAvailable()).isTrue();
    }

    @Test
    void sendsThePageAndReturnsTheRecognisedText() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, """
                        {"candidates":[{"content":{"parts":[{"text":"Rechnung\\n\\nVon : Mir"}]}}]}""");
        GeminiVisionRecognizer recognizer = new GeminiVisionRecognizer(() -> tier("test-key"), transport);

        String text = recognizer.recognize(page());

        assertThat(text).isEqualTo("Rechnung\n\nVon : Mir");
        assertThat(transport.receivedRequests()).hasSize(1);
        HttpRequest sent = transport.receivedRequests().get(0);
        assertThat(sent.headers().firstValue("x-goog-api-key")).contains("test-key");
        assertThat(sent.uri().toString()).contains("gemini-2.5-pro:generateContent");
    }

    @Test
    void failsWithAnOcrExceptionWhenNoKeysAreConfigured() {
        GeminiVisionRecognizer recognizer = new GeminiVisionRecognizer(() -> tier(), new FakeHttpTransport());

        assertThatThrownBy(() -> recognizer.recognize(page())).isInstanceOf(OcrException.class);
    }

    @Test
    void failsWithAnOcrExceptionOnARepeatedNonRetryableError() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(403, "{\"error\":{\"message\":\"invalid key\"}}");
        GeminiVisionRecognizer recognizer = new GeminiVisionRecognizer(() -> tier("wrong-key"), transport);

        assertThatThrownBy(() -> recognizer.recognize(page())).isInstanceOf(OcrException.class);
    }

    @Test
    void readsTheCurrentTierOnEveryCallSoSettingsChangesTakeEffectImmediately() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"a\"}]}}]}")
                .queue(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"b\"}]}}]}");
        java.util.concurrent.atomic.AtomicReference<GeminiVisionRecognizer.TierConfig> current =
                new java.util.concurrent.atomic.AtomicReference<>(tier("key-1"));
        GeminiVisionRecognizer recognizer = new GeminiVisionRecognizer(current::get, transport);

        assertThat(recognizer.recognize(page())).isEqualTo("a");
        current.set(tier("key-2"));
        assertThat(recognizer.recognize(page())).isEqualTo("b");

        assertThat(transport.receivedRequests().get(0).headers().firstValue("x-goog-api-key")).contains("key-1");
        assertThat(transport.receivedRequests().get(1).headers().firstValue("x-goog-api-key")).contains("key-2");
    }
}
