package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import io.github.raindancer118.aigateway.ChatContent;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Der Belegteil eines Prompts - gemeinsam genutzt von {@link InvoiceClassificationPrompt} (die
 * Entscheidung) und {@link RationalePrompt} (die Erlaeuterung dazu). Beide Anfragen muessen
 * denselben Beleg vor Augen haben, sonst erklaert die zweite etwas anderes, als die erste
 * entschieden hat.
 */
final class BelegContext {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BelegContext() {
    }

    /** Die extrahierten Belegdaten samt Volltext, wie sie beiden Anfragen vorliegen. */
    static String describe(ExtractedInvoice invoice) {
        StringBuilder text = new StringBuilder();
        text.append("Beleg (Format ").append(invoice.sourceFormat()).append("):\n");
        text.append("Titel: ").append(invoice.documentTitle()).append('\n');
        text.append("Rechnungsnummer: ").append(invoice.invoiceNumber()).append('\n');
        text.append("Rechnungsdatum: ").append(invoice.invoiceDate()).append('\n');
        text.append("Aussteller: ").append(JSON.writeValueAsString(invoice.seller())).append('\n');
        text.append("Empfaenger: ").append(JSON.writeValueAsString(invoice.buyer())).append('\n');
        text.append("Summen: ").append(JSON.writeValueAsString(invoice.totals())).append('\n');
        text.append("Positionen: ").append(JSON.writeValueAsString(invoice.lines())).append('\n');
        text.append("Volltext:\n").append(invoice.rawText());
        return text.toString();
    }

    /** Eine Nutzernachricht aus Text und - bei gescannten Belegen - den gerasterten Seiten. */
    static ChatRequest request(String text, List<BufferedImage> pages) {
        List<ChatContent> parts = new ArrayList<>();
        parts.add(new ChatContent.Text(text));
        for (BufferedImage page : pages) {
            parts.add(new ChatContent.Image("image/png", encode(page)));
        }
        return ChatRequest.builder().message(ChatMessage.user(parts)).build();
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    private static byte[] encode(BufferedImage page) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(page, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
