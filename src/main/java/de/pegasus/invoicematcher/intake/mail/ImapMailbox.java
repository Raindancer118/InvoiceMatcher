package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Das Postfach ueber IMAP.
 *
 * <p>Eine bearbeitete Nachricht wird umgelegt, nicht nur als gelesen markiert: der Ordner ist
 * die Ablage, in der jemand nachsehen kann, was die Annahme gemacht hat. Was nicht durchlief,
 * landet in einem eigenen Ordner - sichtbar statt still verworfen. Geloescht wird nichts.
 *
 * <p>Verbindung, Abholung und Ablage liegen in einem einzigen {@link #forEachUnread}-Aufruf.
 * Waere das getrennt, muesste zwischen Abholen und Umlegen eine Nachrichtenkennung ueber eine
 * neue Verbindung wiedergefunden werden - IMAP garantiert das nur innerhalb einer Sitzung.
 *
 * <p>Die Einstellungen kommen ueber einen Lieferanten statt eines festen Objekts: sie sind
 * ueber die Einstellungen-Oberflaeche jederzeit aenderbar, eine Verbindung baut deshalb bei
 * jedem Durchlauf mit den dann aktuellen Werten auf.
 */
public class ImapMailbox implements Mailbox {

    private static final Logger log = LoggerFactory.getLogger(ImapMailbox.class);

    private final Supplier<MailIntakeProperties> properties;

    public ImapMailbox(Supplier<MailIntakeProperties> properties) {
        this.properties = properties;
    }

    @Override
    public int forEachUnread(Function<IncomingMail, MailResult> handler) {
        MailIntakeProperties properties = this.properties.get();
        MailAttachments attachments = new MailAttachments(properties.maxAttachmentSize().toBytes());
        try (Store store = session(properties).getStore(properties.protocol())) {
            store.connect(properties.host(), properties.port(), properties.username(), properties.password());
            Folder inbox = store.getFolder(properties.folder());
            if (!inbox.exists()) {
                throw new MailIntakeException("Ordner '" + properties.folder() + "' gibt es im Postfach nicht");
            }
            inbox.open(Folder.READ_WRITE);
            try {
                return handleAll(properties, attachments, store, inbox, handler);
            } finally {
                // expunge: die umgelegten Nachrichten verlassen den Eingang erst hier.
                inbox.close(true);
            }
        } catch (MessagingException e) {
            throw new MailIntakeException("Postfach " + properties.username() + "@" + properties.host()
                    + " konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    private int handleAll(MailIntakeProperties properties, MailAttachments attachments, Store store, Folder inbox,
                          Function<IncomingMail, MailResult> handler) throws MessagingException {
        Message[] unread = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        int count = Math.min(unread.length, properties.maxMessagesPerPoll());
        if (unread.length > count) {
            log.info("{} unbearbeitete Nachrichten im Postfach, {} werden in diesem Durchlauf geholt",
                    unread.length, count);
        }
        for (int i = 0; i < count; i++) {
            Message message = unread[i];
            file(properties, store, inbox, message, resultFor(attachments, message, handler));
        }
        return count;
    }

    /**
     * Ein Anhang, der sich nicht einmal lesen laesst, kommt gar nicht erst beim Bearbeiter an -
     * die Nachricht gilt dann als fehlgeschlagen und bleibt sichtbar.
     */
    private MailResult resultFor(MailAttachments attachments, Message message,
                                 Function<IncomingMail, MailResult> handler) {
        try {
            return handler.apply(toIncomingMail(attachments, message));
        } catch (MailIntakeException | MessagingException e) {
            log.error("Nachricht konnte nicht gelesen werden: {}", e.getMessage());
            return MailResult.FEHLGESCHLAGEN;
        }
    }

    private IncomingMail toIncomingMail(MailAttachments attachments, Message message) throws MessagingException {
        String messageId = message instanceof MimeMessage mime && mime.getMessageID() != null
                ? mime.getMessageID()
                : "ohne-message-id-" + message.getMessageNumber();
        Instant receivedAt = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : Instant.now();
        return new IncomingMail(messageId, senderOf(message), message.getSubject(), receivedAt,
                attachments.from(message));
    }

    private static String senderOf(Message message) throws MessagingException {
        var from = message.getFrom();
        return from == null || from.length == 0 ? "" : from[0].toString();
    }

    /**
     * Umlegen statt loeschen. Schlaegt das Umlegen fehl, bleibt die Nachricht als gelesen im
     * Eingang: sie ein zweites Mal zu verarbeiten waere schlimmer als sie am falschen Ort zu
     * haben.
     */
    private void file(MailIntakeProperties properties, Store store, Folder inbox, Message message,
                      MailResult result) {
        String targetName = result.isProcessed() ? properties.processedFolder() : properties.failedFolder();
        try {
            message.setFlag(Flags.Flag.SEEN, true);
            if (targetName == null || targetName.isBlank()) {
                return;
            }
            Folder target = store.getFolder(targetName);
            if (!target.exists() && !target.create(Folder.HOLDS_MESSAGES)) {
                throw new MessagingException("Ordner '" + targetName + "' konnte nicht angelegt werden");
            }
            inbox.copyMessages(new Message[] {message}, target);
            message.setFlag(Flags.Flag.DELETED, true);
        } catch (MessagingException e) {
            log.error("Nachricht konnte nicht nach '{}' umgelegt werden ({}); sie bleibt als gelesen"
                    + " im Eingang liegen", targetName, e.getMessage());
        }
    }

    private Session session(MailIntakeProperties properties) {
        Properties config = new Properties();
        config.put("mail.store.protocol", properties.protocol());
        config.put("mail." + properties.protocol() + ".host", properties.host());
        config.put("mail." + properties.protocol() + ".port", String.valueOf(properties.port()));
        config.put("mail." + properties.protocol() + ".connectiontimeout", "20000");
        config.put("mail." + properties.protocol() + ".timeout", "60000");
        if ("imap".equals(properties.protocol())) {
            // Klartext-IMAP nur mit STARTTLS, wo der Server es anbietet - im Testbetrieb nicht.
            config.put("mail.imap.starttls.enable", "true");
        }
        config.put("mail.mime.decodefilename", "true");
        return Session.getInstance(config);
    }

    /** Nur zur Protokollspur, immer mit den aktuell gueltigen Einstellungen. */
    public String describe() {
        MailIntakeProperties properties = this.properties.get();
        return properties.username() + "@" + properties.host() + ":" + properties.port()
                + " (" + properties.protocol() + ", Ordner " + properties.folder() + ")";
    }
}
