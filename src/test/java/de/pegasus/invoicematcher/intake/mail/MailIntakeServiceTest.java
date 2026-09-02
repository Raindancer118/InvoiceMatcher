package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.review.ReviewStatus;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Belegannahme per Mail, ohne Mailserver: was mit einer Nachricht geschieht, haengt allein
 * am Ergebnis ihrer Verarbeitung.
 *
 * <p>Der Rueckgabewert je Mail ist die eigentliche Fachlogik dieser Stufe - er entscheidet, ob
 * eine Nachricht als erledigt abgelegt wird oder zur Ansicht liegen bleibt. Eine Mail, die
 * nichts hinterlaesst und trotzdem verschwindet, waere ein verlorener Beleg.
 */
class MailIntakeServiceTest {

    private final ReviewService review = Kontierung.standardWorkflow().review();
    private final FakeMailbox mailbox = new FakeMailbox();

    private MailIntakeService service(String... allowedSenders) {
        MailIntakeProperties properties = properties(List.of(allowedSenders));
        return new MailIntakeService(mailbox, review, () -> properties);
    }

    private static MailIntakeProperties properties(List<String> allowedSenders) {
        return new MailIntakeProperties(true, null, "mail.example", null, "belege", "geheim",
                null, null, null, null, 0, null, allowedSenders);
    }

    @Test
    void everyAttachmentOfAMailBecomesItsOwnReviewCase() {
        mailbox.add(mail("lieferant@example.de",
                Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf"),
                Belege.document("05_PDF_Buerobedarf_Kopierer.pdf")));

        int gelesen = service().poll();

        assertThat(gelesen).isEqualTo(1);
        assertThat(review.allCases()).hasSize(2)
                .extracting(reviewCase -> reviewCase.report().filename())
                .containsExactlyInAnyOrder("08_ZUGFeRD_Saegewerk_Testtanne.pdf",
                        "05_PDF_Buerobedarf_Kopierer.pdf");
        assertThat(mailbox.results()).containsExactly(Map.entry("1", MailResult.VERARBEITET));
    }

    /** Der Weg durch die Mail endet dort, wo er auch beim Upload endet: im Pruefablauf. */
    @Test
    void aFlaggedDocumentFromAMailWaitsForAHumanJustLikeAnUpload() {
        mailbox.add(mail("lieferant@example.de", Belege.document("05_PDF_Buerobedarf_Kopierer.pdf")));

        service().poll();

        assertThat(review.openCases()).extracting(ReviewCase::status).containsExactly(ReviewStatus.OFFEN);
    }

    @Test
    void aMailWithoutAnyDocumentIsSetAsideInsteadOfCountingAsProcessed() {
        mailbox.add(mail("kollege@example.de"));

        service().poll();

        assertThat(review.allCases()).isEmpty();
        assertThat(mailbox.results()).containsExactly(Map.entry("1", MailResult.OHNE_BELEG));
    }

    /**
     * Wer an das Postfach schreiben darf, bucht in der Buchhaltung mit. Ist eine Liste
     * hinterlegt, kommt niemand sonst hinein.
     */
    @Test
    void anUnknownSenderIsRejectedWhenAnAllowListIsConfigured() {
        mailbox.add(mail("fremder@spam.example", Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf")));

        service("@example.de", "buchhaltung@partner.example").poll();

        assertThat(review.allCases()).isEmpty();
        assertThat(mailbox.results()).containsExactly(Map.entry("1", MailResult.ABGEWIESEN));
    }

    @Test
    void anAllowedDomainPassesRegardlessOfTheLocalPart() {
        mailbox.add(mail("Rechnungen <buchhaltung@example.de>",
                Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf")));

        service("@example.de").poll();

        assertThat(review.allCases()).hasSize(1);
        assertThat(mailbox.results()).containsExactly(Map.entry("1", MailResult.VERARBEITET));
    }

    /**
     * Ein unlesbarer Anhang darf die uebrigen Mails nicht aufhalten - und die betroffene
     * Nachricht muss auffindbar bleiben.
     */
    @Test
    void oneUnreadableDocumentDoesNotStopTheRemainingMails() {
        mailbox.add(mail("lieferant@example.de", new SourceDocument("kaputt.pdf", "kein PDF".getBytes())));
        mailbox.add(mail("lieferant@example.de", Belege.document("08_ZUGFeRD_Saegewerk_Testtanne.pdf")));

        int gelesen = service().poll();

        assertThat(gelesen).isEqualTo(2);
        assertThat(review.allCases()).hasSize(1);
        assertThat(mailbox.results()).containsExactly(
                Map.entry("1", MailResult.FEHLGESCHLAGEN),
                Map.entry("2", MailResult.VERARBEITET));
    }

    private IncomingMail mail(String sender, SourceDocument... documents) {
        return new IncomingMail(String.valueOf(mailbox.mails.size() + 1), sender,
                "Rechnung", Instant.now(), List.of(documents));
    }

    /** Ein Postfach, das sich merkt, wie ueber jede Nachricht entschieden wurde. */
    private static final class FakeMailbox implements Mailbox {

        private final List<IncomingMail> mails = new ArrayList<>();
        private final Map<String, MailResult> results = new LinkedHashMap<>();

        void add(IncomingMail mail) {
            mails.add(mail);
        }

        List<Map.Entry<String, MailResult>> results() {
            return List.copyOf(results.entrySet());
        }

        @Override
        public int forEachUnread(Function<IncomingMail, MailResult> handler) {
            mails.forEach(mail -> results.put(mail.messageId(), handler.apply(mail)));
            return mails.size();
        }
    }
}
