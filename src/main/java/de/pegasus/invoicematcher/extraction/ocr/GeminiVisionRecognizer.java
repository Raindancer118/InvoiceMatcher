package de.pegasus.invoicematcher.extraction.ocr;

import io.github.raindancer118.aigateway.AiProvider;
import io.github.raindancer118.aigateway.AiProviderException;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import io.github.raindancer118.aigateway.RetryableAiException;
import io.github.raindancer118.aigateway.http.HttpTransport;
import io.github.raindancer118.aigateway.http.JdkHttpTransport;
import io.github.raindancer118.aigateway.provider.GoogleGeminiProvider;
import io.github.raindancer118.revolver.Revolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Zeichenerkennung ueber Gemini-Vision - der Ausweichweg fuer Seiten, deren handschriftliche
 * Ueberschrift Tesseract nicht lesen konnte.
 *
 * <p>Bewusst nicht die primaere Erkennung: das Belegbild verlaesst dafuer den eigenen Server an
 * einen Cloud-Dienst. {@link FallbackOcrTextRecognizer} fragt diese Klasse deshalb nur an, wenn
 * die erste Erkennung keinen bekannten Belegtitel gefunden hat.
 *
 * <p>Modell und Schluessel-Pool werden bei jedem Aufruf frisch ueber {@link #config} gelesen
 * (typischerweise {@code AiSettingsService.current().smart()}) statt einmalig beim Start
 * gebunden zu werden - eine Aenderung in den Einstellungen wirkt sofort, ohne Neustart.
 */
public class GeminiVisionRecognizer implements OcrTextRecognizer {

    private static final Logger log = LoggerFactory.getLogger(GeminiVisionRecognizer.class);

    /** Bei Ratenlimit wird der naechste Schluessel aus dem Pool versucht. */
    private static final int MAX_ATTEMPTS = 3;

    private static final String PROMPT = """
            Lies den Text auf diesem Belegbild vollstaendig und wortgetreu, auch wenn er von Hand
            geschrieben ist. Gib ausschliesslich den erkannten Text zurueck, keine Erklaerung und
            keine Formatierung. Erhalte die Zeilenumbrueche des Originals. Stehen mehrere
            Textbloecke nebeneinander (z.B. Spalten oder Labels und Werte), trenne sie in derselben
            Zeile durch mindestens drei Leerzeichen, damit die Spalten erkennbar bleiben.
            """;

    /** Modell + Schluessel-Pool fuer einen Erkennungsversuch. */
    public record TierConfig(String model, List<String> apiKeys) {
        public TierConfig {
            apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
        }
    }

    private final Supplier<TierConfig> config;
    private final HttpTransport transport;

    public GeminiVisionRecognizer(Supplier<TierConfig> config) {
        this(config, new JdkHttpTransport());
    }

    public GeminiVisionRecognizer(Supplier<TierConfig> config, HttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    @Override
    public boolean isAvailable() {
        return !config.get().apiKeys().isEmpty();
    }

    @Override
    public String recognize(BufferedImage page) {
        TierConfig tier = config.get();
        if (tier.apiKeys().isEmpty()) {
            throw new OcrException("Gemini-Zeichenerkennung ohne konfigurierten Schluessel-Pool angefragt");
        }
        AiProvider provider = buildProvider(tier.apiKeys());
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.userWithImage(PROMPT, "image/png", encode(page)))
                .build();

        RetryableAiException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return provider.chat(tier.model(), request).content();
            } catch (RetryableAiException e) {
                lastFailure = e;
                log.info("Gemini-Schluessel nicht verfuegbar ({}), versuche den naechsten aus dem Pool",
                        e.getMessage());
            } catch (AiProviderException e) {
                throw new OcrException("Gemini-Zeichenerkennung fehlgeschlagen: " + e.getMessage(), e);
            }
        }
        throw new OcrException("Gemini-Zeichenerkennung: " + lastFailure.getMessage(), lastFailure);
    }

    private AiProvider buildProvider(List<String> apiKeys) {
        try {
            return new GoogleGeminiProvider(Revolver.builder(apiKeys).load(), transport);
        } catch (IOException e) {
            throw new OcrException("Gemini-Zeichenerkennung: Schluessel-Pool konnte nicht geladen werden", e);
        }
    }

    private byte[] encode(BufferedImage page) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(page, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "GeminiVisionRecognizer[%s]", config.get().model());
    }
}
