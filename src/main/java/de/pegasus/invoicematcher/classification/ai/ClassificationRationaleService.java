package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.classification.ClassificationRationale;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Die zweite KI-Anfrage: sie erklaert die bereits getroffene Kontierung, ohne sie anzufassen.
 *
 * <p>Getrennt von der Klassifikation, weil beides sonst um dieselbe Antwort konkurriert - ein
 * Modell, das im selben Zug entscheidet und begruendet, schreibt die Begruendung auf die
 * Entscheidung zu und kuerzt beides. Zwei Aufrufe kosten mehr Token, liefern aber eine
 * Herleitung, die ein Pruefer tatsaechlich gegenlesen kann.
 *
 * <p>Jeder Fehlschlag endet in {@link Optional#empty()}: ohne Erlaeuterung ist der Beleg
 * weiterhin vollstaendig kontiert, die Pruefliste zeigt dann nur die Kurzbegruendung aus der
 * Klassifikation. Eine gescheiterte Erklaerung darf nie eine Buchung verhindern.
 */
@Component
public class ClassificationRationaleService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationRationaleService.class);

    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);
    private static final Pattern BARE_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final InvoiceClassifier classifier;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ClassificationRationaleService(InvoiceClassifier classifier) {
        this.classifier = classifier;
    }

    public Optional<ClassificationRationale> explain(ExtractedInvoice invoice, ClassificationResult result,
                                                     List<BufferedImage> pages) {
        Optional<InvoiceClassifier.Result> response;
        try {
            response = classifier.classify(RationalePrompt.build(invoice, result, pages));
        } catch (RuntimeException e) {
            log.warn("Erlaeuterung zur Kontierung fehlgeschlagen: {}", e.getMessage());
            return Optional.empty();
        }
        if (response.isEmpty()) {
            log.info("Erlaeuterung zur Kontierung nicht verfuegbar - Beleg bleibt ohne Gedankengang");
            return Optional.empty();
        }
        InvoiceClassifier.Result answer = response.get();
        return Optional.of(toRationale(answer.content(), answer.provider() + "/" + answer.model()));
    }

    /**
     * Freitext statt JSON ist hier kein Fehlerfall: die Antwort ist reine Erklaerung, und ein
     * unstrukturierter, aber lesbarer Absatz hilft dem Pruefer mehr als gar nichts. Nur die
     * Kontierung selbst haengt an einer festen Form - siehe {@link AiClassificationResponseParser}.
     */
    private ClassificationRationale toRationale(String rawText, String model) {
        String text = rawText == null ? "" : rawText.strip();
        Optional<JsonNode> json = parseJson(text);
        if (json.isEmpty()) {
            return new ClassificationRationale(model, text, List.of(), List.of(), null);
        }
        JsonNode root = json.get();
        return new ClassificationRationale(model, textOrNull(root, "kurzfassung"), steps(root),
                strings(root.path("unsicherheiten")), textOrNull(root, "gegenprobe"));
    }

    private Optional<JsonNode> parseJson(String text) {
        var fenced = CODE_FENCE.matcher(text);
        String candidate = fenced.find() ? fenced.group(1) : text;
        if (!candidate.startsWith("{")) {
            var bare = BARE_OBJECT.matcher(candidate);
            if (!bare.find()) {
                return Optional.empty();
            }
            candidate = bare.group();
        }
        try {
            JsonNode root = mapper.readTree(candidate);
            return root.isObject() ? Optional.of(root) : Optional.empty();
        } catch (RuntimeException e) {
            log.info("Erlaeuterung war kein gueltiges JSON, wird als Freitext uebernommen: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<ClassificationRationale.Schritt> steps(JsonNode root) {
        JsonNode array = root.path("schritte");
        if (!array.isArray()) {
            return List.of();
        }
        List<ClassificationRationale.Schritt> steps = new ArrayList<>();
        for (JsonNode node : array) {
            String text = textOrNull(node, "text");
            if (text == null || text.isBlank()) {
                continue;
            }
            steps.add(new ClassificationRationale.Schritt(textOrNull(node, "titel"), text));
        }
        return steps;
    }

    private List<String> strings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String value = node.isNull() ? null : node.asText();
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
