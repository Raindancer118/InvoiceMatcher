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
 * @param exportedAt   Zeitpunkt der Uebergabe an DATEV; verhindert eine zweite Buchung
 */
public record ReviewCase(String id,
                         ClassificationReport report,
                         ReviewStatus status,
                         Instant submittedAt,
                         Instant decidedAt,
                         String decidedBy,
                         String decisionNote,
                         Instant exportedAt) {

    public static ReviewCase opened(String id, ClassificationReport report, ReviewStatus status, Instant at) {
        return new ReviewCase(id, report, status, at, status.isDecided() ? at : null, null, null, null);
    }

    /** Die Klassifikation ist durch: Bericht und Zustand treten an die Stelle des Platzhalters. */
    public ReviewCase classified(ClassificationReport newReport, ReviewStatus newStatus, Instant at) {
        return new ReviewCase(id, newReport, newStatus, submittedAt,
                newStatus.isDecided() ? at : null, decidedBy, decisionNote, exportedAt);
    }

    public ReviewCase decided(ReviewStatus newStatus, ClassificationReport newReport,
                              String by, String note, Instant at) {
        return new ReviewCase(id, newReport, newStatus, submittedAt, at, by, note, exportedAt);
    }

    public ReviewCase exported(Instant at) {
        return new ReviewCase(id, report, status, submittedAt, decidedAt, decidedBy, decisionNote, at);
    }

    /** Freigegeben und noch nicht uebergeben - nur solche Belege gehoeren in ein Paket. */
    public boolean isExportable() {
        return exportedAt == null
                && (status == ReviewStatus.FREIGEGEBEN || status == ReviewStatus.AUTOMATISCH_FREIGEGEBEN);
    }
}
