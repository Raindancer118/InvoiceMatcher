package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;

/**
 * Extrahiert genau ein Feld aus dem Belegtext.
 *
 * <p>Jede Heuristik lebt in einer eigenen, einzeln testbaren Implementierung. Ein leeres
 * Ergebnis bedeutet "auf dem Beleg nicht auffindbar" und ist ein fachlich relevanter Zustand -
 * das Regelwerk leitet daraus fehlende Pflichtangaben ab.
 */
@FunctionalInterface
public interface TextFieldExtractor<T> {

    Optional<T> extract(DocumentText text);

    default String fieldName() {
        return getClass().getSimpleName().replace("Extractor", "");
    }
}
