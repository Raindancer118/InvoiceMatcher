package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.review.ReviewDecision;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Freigabe eines Pruefvorgangs, optional mit Korrektur.
 *
 * @param benutzer       wer freigibt - Pflichtangabe fuer die Nachvollziehbarkeit
 * @param belegtyp       korrigierter Belegtyp, sonst {@code null}
 * @param buchungszeilen korrigierte Buchungszeilen, sonst {@code null}
 */
public record ApproveRequest(@NotBlank String benutzer,
                             DocumentType belegtyp,
                             List<BookingLine> buchungszeilen) {

    public ReviewDecision.Correction toCorrection() {
        return belegtyp == null && buchungszeilen == null
                ? null
                : new ReviewDecision.Correction(belegtyp, buchungszeilen);
    }
}
