package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.masterdata.account.FixedAssetIndicator;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regel 1.4: Enthaelt eine Eingangsrechnung eine Anlagenposition ueber der GWG-Grenze?
 *
 * <p>Dann wird bewusst kein Konto geraten. Ob aktiviert oder sofort abgeschrieben wird, ist
 * eine Bilanzentscheidung und keine Standardkontierung.
 */
@Component
@Order(FixedAssetRule.ORDER)
public class FixedAssetRule implements IRule {

    static final int ORDER = 500;

    private final FixedAssetIndicator indicator;
    private final AccountingProperties properties;

    public FixedAssetRule(FixedAssetIndicator indicator, AccountingProperties properties) {
        this.indicator = indicator;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "1.4";
    }

    @Override
    public String description() {
        return "Anlagenposition oberhalb der GWG-Grenze";
    }

    @Override
    public void apply(ClassificationContext context) {
        if (context.direction() != Direction.EINGANG) {
            return;
        }
        List<InvoiceLine> exceeding = context.invoice().lines().stream()
                .filter(indicator::exceedsGwgThreshold)
                .toList();
        if (exceeding.isEmpty()) {
            return;
        }
        String positions = exceeding.stream()
                .map(line -> line.description() + " (" + line.effectiveUnitNetPrice() + " EUR)")
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        context.requireReview(Finding.violation(id(),
                "Nettoeinzelpreis ueber der GWG-Grenze von " + properties.gwgLimit()
                        + " EUR - Anlagevermoegen manuell entscheiden: " + positions));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
