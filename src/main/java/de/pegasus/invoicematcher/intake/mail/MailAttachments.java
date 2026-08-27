package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.extraction.SourceDocument;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zieht aus einer Nachricht die Anhaenge, die ein Beleg sein koennen.
 *
 * <p>Angenommen wird nur PDF und XML - dieselben beiden Formate, die auch ueber die API
 * hereinkommen. Alles andere an einer Belegmail ist Beiwerk: Signaturbilder, eingebettete
 * Logos, die Nachricht selbst.
 */
public class MailAttachments {

    private static final List<String> ACCEPTED_EXTENSIONS = List.of(".pdf", ".xml");
    private static final List<String> ACCEPTED_TYPES =
            List.of("application/pdf", "application/xml", "text/xml");

    private final long maxAttachmentBytes;

    public MailAttachments(long maxAttachmentBytes) {
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    public List<SourceDocument> from(Part message) {
        List<SourceDocument> documents = new ArrayList<>();
        collect(message, documents);
        return List.copyOf(documents);
    }

    private void collect(Part part, List<SourceDocument> documents) {
        try {
            Object content = part.isMimeType("multipart/*") ? part.getContent() : null;
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collect(multipart.getBodyPart(i), documents);
                }
                return;
            }
            if (isDocument(part)) {
                documents.add(read(part));
            }
        } catch (MessagingException | IOException e) {
            throw new MailIntakeException("Anhang konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    /**
     * Ein Anhang zaehlt ueber seinen Namen oder - wenn er keinen traegt - ueber den Inhaltstyp.
     * Der Name hat Vorrang, weil Mailprogramme Belege haeufig als
     * {@code application/octet-stream} verschicken.
     */
    private boolean isDocument(Part part) throws MessagingException {
        String filename = filenameOf(part);
        if (filename != null && hasAcceptedExtension(filename)) {
            return true;
        }
        if (filename != null) {
            return false;
        }
        return ACCEPTED_TYPES.contains(baseTypeOf(part));
    }

    private SourceDocument read(Part part) throws MessagingException, IOException {
        String filename = filenameOf(part);
        try (InputStream in = part.getInputStream()) {
            byte[] content = in.readNBytes(limit());
            if (content.length > maxAttachmentBytes) {
                throw new MailIntakeException("Anhang '" + (filename == null ? "unbenannt" : filename)
                        + "' ueberschreitet die zulaessige Groesse von " + maxAttachmentBytes + " Byte");
            }
            return new SourceDocument(filename, content);
        }
    }

    /** Ein Byte mehr als erlaubt, damit das Ueberschreiten ueberhaupt auffaellt. */
    private int limit() {
        long limit = maxAttachmentBytes + 1;
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }

    /** Mailprogramme kodieren Umlaute im Dateinamen (RFC 2047); ohne Dekodierung bleibt Kauderwelsch. */
    private String filenameOf(Part part) throws MessagingException {
        String filename = part.getFileName();
        if (filename == null || filename.isBlank()) {
            return null;
        }
        try {
            return MimeUtility.decodeText(filename);
        } catch (UnsupportedEncodingException e) {
            return filename;
        }
    }

    private static boolean hasAcceptedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return ACCEPTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String baseTypeOf(Part part) throws MessagingException {
        String contentType = part.getContentType();
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters))
                .trim().toLowerCase(Locale.ROOT);
    }
}
