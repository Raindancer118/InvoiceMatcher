package de.pegasus.invoicematcher.intake.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Holt das Postfach in festem Abstand ab.
 *
 * <p>Ein Fehler beendet den Durchlauf, nicht den Dienst: ein Mailserver, der gerade nicht
 * antwortet, darf die Anwendung nicht anhalten - beim naechsten Takt wird es erneut versucht.
 */
public class MailIntakeScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailIntakeScheduler.class);

    private final MailIntakeService intake;

    public MailIntakeScheduler(MailIntakeService intake) {
        this.intake = intake;
    }

    @Scheduled(fixedDelayString = "${invoicematcher.intake.mail.poll-interval:5m}",
            initialDelayString = "${invoicematcher.intake.mail.poll-interval:5m}")
    public void poll() {
        try {
            int bearbeitet = intake.poll();
            if (bearbeitet > 0) {
                log.info("Belegannahme per Mail: {} Nachricht(en) bearbeitet", bearbeitet);
            }
        } catch (RuntimeException e) {
            log.error("Belegannahme per Mail fehlgeschlagen: {}", e.getMessage());
        }
    }
}
