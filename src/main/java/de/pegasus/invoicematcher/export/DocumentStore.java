package de.pegasus.invoicematcher.export;

import de.pegasus.invoicematcher.extraction.SourceDocument;

import java.util.Optional;

/**
 * Bewahrt das Belegbild zu einem Pruefvorgang auf.
 *
 * <p>Das Original wird gebraucht, weil die DATEV-Schnittstelle Belegbild und Buchungsdaten
 * gemeinsam uebertraegt - und weil die Aufbewahrungspflicht am Original haengt, nicht an den
 * daraus gelesenen Feldern.
 */
public interface DocumentStore {

    void store(String caseId, SourceDocument document);

    Optional<SourceDocument> load(String caseId);
}
