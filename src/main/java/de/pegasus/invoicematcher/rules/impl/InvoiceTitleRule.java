package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Regel 1.1: Traegt der Beleg im Titel "Rechnung" oder "Gutschrift"?
 *
 * <p>Die Wortgrenze ist die eigentliche Pruefung. Ein Grundabgabenbescheid nennt
 * "Rechnungsdatum" und "Rechnungsnummer" und ist trotzdem keine Rechnung; eine Gutschrift
 * ist umgekehrt vollwertig zu behandeln.
 */
@Component
@Order(InvoiceTitleRule.ORDER)
public class InvoiceTitleRule implements IRule {

    static final int ORDER = 100;

    private static final Pattern INVOICE_TITLE =
            Pattern.compile("\\b(rechnung|gutschrift)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String id() {
        return "1.1";
    }

    @Override
    public String description() {
        return "Belegtitel enthaelt 'Rechnung' oder 'Gutschrift'";
    }

    @Override
    public void apply(ClassificationContext context) {
        String title = context.invoice().documentTitle();
        if (title != null && INVOICE_TITLE.matcher(title).find()) {
            return;
        }
        context.markAsSonstige(Finding.violation(id(), title == null || title.isBlank()
                ? "Kein Belegtitel erkennbar - der Beleg wird als Sonstige abgelegt"
                : "Belegtitel '" + title + "' weist den Beleg nicht als Rechnung oder Gutschrift aus"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
