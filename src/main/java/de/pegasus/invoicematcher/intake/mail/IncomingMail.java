package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.extraction.SourceDocument;

import java.time.Instant;
import java.util.List;

/**
 * Eine eingegangene Nachricht, reduziert auf das, was die Belegannahme braucht.
 *
 * @param messageId  Message-ID der Mail, sonst eine Ersatzkennung - dient nur der Protokollspur
 * @param sender     Absender, wie er in der Mail steht (ggf. mit Anzeigename)
 * @param attachments die als Beleg in Frage kommenden Anhaenge
 */
public record IncomingMail(String messageId, String sender, String subject, Instant receivedAt,
                           List<SourceDocument> attachments) {

    public IncomingMail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
