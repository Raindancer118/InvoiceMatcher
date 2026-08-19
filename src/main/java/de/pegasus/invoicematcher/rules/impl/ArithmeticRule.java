package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Regel 1.3: Gilt Netto + Steuer = Brutto innerhalb der zulaessigen Toleranz?
 *
 * <p>Geprueft werden die auf dem Beleg <em>ausgewiesenen</em> Summen. Ein Nachrechnen aus den
 * Positionen wuerde genau den Fehler verdecken, den diese Regel finden soll.
 */
@Component
@Order(ArithmeticRule.ORDER)
public class ArithmeticRule implements IRule {

    static final int ORDER = 400;

    private final AccountingProperties properties;

    public ArithmeticRule(AccountingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "1.3";
    }

    @Override
    public String description() {
        return "Rechenprobe Netto + Steuer = Brutto";
    }

    @Override
    public void apply(ClassificationContext context) {
        MonetaryTotals totals = context.invoice().totals();
        if (!totals.isComplete()) {
            return; // bereits von Regel 1.2 bemaengelt
        }
        BigDecimal deviation = totals.net().add(totals.tax()).subtract(totals.gross()).abs();
        if (deviation.compareTo(properties.roundingTolerance()) > 0) {
            context.requireReview(Finding.violation(id(), String.format(
                    "Rechenprobe fehlgeschlagen: %s + %s ergibt nicht %s (Abweichung %s EUR)",
                    totals.net(), totals.tax(), totals.gross(), deviation)));
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
