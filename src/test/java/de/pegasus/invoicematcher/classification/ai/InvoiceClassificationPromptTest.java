package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;
import io.github.raindancer118.aigateway.ChatContent;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceClassificationPromptTest {

    private final CompanyProperties company = new CompanyProperties("Muster zu Testzwecken GmbH", "DE 815000010", "24112345678");
    private final AccountingProperties accounting = new AccountingProperties(new BigDecimal("800.00"),
            new BigDecimal("0.01"),
            List.of(new AccountingProperties.VatAccount(new BigDecimal("19"), 8400, true)),
            List.of(new AccountingProperties.VatAccount(new BigDecimal("19"), 3400, true)),
            List.of(new AccountingProperties.ExpenseCategory("Furniere/Kanten", 3401, false, List.of("furnier"))),
            List.of("maschine"));
    private final PartnerProperties partners = new PartnerProperties(new PartnerProperties.Diverse(70001, 10001),
            List.of(new PartnerProperties.KnownPartner(70101, "Friedrich Klausmuster", Direction.EINGANG,
                    "DE 404131066", "156/290/44989")));

    private ExtractedInvoice invoice(SourceFormat format) {
        return ExtractedInvoice.builder(format)
                .documentTitle("Rechnung")
                .invoiceNumber("5007")
                .seller(new Party("Friedrich Klausmuster", "DE 404131066", "156/290/44989"))
                .buyer(new Party("Muster zu Testzwecken GmbH", null, null))
                .totals(new MonetaryTotals(new BigDecimal("404.00"), new BigDecimal("76.76"), new BigDecimal("480.76")))
                .rawText("Rechnung\nRechnungsnummer 5007")
                .build();
    }

    @Test
    void includesTheAccountingAndPartnerCatalogAndTheInvoiceText() {
        ChatRequest request = InvoiceClassificationPrompt.build(invoice(SourceFormat.PDF), company, accounting,
                partners, List.of());

        String text = onlyMessage(request).content();
        assertThat(text).contains("Furniere/Kanten");
        assertThat(text).contains("3401");
        assertThat(text).contains("Friedrich Klausmuster");
        assertThat(text).contains("70101");
        assertThat(text).contains("Muster zu Testzwecken GmbH");
        assertThat(text).contains("Rechnungsnummer 5007");
        assertThat(text).contains("document_type");
    }

    /** Schwellenwerte kommen aus dem Kontenrahmen, nicht als eingebrannte Zahl aus dem Text. */
    @Test
    void takesItsThresholdsFromTheEditableAccountingSettings() {
        String text = onlyMessage(InvoiceClassificationPrompt.build(invoice(SourceFormat.PDF), company,
                accounting, partners, List.of())).content();

        assertThat(text).contains("roundingTolerance");
        assertThat(text).contains("gwgLimit");
        assertThat(text).doesNotContain("Toleranz 0,01 EUR");
    }

    @Test
    void attachesRenderedPagesForScannedDocuments() {
        BufferedImage page = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ChatRequest request = InvoiceClassificationPrompt.build(invoice(SourceFormat.SCAN), company, accounting,
                partners, List.of(page));

        ChatMessage message = onlyMessage(request);
        assertThat(message.hasImages()).isTrue();
        assertThat(message.parts()).anySatisfy(part -> assertThat(part).isInstanceOf(ChatContent.Image.class));
    }

    @Test
    void attachesNoImagesWhenNoPagesAreGiven() {
        ChatRequest request = InvoiceClassificationPrompt.build(invoice(SourceFormat.PDF), company, accounting,
                partners, List.of());

        assertThat(onlyMessage(request).hasImages()).isFalse();
    }

    private static ChatMessage onlyMessage(ChatRequest request) {
        assertThat(request.messages()).hasSize(1);
        return request.messages().get(0);
    }
}
