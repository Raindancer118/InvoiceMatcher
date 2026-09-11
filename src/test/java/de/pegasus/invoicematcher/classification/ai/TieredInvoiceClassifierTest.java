package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AiProperties;
import de.pegasus.invoicematcher.support.FakeHttpTransport;
import io.github.raindancer118.aigateway.ChatContent;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TieredInvoiceClassifierTest {

    private static ChatRequest textRequest() {
        return ChatRequest.builder().user("Beleg-Text").build();
    }

    private static ChatRequest imageRequest() {
        return ChatRequest.builder()
                .message(ChatMessage.user(List.of(new ChatContent.Text("Beleg"),
                        new ChatContent.Image("image/png", new byte[]{1, 2, 3}))))
                .build();
    }

    private static AiProperties properties(List<String> fastKeys, List<String> smartKeys) {
        return new AiProperties(new AiProperties.Tier("fast-model", fastKeys),
                new AiProperties.Tier("smart-model", smartKeys));
    }

    @Test
    void usesTheFastTierForTextOnlyRequests() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, groqBody("fast-answer"));
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(
                () -> properties(List.of("groq-key"), List.of("gemini-key")), transport);

        Optional<TieredInvoiceClassifier.Result> result = classifier.classify(textRequest());

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("fast-answer");
        assertThat(result.get().provider()).isEqualTo("groq");
        assertThat(transport.receivedRequests()).hasSize(1);
    }

    @Test
    void escalatesToTheSmartTierWhenTheFastTierFails() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(500, "server error")
                .queue(200, geminiBody("smart-answer"));
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(
                () -> properties(List.of("groq-key"), List.of("gemini-key")), transport);

        Optional<TieredInvoiceClassifier.Result> result = classifier.classify(textRequest());

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("smart-answer");
        assertThat(result.get().provider()).isEqualTo("google-gemini");
        assertThat(transport.receivedRequests()).hasSize(2);
    }

    @Test
    void goesDirectlyToTheSmartTierForImageRequestsSinceGroqHasNoVision() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(200, geminiBody("vision-answer"));
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(
                () -> properties(List.of("groq-key"), List.of("gemini-key")), transport);

        Optional<TieredInvoiceClassifier.Result> result = classifier.classify(imageRequest());

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("vision-answer");
        assertThat(transport.receivedRequests()).hasSize(1);
    }

    @Test
    void returnsEmptyWhenNoTierIsConfigured() {
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(
                () -> properties(List.of(), List.of()), new FakeHttpTransport());

        assertThat(classifier.classify(textRequest())).isEmpty();
    }

    @Test
    void returnsEmptyWhenBothTiersFail() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(500, "server error")
                .queue(500, "server error");
        TieredInvoiceClassifier classifier = new TieredInvoiceClassifier(
                () -> properties(List.of("groq-key"), List.of("gemini-key")), transport);

        assertThat(classifier.classify(textRequest())).isEmpty();
    }

    private static String groqBody(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    private static String geminiBody(String content) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + content + "\"}]}}]}";
    }
}
