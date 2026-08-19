package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.DocumentType;

import java.util.List;

/** Die Eingaben, mit denen ein Mensch einen Pruefvorgang abschliesst. */
public final class ReviewDecision {

    private ReviewDecision() {
    }

    /**
     * Eine Korrektur des Vorschlags. Beide Felder sind optional - wer nur den Belegtyp
     * richtigstellt, muss die Buchungszeilen nicht erneut senden.
     */
    public record Correction(DocumentType documentType, List<BookingLine> buchungszeilen) {
    }
}
