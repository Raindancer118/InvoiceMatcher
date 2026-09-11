package de.pegasus.invoicematcher.support;

import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import io.github.raindancer118.aigateway.ChatContent;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test-Double fuer {@link InvoiceClassifier}: liefert eine vorbereitete Modellantwort zurueck,
 * je nachdem, welche eindeutige Kennung (z.B. die Rechnungsnummer) im Prompt-Text vorkommt -
 * ohne echten API-Zugriff. Downstream-Tests (Export, Pruefablauf, Persistenz) pruefen damit ihr
 * eigenes Verhalten, nicht die fachliche Richtigkeit einer echten KI-Antwort.
 */
public final class FakeInvoiceClassifier implements InvoiceClassifier {

    private final Map<String, String> responsesByMarker = new LinkedHashMap<>();

    public FakeInvoiceClassifier respond(String marker, String json) {
        responsesByMarker.put(marker, json);
        return this;
    }

    @Override
    public Optional<Result> classify(ChatRequest request) {
        String text = textOf(request);
        return responsesByMarker.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()))
                .findFirst()
                .map(entry -> new Result(entry.getValue(), "fake", "fake-model"));
    }

    private static String textOf(ChatRequest request) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : request.messages()) {
            for (ChatContent part : message.parts()) {
                if (part instanceof ChatContent.Text t) {
                    text.append(t.text()).append('\n');
                }
            }
        }
        return text.toString();
    }
}
