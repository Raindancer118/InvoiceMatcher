package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.ReviewProperties;

import java.math.BigDecimal;

/**
 * Die Steuerung des Human-in-the-Loop fuer die Oberflaeche.
 *
 * @param benachrichtigungAktiv nur zur Anzeige - die Mailbenachrichtigung haengt am Start der
 *                              Anwendung und laesst sich hier nicht umschalten
 */
public record PruefungResponse(BigDecimal freigabeschwelle, boolean benachrichtigungAktiv) {

    public static PruefungResponse of(ReviewProperties review) {
        return new PruefungResponse(review.autoApproveThreshold(),
                review.notification() != null && review.notification().enabled());
    }
}
