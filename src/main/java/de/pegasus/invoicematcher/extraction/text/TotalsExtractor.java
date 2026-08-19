package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.MonetaryTotals;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Liest die auf dem Beleg ausgewiesenen Summen.
 *
 * <p>Die drei Beschriftungen werden in fester Reihenfolge gesucht und jede Zeile nur einmal
 * vergeben. Damit gewinnt "Netto-Rechnungsbetrag" die Netto-Suche, bevor die Brutto-Suche
 * ueber dieselbe Zeile stolpern koennte.
 */
public final class TotalsExtractor implements TextFieldExtractor<MonetaryTotals> {

    private static final Pattern NET = Pattern.compile(
            "(?:netto[\\s-]?summe|netto[\\s-]?rechnungsbetrag|netto[\\s-]?betrag|summe\\s+netto"
                    + "|gesamt\\s+netto|zwischensumme|nettowarenwert)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAX = Pattern.compile(
            "(?:mehrwertsteuer|umsatzsteuer|\\bmwst\\.?|\\bust\\.?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern GROSS = Pattern.compile(
            "(?:gesamtsumme|brutto[\\s-]?rechnungsbetrag|brutto[\\s-]?betrag|gesamt[\\s-]?betrag"
                    + "|rechnungsbetrag|endbetrag|zahlbetrag|rechnungssumme)", Pattern.CASE_INSENSITIVE);

    /** Identifikationsnummern stehen ebenfalls unter "USt", tragen aber keinen Steuerbetrag. */
    private static final Pattern IDENTIFIER = Pattern.compile(
            "(?:id\\.?[\\s-]?nr|identifikationsnummer)", Pattern.CASE_INSENSITIVE);

    @Override
    public Optional<MonetaryTotals> extract(DocumentText text) {
        boolean[] consumed = new boolean[text.lines().size()];
        BigDecimal net = firstAmount(text, consumed, NET);
        BigDecimal tax = firstAmount(text, consumed, TAX);
        BigDecimal gross = firstAmount(text, consumed, GROSS);
        return Optional.of(new MonetaryTotals(net, tax, gross));
    }

    private BigDecimal firstAmount(DocumentText text, boolean[] consumed, Pattern label) {
        for (int i = 0; i < text.lines().size(); i++) {
            String line = text.lineAt(i);
            if (consumed[i] || !label.matcher(line).find() || IDENTIFIER.matcher(line).find()) {
                continue;
            }
            Optional<BigDecimal> amount = GermanAmounts.lastAmountIn(line);
            if (amount.isPresent()) {
                consumed[i] = true;
                return amount.get();
            }
        }
        return null;
    }
}
