package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.support.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiConnectionTesterTest {

    @Test
    void reportsFailureWhenNoKeyIsGiven() {
        AiConnectionTester tester = new AiConnectionTester(new FakeHttpTransport());

        AiConnectionTester.Result result = tester.testGroq("llama-3.3-70b-versatile", List.of());

        assertThat(result.ok()).isFalse();
        assertThat(result.meldung()).isNotBlank();
    }

    @Test
    void reportsSuccessOnAWorkingGroqKey() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}");

        AiConnectionTester.Result result = new AiConnectionTester(transport)
                .testGroq("llama-3.3-70b-versatile", List.of("key"));

        assertThat(result.ok()).isTrue();
        assertThat(result.meldung()).contains("groq");
    }

    @Test
    void reportsSuccessOnAWorkingGeminiKey() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .queue(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}");

        AiConnectionTester.Result result = new AiConnectionTester(transport)
                .testGemini("gemini-2.5-pro", List.of("key"));

        assertThat(result.ok()).isTrue();
        assertThat(result.meldung()).contains("google-gemini");
    }

    @Test
    void reportsTheProviderErrorOnFailure() {
        FakeHttpTransport transport = new FakeHttpTransport().queue(403, "{\"error\":\"invalid key\"}");

        AiConnectionTester.Result result = new AiConnectionTester(transport)
                .testGroq("llama-3.3-70b-versatile", List.of("wrong-key"));

        assertThat(result.ok()).isFalse();
        assertThat(result.meldung()).isNotBlank();
    }
}
