package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.intake.mail.ImapMailbox;
import de.pegasus.invoicematcher.intake.mail.MailIntakeScheduler;
import de.pegasus.invoicematcher.intake.mail.MailIntakeService;
import de.pegasus.invoicematcher.intake.mail.Mailbox;
import de.pegasus.invoicematcher.review.ReviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Verdrahtung der Belegannahme per Mail.
 *
 * <p>Die Beans existieren jetzt immer - der An/Aus-Zustand lebt in {@link MailIntakeSettingsService}
 * (Einstellungen-Oberflaeche) statt in der Bean-Existenz selbst. Vorher entschied
 * {@code @ConditionalOnProperty}, ob ueberhaupt ein Postfach-Bean entsteht; das haette eine
 * spaetere Aktivierung ohne Neustart unmoeglich gemacht. {@link MailIntakeScheduler} prueft bei
 * jedem Takt selbst, ob die Annahme gerade aktiv ist.
 */
@Configuration
@EnableScheduling
public class MailIntakeConfiguration {

    @Bean
    public Mailbox mailbox(MailIntakeSettingsService settings) {
        return new ImapMailbox(settings);
    }

    @Bean
    public MailIntakeService mailIntakeService(Mailbox mailbox, ReviewService review,
                                               MailIntakeSettingsService settings) {
        return new MailIntakeService(mailbox, review, settings);
    }

    @Bean
    public MailIntakeScheduler mailIntakeScheduler(MailIntakeService intake, MailIntakeSettingsService settings) {
        return new MailIntakeScheduler(intake, settings);
    }
}
