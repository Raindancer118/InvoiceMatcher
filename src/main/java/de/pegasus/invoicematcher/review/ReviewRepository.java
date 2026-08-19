package de.pegasus.invoicematcher.review;

import java.util.List;
import java.util.Optional;

/**
 * Ablage der Pruefvorgaenge.
 *
 * <p>Als Port formuliert: der Prototyp haelt die Vorgaenge im Speicher, produktiv gehoert
 * hier eine Datenbank hinter, ohne dass der Ablauf sich aendert.
 */
public interface ReviewRepository {

    ReviewCase save(ReviewCase reviewCase);

    Optional<ReviewCase> findById(String id);

    List<ReviewCase> findAll();

    List<ReviewCase> findByStatus(ReviewStatus status);
}
