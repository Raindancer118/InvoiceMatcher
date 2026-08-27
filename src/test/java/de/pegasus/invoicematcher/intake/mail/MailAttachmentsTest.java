package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.support.Belege;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Was aus einer Mail ueberhaupt ein Beleg ist.
 *
 * <p>Eine Belegmail besteht selten nur aus dem Beleg: Signaturbilder, Logos im Text und die
 * Nachricht selbst haengen mit daran. Wer alles annimmt, legt Signaturbilder als Belege ab.
 */
class MailAttachmentsTest {

    private static final Session SESSION = Session.getInstance(new Properties());

    private final MailAttachments attachments = new MailAttachments(20L * 1024 * 1024);

    @Test
    void onlyPdfAndXmlAttachmentsBecomeDocuments() throws Exception {
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(textPart("Guten Tag, anbei die Rechnung."));
        body.addBodyPart(attachment("rechnung.pdf", "application/pdf",
                Belege.bytes("01_PDF_Klaus_Furniere.pdf")));
        body.addBodyPart(attachment("xrechnung.xml", "application/xml",
                Belege.bytes("13_XRechnung_Spanexempel_gemischt.xml")));
        body.addBodyPart(attachment("signatur.png", "image/png", new byte[] {1, 2, 3}));

        List<SourceDocument> found = attachments.from(message(body));

        assertThat(found).extracting(SourceDocument::filename)
                .containsExactly("rechnung.pdf", "xrechnung.xml");
    }

    @Test
    void attachmentsInsideNestedPartsAreFoundToo() throws Exception {
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(textPart("Anbei die Rechnung."));
        alternative.addBodyPart(htmlPart("<p>Anbei die Rechnung.</p>"));
        MimeBodyPart alternativePart = new MimeBodyPart();
        alternativePart.setContent(alternative);

        MimeMultipart mixed = new MimeMultipart();
        mixed.addBodyPart(alternativePart);
        mixed.addBodyPart(attachment("rechnung.pdf", "application/pdf",
                Belege.bytes("01_PDF_Klaus_Furniere.pdf")));

        assertThat(attachments.from(message(mixed))).hasSize(1);
    }

    @Test
    void aMailWithoutAttachmentsYieldsNothing() throws Exception {
        MimeMessage message = new MimeMessage(SESSION);
        message.setText("Nur eine Nachfrage, kein Beleg.");

        assertThat(attachments.from(message)).isEmpty();
    }

    /**
     * Mailprogramme kodieren Umlaute im Dateinamen. Ohne Dekodierung landet der Beleg als
     * {@code =?UTF-8?Q?...?=} in der Ablage und ist ueber den Namen nicht mehr auffindbar.
     */
    @Test
    void encodedFilenamesAreDecoded() throws Exception {
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(attachment("Rechnung_Grün_März.pdf", "application/pdf",
                Belege.bytes("01_PDF_Klaus_Furniere.pdf")));

        assertThat(attachments.from(message(body)))
                .extracting(SourceDocument::filename)
                .containsExactly("Rechnung_Grün_März.pdf");
    }

    /**
     * Eine Groessengrenze, die stillschweigend ueberspringt, verliert Belege. Der Anhang
     * fliegt deshalb als Fehler auf und die Mail bleibt zur Ansicht liegen.
     */
    @Test
    void anOversizedAttachmentIsRefusedInsteadOfSilentlySkipped() throws Exception {
        MailAttachments eng = new MailAttachments(1024);
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(attachment("gross.pdf", "application/pdf", new byte[4096]));

        assertThatThrownBy(() -> eng.from(message(body)))
                .isInstanceOf(MailIntakeException.class)
                .hasMessageContaining("gross.pdf");
    }

    /** Ein Beleg ohne Endung im Namen: der Inhaltstyp entscheidet. */
    @Test
    void anAttachmentWithoutAFilenameIsRecognizedByItsContentType() throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(
                Belege.bytes("01_PDF_Klaus_Furniere.pdf"), "application/pdf")));
        part.setDisposition(MimeBodyPart.ATTACHMENT);
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(part);

        assertThat(attachments.from(message(body))).hasSize(1);
    }

    private static Message message(MimeMultipart body) throws Exception {
        MimeMessage message = new MimeMessage(SESSION);
        message.setContent(body);
        message.saveChanges();
        return message;
    }

    private static MimeBodyPart textPart(String text) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(text);
        return part;
    }

    private static MimeBodyPart htmlPart(String html) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(html, "text/html; charset=UTF-8");
        return part;
    }

    private static MimeBodyPart attachment(String filename, String contentType, byte[] content)
            throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(content, contentType)));
        part.setFileName(jakarta.mail.internet.MimeUtility.encodeText(filename, StandardCharsets.UTF_8.name(), null));
        part.setDisposition(MimeBodyPart.ATTACHMENT);
        return part;
    }
}
