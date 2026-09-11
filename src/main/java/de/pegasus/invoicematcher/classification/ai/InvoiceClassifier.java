package de.pegasus.invoicematcher.classification.ai;

import io.github.raindancer118.aigateway.ChatRequest;

import java.util.Optional;

/**
 * Fragt eine KI nach der Klassifikation eines Belegs. Eigenes, schmales Interface statt direkter
 * Abhaengigkeit auf {@link TieredInvoiceClassifier}, damit Tests ausserhalb der KI-Anbindung
 * (Export, Pruefablauf, Persistenz) eine kontrollierte Antwort vorgeben koennen, ohne echte
 * API-Schluessel zu brauchen.
 */
public interface InvoiceClassifier {

    /** Die Modellantwort samt Herkunft, fuer ein nachvollziehbares Finding in der Pruefliste. */
    record Result(String content, String provider, String model) {
    }

    /**
     * @return leer, wenn keine Stufe verfuegbar war oder beide Anfragen scheiterten - der
     * Aufrufer markiert den Beleg dann zur manuellen Pruefung statt ihn zu verwerfen.
     */
    Optional<Result> classify(ChatRequest request);
}
