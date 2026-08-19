package de.pegasus.invoicematcher.domain;

import java.math.BigDecimal;

/**
 * Die auf dem Beleg <em>ausgewiesenen</em> Summen. Bewusst nicht nachgerechnet: Regel 1.3
 * prueft genau die Differenz zwischen ausgewiesenen und rechnerischen Werten.
 *
 * @param net   Nettobetrag, {@code null} wenn nicht ausgewiesen
 * @param tax   Steuerbetrag, {@code null} wenn nicht ausgewiesen
 * @param gross Bruttobetrag, {@code null} wenn nicht ausgewiesen
 */
public record MonetaryTotals(BigDecimal net, BigDecimal tax, BigDecimal gross) {

    public static final MonetaryTotals EMPTY = new MonetaryTotals(null, null, null);

    public boolean isComplete() {
        return net != null && tax != null && gross != null;
    }
}
