package de.pegasus.invoicematcher.domain;

import java.math.BigDecimal;

/**
 * Eine Rechnungsposition.
 *
 * @param description  Positionstext; einzige belastbare Grundlage der Sachkontenzuordnung
 * @param quantity     Menge, {@code null} wenn der Beleg keine ausweist
 * @param unitNetPrice Nettoeinzelpreis; massgeblich fuer die GWG-Grenze
 * @param netAmount    Nettozeilensumme
 * @param vatRate      Steuersatz in Prozent, {@code null} wenn nicht ausgewiesen
 */
public record InvoiceLine(String description,
                          BigDecimal quantity,
                          BigDecimal unitNetPrice,
                          BigDecimal netAmount,
                          BigDecimal vatRate) {

    public boolean hasVatRate() {
        return vatRate != null;
    }

    /** Fehlt der Einzelpreis, wird er aus Zeilensumme und Menge abgeleitet. */
    public BigDecimal effectiveUnitNetPrice() {
        if (unitNetPrice != null) {
            return unitNetPrice;
        }
        if (netAmount == null || quantity == null || quantity.signum() == 0) {
            return null;
        }
        return netAmount.divide(quantity, 2, java.math.RoundingMode.HALF_UP);
    }
}
