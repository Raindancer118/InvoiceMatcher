package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AiProperties;
import io.github.raindancer118.aigateway.AiGateway;
import io.github.raindancer118.aigateway.AiGatewayException;
import io.github.raindancer118.aigateway.AiProvider;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import io.github.raindancer118.aigateway.ChatResponse;
import io.github.raindancer118.aigateway.ModelTier;
import io.github.raindancer118.aigateway.Route;
import io.github.raindancer118.aigateway.http.HttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fragt die KI-Klassifikation ueber zwei Eskalationsstufen an: {@code fast} (Groq, schnell und
 * guenstig, ohne Bildeingabe) zuerst, {@code smart} (Gemini, vision-faehig, staerkeres Modell)
 * wenn die schnelle Stufe scheitert oder das Belegbild ein Bild enthaelt (Groq wuerde dort
 * ohnehin nur fehlschlagen).
 *
 * <p>Modell und Schluesselliste werden bei jeder Anfrage frisch aus {@code AiSettingsService}
 * gelesen (siehe {@link #settings}) - eine Aenderung in den Einstellungen wirkt sofort. Der
 * Schluessel-Pool selbst kommt dagegen aus {@link AiKeyPool} und ist langlebig: nur so bleibt
 * eine Kontingentsperre ueber den einzelnen Beleg hinaus bekannt.
 */
public class TieredInvoiceClassifier implements InvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(TieredInvoiceClassifier.class);

    private final Supplier<AiProperties> settings;
    private final AiKeyPool pool;

    public TieredInvoiceClassifier(Supplier<AiProperties> settings) {
        this(settings, new AiKeyPool());
    }

    /** Fuer Tests: eigener Transport, Pool ohne Zustandsablage. */
    public TieredInvoiceClassifier(Supplier<AiProperties> settings, HttpTransport transport) {
        this(settings, new AiKeyPool(transport, ZustandsAblage.ohne()));
    }

    public TieredInvoiceClassifier(Supplier<AiProperties> settings, AiKeyPool pool) {
        this.settings = settings;
        this.pool = pool;
    }

    /**
     * @return leer, wenn keine Stufe verfuegbar war oder beide Anfragen scheiterten - der
     * Aufrufer markiert den Beleg dann zur manuellen Pruefung statt ihn zu verwerfen.
     */
    @Override
    public Optional<Result> classify(ChatRequest request) {
        AiProperties properties = settings.get();
        boolean requiresVision = request.messages().stream().anyMatch(ChatMessage::hasImages);

        if (!requiresVision && !properties.fast().apiKeys().isEmpty()) {
            Optional<Result> fast = tryTier(properties.fast(), AiKeyPool.Stufe.FAST, request);
            if (fast.isPresent()) {
                return fast;
            }
            log.info("Schnelle KI-Klassifikation (Groq) fehlgeschlagen, versuche das starke Modell");
        }
        if (!properties.smart().apiKeys().isEmpty()) {
            return tryTier(properties.smart(), AiKeyPool.Stufe.SMART, request);
        }
        return Optional.empty();
    }

    private Optional<Result> tryTier(AiProperties.Tier tier, AiKeyPool.Stufe stufe, ChatRequest request) {
        AiProvider provider;
        try {
            provider = pool.provider(stufe, tier.apiKeys());
        } catch (IOException e) {
            log.warn("KI-Schluessel-Pool konnte nicht geladen werden: {}", e.getMessage());
            return Optional.empty();
        }
        AiGateway gateway = AiGateway.builder()
                .route(ModelTier.BALANCED, List.of(new Route(provider, tier.model())))
                .build();
        try {
            ChatResponse response = gateway.chat(ModelTier.BALANCED, request);
            return Optional.of(new Result(response.content(), response.provider(), response.model()));
        } catch (AiGatewayException e) {
            log.info("KI-Anfrage an {} fehlgeschlagen: {}", provider.name(), e.getMessage());
            return Optional.empty();
        }
    }

}
