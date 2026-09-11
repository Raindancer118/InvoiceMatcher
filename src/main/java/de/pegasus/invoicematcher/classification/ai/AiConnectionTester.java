package de.pegasus.invoicematcher.classification.ai;

import io.github.raindancer118.aigateway.AiProvider;
import io.github.raindancer118.aigateway.AiProviderException;
import io.github.raindancer118.aigateway.ChatRequest;
import io.github.raindancer118.aigateway.http.HttpTransport;
import io.github.raindancer118.aigateway.http.JdkHttpTransport;
import io.github.raindancer118.aigateway.provider.GoogleGeminiProvider;
import io.github.raindancer118.aigateway.provider.OpenAiCompatibleProvider;
import io.github.raindancer118.revolver.Revolver;

import java.io.IOException;
import java.util.List;

/**
 * Prueft, ob ein Modell/Schluessel-Paar tatsaechlich funktioniert - fuer den "Verbindung
 * testen"-Knopf in den Einstellungen. Nimmt bewusst die (ggf. noch ungespeicherten)
 * Kandidatenwerte direkt entgegen, nicht {@code AiSettingsService}, damit der Nutzer einen
 * Schluessel pruefen kann, bevor er ihn speichert.
 */
public class AiConnectionTester {

    private static final String PING_PROMPT = "Antworte ausschliesslich mit dem einzigen Wort OK.";

    public record Result(boolean ok, String meldung) {
        static Result success(String provider, String model) {
            return new Result(true, "Verbindung erfolgreich (" + provider + "/" + model + ")");
        }

        static Result failure(String meldung) {
            return new Result(false, meldung);
        }
    }

    private final HttpTransport transport;

    public AiConnectionTester() {
        this(new JdkHttpTransport());
    }

    public AiConnectionTester(HttpTransport transport) {
        this.transport = transport;
    }

    public Result testGroq(String model, List<String> apiKeys) {
        return test(model, apiKeys, this::groqProvider);
    }

    public Result testGemini(String model, List<String> apiKeys) {
        return test(model, apiKeys, this::geminiProvider);
    }

    private Result test(String model, List<String> apiKeys, ProviderFactory factory) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return Result.failure("Kein Schluessel angegeben");
        }
        AiProvider provider;
        try {
            provider = factory.create(apiKeys);
        } catch (IOException e) {
            return Result.failure("Schluessel-Pool konnte nicht geladen werden: " + e.getMessage());
        }
        try {
            provider.chat(model, ChatRequest.builder().user(PING_PROMPT).build());
            return Result.success(provider.name(), model);
        } catch (AiProviderException e) {
            return Result.failure(e.getMessage());
        }
    }

    private AiProvider groqProvider(List<String> apiKeys) throws IOException {
        return OpenAiCompatibleProvider.groq(Revolver.builder(apiKeys).load(), transport);
    }

    private AiProvider geminiProvider(List<String> apiKeys) throws IOException {
        return new GoogleGeminiProvider(Revolver.builder(apiKeys).load(), transport);
    }

    @FunctionalInterface
    private interface ProviderFactory {
        AiProvider create(List<String> apiKeys) throws IOException;
    }
}
