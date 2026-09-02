package de.pegasus.invoicematcher.intake.mail;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.support.Belege;
import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Belegannahme per Mail im vollstaendigen Anwendungskontext.
 *
 * <p>Der Ausschnitt-Test beweist die Fachlogik, dieser die Betriebsfaehigkeit: eingeschaltete
 * Annahme, gebundene Einstellungen, laufender Takt - und ein Beleg, der aus dem Postfach in
 * dieselbe Ablage laeuft wie ein Upload.
 */
@SpringBootTest(properties = {
        "invoicematcher.intake.mail.enabled=true",
        "invoicematcher.intake.mail.protocol=imap",
        "invoicematcher.intake.mail.host=127.0.0.1",
        "invoicematcher.intake.mail.username=belege@invoicematcher.test",
        "invoicematcher.intake.mail.password=geheim",
        "invoicematcher.intake.mail.allowed-senders[0]=@example.de",
        // Der Takt darf im Test nicht selbst zuschlagen; abgeholt wird von Hand.
        "invoicematcher.intake.mail.poll-interval=1h"
})
@ActiveProfiles("test")
class MailIntakeApplicationTest {

    private static final String POSTFACH = "belege@invoicematcher.test";

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.IMAP);

    @org.junit.jupiter.api.io.TempDir
    static Path ablage;

    @DynamicPropertySource
    static void umgebung(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + ablage.resolve("test.db"));
        // Der feste Port der Testeinstellung, nicht GREEN_MAIL.getImap(): der Kontext wird
        // gebunden, bevor der Mailserver hochgefahren ist.
        registry.add("invoicematcher.intake.mail.port", ServerSetupTest.IMAP::getPort);
    }

    private final MailIntakeScheduler scheduler;
    private final ReviewService review;

    @Autowired
    MailIntakeApplicationTest(MailIntakeScheduler scheduler, ReviewService review) {
        this.scheduler = scheduler;
        this.review = review;
    }

    @Test
    void aMailedInvoiceReachesTheSameReviewFlowAsAnUpload() throws Exception {
        GreenMailUser user = GREEN_MAIL.setUser(POSTFACH, POSTFACH, "geheim");
        user.deliver(rechnungsmail("lieferant@example.de"));

        scheduler.poll();

        assertThat(review.allCases()).extracting(reviewCase -> reviewCase.report().filename())
                .containsExactly("rechnung.pdf");
    }

    private MimeMessage rechnungsmail(String absender) throws Exception {
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Anbei unsere Rechnung.");
        MimeBodyPart anhang = new MimeBodyPart();
        anhang.setDataHandler(new DataHandler(new ByteArrayDataSource(
                Belege.bytes("08_ZUGFeRD_Saegewerk_Testtanne.pdf"), "application/pdf")));
        anhang.setFileName("rechnung.pdf");
        anhang.setDisposition(MimeBodyPart.ATTACHMENT);
        MimeMultipart body = new MimeMultipart();
        body.addBodyPart(text);
        body.addBodyPart(anhang);

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(absender));
        message.setRecipients(MimeMessage.RecipientType.TO, POSTFACH);
        message.setSubject("Rechnung 5007");
        message.setContent(body);
        message.saveChanges();
        return message;
    }
}
