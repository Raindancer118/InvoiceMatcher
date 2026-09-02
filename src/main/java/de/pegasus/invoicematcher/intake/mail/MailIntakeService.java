package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Belegannahme aus dem Postfach: jeder brauchbare Anhang geht denselben Weg wie ein Upload.
 *
 * <p>Der Rueckgabewert je Nachricht steuert, was das Postfach mit ihr macht. Deshalb faellt
 * hier auch die Entscheidung, dass eine Mail ohne verwertbaren Anhang <em>nicht</em> als
 * erledigt gilt: sonst verschwindet eine Rueckfrage der Buchhaltung ungelesen, und ein Beleg
 * in einem unerwarteten Format ebenso.
 */
public class MailIntakeService {

    private static final Logger log = LoggerFactory.getLogger(MailIntakeService.class);

    private final Mailbox mailbox;
    private final ReviewService review;
    private final Supplier<MailIntakeProperties> properties;

    public MailIntakeService(Mailbox mailbox, ReviewService review, Supplier<MailIntakeProperties> properties) {
        this.mailbox = mailbox;
        this.review = review;
        this.properties = properties;
    }

    /** Ein Durchlauf ueber alle unbearbeiteten Nachrichten. */
    public int poll() {
        return mailbox.forEachUnread(this::handle);
    }

    MailResult handle(IncomingMail mail) {
        if (!properties.get().accepts(mail.sender())) {
            log.warn("Mail von '{}' abgewiesen: der Absender ist nicht zur Einlieferung zugelassen",
                    mail.sender());
            return MailResult.ABGEWIESEN;
        }
        if (!mail.hasAttachments()) {
            log.info("Mail von '{}' ohne verwertbaren Beleganhang (Betreff '{}')",
                    mail.sender(), mail.subject());
            return MailResult.OHNE_BELEG;
        }
        return submitAll(mail);
    }

    /**
     * Ein unlesbarer Anhang laesst die ganze Nachricht liegen - auch dann, wenn ein anderer
     * Anhang derselben Mail bereits durchgelaufen ist. Das kann bei einer erneuten Einlieferung
     * von Hand zu einem doppelten Vorgang fuehren; der Pruefablauf faengt das ab, ein
     * verschwundener Beleg dagegen bleibt unbemerkt.
     */
    private MailResult submitAll(IncomingMail mail) {
        MailResult result = MailResult.VERARBEITET;
        for (SourceDocument attachment : mail.attachments()) {
            try {
                ReviewCase reviewCase = review.submit(attachment);
                log.info("Beleg '{}' aus Mail von '{}' angenommen: Vorgang {} ({})",
                        attachment.filename(), mail.sender(), reviewCase.id(), reviewCase.status());
            } catch (RuntimeException e) {
                log.error("Beleg '{}' aus Mail von '{}' (Message-ID {}) konnte nicht verarbeitet werden: {}",
                        attachment.filename(), mail.sender(), mail.messageId(), e.toString());
                result = MailResult.FEHLGESCHLAGEN;
            }
        }
        return result;
    }
}
