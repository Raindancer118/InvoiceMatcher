package de.pegasus.invoicematcher.export;

import de.pegasus.invoicematcher.extraction.SourceDocument;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speicherbasierte Belegablage.
 *
 * <p>Bewusst keine Spring-Bean: produktiv laeuft die SQLite-Ablage, diese Fassung dient den
 * Modultests.
 */
public class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, SourceDocument> documents = new ConcurrentHashMap<>();

    @Override
    public void store(String caseId, SourceDocument document) {
        documents.put(caseId, document);
    }

    @Override
    public Optional<SourceDocument> load(String caseId) {
        return Optional.ofNullable(documents.get(caseId));
    }
}
