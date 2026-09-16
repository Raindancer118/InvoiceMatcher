package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RationalePromptTest {

    private final ExtractedInvoice invoice = ExtractedInvoice.builder(SourceFormat.PDF)
            .documentTitle("Rechnung")
            .invoiceNumber("5007")
            .seller(new Party("Friedrich Klausmuster", "DE 404131066", "156/290/44989"))
            .buyer(new Party("Muster zu Testzwecken GmbH", null, null))
            .totals(new MonetaryTotals(new BigDecimal("404.00"), new BigDecimal("76.76"), new BigDecimal("480.76")))
            .rawText("Rechnung\nRechnungsnummer 5007")
            .build();

    private final ClassificationResult result = new ClassificationResult(DocumentType.RECHNUNGSEINGANG,
            "04012026", "5007", 70101, List.of(), null, new BigDecimal("0.95"));

    @Test
    void showsTheBelegAndTheDecisionThatIsToBeExplained() {
        String text = onlyMessage(RationalePrompt.build(invoice, result, List.of())).content();

        assertThat(text).contains("Rechnungsnummer 5007");
        assertThat(text).contains("Friedrich Klausmuster");
        assertThat(text).contains("Rechnungseingang");
        assertThat(text).contains("70101");
        assertThat(text).contains("kurzfassung");
        assertThat(text).contains("schritte");
    }

    /** Die Entscheidung ist gefallen - der zweite Aufruf darf nicht erneut kontieren. */
    @Test
    void asksForAnExplanationRatherThanForANewClassification() {
        String text = onlyMessage(RationalePrompt.build(invoice, result, List.of())).content();

        assertThat(text).doesNotContain("Ablauflogik (in dieser Reihenfolge pruefen)");
        assertThat(text).contains("bereits getroffene");
    }

    /** Gefragt ist ein Gedankengang, kein Bericht - die Ich-Form steht deshalb im Auftrag. */
    @Test
    void asksForFirstPersonThoughtsInsteadOfReportLanguage() {
        String text = onlyMessage(RationalePrompt.build(invoice, result, List.of())).content();

        assertThat(text).contains("Ich-Form");
        assertThat(text).contains("Gedankengang");
        assertThat(text).contains("denkst du gerade laut");
    }

    @Test
    void attachesTheRenderedPagesSoHandwrittenBelegeCanBeExplainedToo() {
        ChatRequest request = RationalePrompt.build(invoice, result,
                List.of(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));

        assertThat(onlyMessage(request).hasImages()).isTrue();
    }

    private static ChatMessage onlyMessage(ChatRequest request) {
        assertThat(request.messages()).hasSize(1);
        return request.messages().get(0);
    }
}
