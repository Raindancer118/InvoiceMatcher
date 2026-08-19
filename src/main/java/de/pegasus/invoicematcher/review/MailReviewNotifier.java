package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.config.ReviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Benachrichtigt die Buchhaltung per Mail ueber einen offenen Pruefvorgang.
 *
 * <p>Ein Zustellfehler wird protokolliert, aber nicht weitergereicht: der Beleg liegt bereits
 * in der Pruefliste, und ein Mailproblem darf die Belegannahme nicht scheitern lassen.
 */
@Component
@ConditionalOnProperty(name = "invoicematcher.review.notification.enabled", havingValue = "true")
public class MailReviewNotifier implements ReviewNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailReviewNotifier.class);

    private final JavaMailSender mailSender;
    private final ReviewProperties properties;

    public MailReviewNotifier(JavaMailSender mailSender, ReviewProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void notifyPending(ReviewCase reviewCase) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.notification().from());
        message.setTo(properties.notification().to());
        message.setSubject("Beleg zur Pruefung: " + reviewCase.report().filename());
        message.setText(body(reviewCase));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Pruefbenachrichtigung fuer Vorgang {} konnte nicht versendet werden: {}",
                    reviewCase.id(), e.getMessage());
        }
    }

    private String body(ReviewCase reviewCase) {
        var result = reviewCase.report().result();
        String hinweise = reviewCase.report().findings().stream()
                .map(finding -> "  [" + finding.ruleId() + "] " + finding.message())
                .collect(Collectors.joining(System.lineSeparator()));
        return """
                Ein Beleg benoetigt eine manuelle Pruefung.

                Vorgang:    %s
                Datei:      %s
                Belegtyp:   %s
                Belegdatum: %s
                Konfidenz:  %s

                Hinweise:
                %s
                """.formatted(reviewCase.id(), reviewCase.report().filename(),
                result.documentType().label(),
                result.belegdatum() == null ? "unbekannt" : result.belegdatum(),
                result.konfidenzScore(),
                hinweise.isBlank() ? "  keine" : hinweise);
    }
}
