package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.domain.ClassificationResult;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Steuerung des Human-in-the-Loop.
 *
 * @param autoApproveThreshold Konfidenz, ab der ein Beleg ohne Pruefung weiterlaeuft
 * @param notification         Benachrichtigung der Buchhaltung
 */
@ConfigurationProperties("invoicematcher.review")
public record ReviewProperties(BigDecimal autoApproveThreshold, Notification notification) {

    /**
     * @param enabled ausgeschaltet laeuft der Ablauf vollstaendig, es wird nur nicht gemailt
     */
    public record Notification(boolean enabled, String from, String to) {
    }

    /** Zur Pruefung geht, was das Regelwerk bemaengelt hat oder was zu unsicher ist. */
    public boolean needsReview(ClassificationResult result) {
        if (result.documentType().isReview()) {
            return true;
        }
        if (!result.documentType().carriesBookingData()) {
            return false;
        }
        return autoApproveThreshold != null
                && result.konfidenzScore().compareTo(autoApproveThreshold) < 0;
    }
}
