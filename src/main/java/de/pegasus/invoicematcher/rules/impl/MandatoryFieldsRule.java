package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Regel 1.2: Sind Rechnungsnummer, Rechnungsdatum, Steuernummer/USt-IdNr. sowie Netto-, Steuer-
 * und Bruttobetrag vorhanden?
 *
 * <p>Es werden alle fehlenden Angaben gesammelt und nicht nur die erste - ein Beleg soll in
 * einem Durchgang vollstaendig bemaengelt werden koennen.
 */
@Component
@Order(MandatoryFieldsRule.ORDER)
public class MandatoryFieldsRule implements IRule {

    static final int ORDER = 300;

    @Override
    public String id() {
        return "1.2";
    }

    @Override
    public String description() {
        return "Pflichtangaben nach §14 UStG vorhanden";
    }

    @Override
    public void apply(ClassificationContext context) {
        List<String> missing = missingFields(context.invoice(), context.direction());
        if (!missing.isEmpty()) {
            context.requireReview(Finding.violation(id(),
                    "Pflichtangaben fehlen: " + String.join(", ", missing)));
        }
    }

    /** Pflichtangabe ist stets die Steuernummer des Rechnungsstellers - bei uns wie beim Lieferanten. */
    private List<String> missingFields(ExtractedInvoice invoice, Direction direction) {
        List<String> missing = new ArrayList<>();
        if (isBlank(invoice.invoiceNumber())) {
            missing.add("Rechnungsnummer");
        }
        if (invoice.invoiceDate() == null) {
            missing.add("Rechnungsdatum");
        }
        if (!invoice.seller().hasTaxIdentifier()) {
            missing.add("Steuernummer/USt-IdNr.");
        }
        MonetaryTotals totals = invoice.totals();
        if (totals.net() == null) {
            missing.add("Nettobetrag");
        }
        if (totals.tax() == null) {
            missing.add("Steuerbetrag");
        }
        if (totals.gross() == null) {
            missing.add("Bruttobetrag");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
