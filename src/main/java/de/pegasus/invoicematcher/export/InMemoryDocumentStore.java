package de.pegasus.invoicematcher.export;

import de.pegasus.invoicematcher.extraction.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speicherbasierte Belegablage fuer den Prototyp.
 *
 * <p>Produktiv gehoert hier ein Objektspeicher oder ein revisionssicheres Archiv hinter -
 * der Port bleibt derselbe.
 */
@Component
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
