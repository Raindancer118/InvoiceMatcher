package de.pegasus.invoicematcher.intake.mail;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import jakarta.activation.DataHandler;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Gegenprobe gegen einen echten IMAP-Server: die Nachricht wird zugestellt, abgeholt und
 * danach umgelegt.
 *
 * <p>Das Umlegen ist der Teil, den kein Attrappen-Postfach beweisen kann. Es ist zugleich der
 * Schutz gegen die Doppelbuchung: bleibt die Mail im Eingang liegen, wandert derselbe Beleg
 * beim naechsten Durchlauf ein zweites Mal in die Buchhaltung.
 */
class ImapMailIntakeTest {

    private static final String POSTFACH = "belege@invoicematcher.test";
    private static final String PASSWORT = "geheim";

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.IMAP);

    private ReviewService review;
    private MailIntakeService service;
    private GreenMailUser user;

    @BeforeEach
    void setUp() {
        user = GREEN_MAIL.setUser(POSTFACH, POSTFACH, PASSWORT);
        review = Kontierung.standardWorkflow().review();
        MailIntakeProperties properties = new MailIntakeProperties(true, "imap", "127.0.0.1",
                GREEN_MAIL.getImap().getPort(), POSTFACH, PASSWORT,
                "INBOX", "Verarbeitet", "Fehlgeschlagen", null, 0, null, List.of());
        service = new MailIntakeService(new ImapMailbox(properties), review, properties);
    }

    @Test
    void aMailedInvoiceEndsUpInTheReviewFlowAndTheMailIsFiledAway() throws Exception {
        deliver(withAttachment("rechnung.pdf", Belege.bytes("08_ZUGFeRD_Saegewerk_Testtanne.pdf")));

        assertThat(service.poll()).isEqualTo(1);

        assertThat(review.allCases()).extracting(reviewCase -> reviewCase.report().filename())
                .containsExactly("rechnung.pdf");
        assertThat(messageCount("INBOX")).isZero();
        assertThat(messageCount("Verarbeitet")).isEqualTo(1);
    }

    /** Zweiter Durchlauf, derselbe Beleg: die Mail liegt nicht mehr im Eingang. */
    @Test
    void anAlreadyProcessedMailIsNotPickedUpAgain() throws Exception {
        deliver(withAttachment("rechnung.pdf", Belege.bytes("08_ZUGFeRD_Saegewerk_Testtanne.pdf")));
        service.poll();

        assertThat(service.poll()).isZero();
        assertThat(review.allCases()).hasSize(1);
    }

    @Test
    void aMailWithoutADocumentStaysVisibleInTheFailedFolder() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("kollege@example.de"));
        message.setSubject("Kurze Rueckfrage");
        message.setText("Ist die Rechnung vom Dienstag schon gebucht?");
        deliver(message);

        service.poll();

        assertThat(review.allCases()).isEmpty();
        assertThat(messageCount("INBOX")).isZero();
        assertThat(messageCount("Fehlgeschlagen")).isEqualTo(1);
    }

    private void deliver(MimeMessage message) throws Exception {
        user.deliver(message);
    }

    private MimeMessage withAttachment(String filename, byte[] content) throws Exception {
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Guten Tag, anbei unsere Rechnung.");
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(content, "application/pdf")));
        attachment.setFileName(filename);
        attachment.setDisposition(MimeBodyPart.ATTACHMENT);
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(text);
        body.addBodyPart(attachment);

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("lieferant@example.de"));
        message.setRecipients(MimeMessage.RecipientType.TO, POSTFACH);
        message.setSubject("Rechnung 2026-0815");
        message.setContent(body);
        message.saveChanges();
        return message;
    }

    private int messageCount(String folderName) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        try (Store store = session.getStore("imap")) {
            store.connect("127.0.0.1", GREEN_MAIL.getImap().getPort(), POSTFACH, PASSWORT);
            Folder folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return 0;
            }
            folder.open(Folder.READ_ONLY);
            try {
                return folder.getMessageCount();
            } finally {
                folder.close(false);
            }
        }
    }
}
