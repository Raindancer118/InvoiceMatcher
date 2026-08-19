package de.pegasus.invoicematcher.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Standardbenachrichtigung: der Vorgang wird protokolliert.
 *
 * <p>Bewusst der Vorgabewert - der Pruefablauf funktioniert ohne Mailserver vollstaendig, und
 * eine fehlende Mailkonfiguration darf keinen Beleg verschlucken.
 */
@Component
@ConditionalOnProperty(name = "invoicematcher.review.notification.enabled", havingValue = "false",
        matchIfMissing = true)
public class LoggingReviewNotifier implements ReviewNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingReviewNotifier.class);

    @Override
    public void notifyPending(ReviewCase reviewCase) {
        log.info("Pruefung erforderlich: Beleg '{}' (Vorgang {}), Belegtyp {}, Konfidenz {}",
                reviewCase.report().filename(), reviewCase.id(),
                reviewCase.report().result().documentType().label(),
                reviewCase.report().result().konfidenzScore());
    }
}
