package de.pegasus.invoicematcher.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewStatus;

import java.time.Instant;
import java.util.List;

/**
 * Ein Pruefvorgang, wie ihn die Oberflaeche braucht: Ergebnis, Belegkontext und Verlauf in
 * einer flachen Struktur.
 */
public record ReviewCaseResponse(String id,
                                 ReviewStatus status,
                                 @JsonProperty("dateiname") String filename,
                                 @JsonProperty("quelle") ClassificationReport.Source source,
                                 @JsonProperty("ergebnis") ClassificationResult result,
                                 @JsonProperty("hinweise") List<Finding> findings,
                                 @JsonProperty("eingegangenAm") Instant submittedAt,
                                 @JsonProperty("entschiedenAm") Instant decidedAt,
                                 @JsonProperty("entschiedenVon") String decidedBy,
                                 @JsonProperty("entscheidungsnotiz") String decisionNote,
                                 @JsonProperty("exportiertAm") Instant exportedAt) {

    public static ReviewCaseResponse of(ReviewCase reviewCase) {
        ClassificationReport report = reviewCase.report();
        return new ReviewCaseResponse(reviewCase.id(), reviewCase.status(), report.filename(),
                report.source(), report.result(), report.findings(),
                reviewCase.submittedAt(), reviewCase.decidedAt(), reviewCase.decidedBy(),
                reviewCase.decisionNote(), reviewCase.exportedAt());
    }
}
