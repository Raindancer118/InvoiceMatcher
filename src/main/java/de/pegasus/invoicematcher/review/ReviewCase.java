package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.classification.ClassificationReport;

import java.time.Instant;

/**
 * Ein Beleg auf dem Weg durch die Pruefung.
 *
 * <p>Unveraenderlich: jede Entscheidung erzeugt einen neuen Zustand. Das haelt den Verlauf
 * nachvollziehbar und schliesst nebenlaeufige Teilaenderungen aus.
 *
 * @param id           fachlicher Schluessel, wie ihn die Oberflaeche verwendet
 * @param report       Klassifikationsergebnis, ggf. durch eine Korrektur ersetzt
 * @param status       Bearbeitungsstand
 * @param submittedAt  Eingang des Belegs
 * @param decidedAt    Zeitpunkt der Entscheidung, {@code null} solange offen
 * @param decidedBy    wer entschieden hat
 * @param decisionNote Begruendung, insbesondere bei Ablehnung
 */
public record ReviewCase(String id,
                         ClassificationReport report,
                         ReviewStatus status,
                         Instant submittedAt,
                         Instant decidedAt,
                         String decidedBy,
                         String decisionNote) {

    public static ReviewCase opened(String id, ClassificationReport report, ReviewStatus status, Instant at) {
        return new ReviewCase(id, report, status, at, status.isDecided() ? at : null, null, null);
    }

    public ReviewCase decided(ReviewStatus newStatus, ClassificationReport newReport,
                              String by, String note, Instant at) {
        return new ReviewCase(id, newReport, newStatus, submittedAt, at, by, note);
    }
}
