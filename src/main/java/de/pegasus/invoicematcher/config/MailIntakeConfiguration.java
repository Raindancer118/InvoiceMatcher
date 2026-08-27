package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.intake.mail.ImapMailbox;
import de.pegasus.invoicematcher.intake.mail.MailIntakeScheduler;
import de.pegasus.invoicematcher.intake.mail.MailIntakeService;
import de.pegasus.invoicematcher.intake.mail.Mailbox;
import de.pegasus.invoicematcher.review.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Die Belegannahme per Mail haengt vollstaendig an einem Schalter.
 *
 * <p>Ohne {@code invoicematcher.intake.mail.enabled=true} entsteht kein Bean, kein Takt und
 * keine Verbindung - eine Anwendung ohne Postfach soll nicht alle fuenf Minuten einen
 * Verbindungsfehler protokollieren.
 */
@Configuration
@ConditionalOnProperty(name = "invoicematcher.intake.mail.enabled", havingValue = "true")
@EnableScheduling
public class MailIntakeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MailIntakeConfiguration.class);

    @Bean
    public Mailbox mailbox(MailIntakeProperties properties) {
        ImapMailbox mailbox = new ImapMailbox(properties);
        log.info("Belegannahme per Mail aktiv: {}, Takt {}", mailbox.describe(), properties.pollInterval());
        if (properties.allowedSenders().isEmpty()) {
            log.warn("Fuer die Belegannahme per Mail ist keine Absenderliste hinterlegt -"
                    + " jeder Absender kann Belege in die Buchhaltung einliefern"
                    + " (invoicematcher.intake.mail.allowed-senders)");
        }
        return mailbox;
    }

    @Bean
    public MailIntakeService mailIntakeService(Mailbox mailbox, ReviewService review,
                                               MailIntakeProperties properties) {
        return new MailIntakeService(mailbox, review, properties);
    }

    @Bean
    public MailIntakeScheduler mailIntakeScheduler(MailIntakeService intake) {
        return new MailIntakeScheduler(intake);
    }
}
