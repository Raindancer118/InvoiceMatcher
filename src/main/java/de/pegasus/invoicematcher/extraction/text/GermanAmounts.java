package de.pegasus.invoicematcher.extraction.text;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst Betraege und Prozentsaetze in deutscher Notation ({@code 1.070,99}) aus Belegtexten.
 *
 * <p>Ein Betrag muss zwingend zwei Nachkommastellen tragen. Diese Einschraenkung ist bewusst:
 * sie verhindert, dass Mengen-, Positions- oder Artikelnummern in einer Tabellenzeile
 * faelschlich als Geldbetrag gelesen werden.
 */
public final class GermanAmounts {

    private static final Pattern AMOUNT = Pattern.compile("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|-?\\d+,\\d{2}");
    private static final Pattern PERCENTAGE = Pattern.compile("(\\d{1,2}(?:,\\d{1,2})?)\\s*%");

    private GermanAmounts() {
    }

    /** Parst einen isolierten Betrag; leer, wenn der Text kein vollstaendiger Betrag ist. */
    public static Optional<BigDecimal> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        Matcher matcher = AMOUNT.matcher(trimmed);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(toBigDecimal(trimmed));
    }

    /**
     * Liefert den letzten Betrag einer Zeile. Belegzeilen tragen den Wert in der rechten Spalte;
     * fuehrende Zahlen (Steuersatz, Menge) sollen daher nicht gewinnen.
     */
    public static Optional<BigDecimal> lastAmountIn(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String withoutPercentages = PERCENTAGE.matcher(line).replaceAll(" ");
        Matcher matcher = AMOUNT.matcher(withoutPercentages);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return Optional.ofNullable(last).map(GermanAmounts::toBigDecimal);
    }

    /** Liefert den ersten Prozentsatz einer Zeile, z.B. {@code 19} aus {@code "19% Mehrwertsteuer"}. */
    public static Optional<BigDecimal> firstPercentageIn(String line) {
        if (line == null) {
            return Optional.empty();
        }
        Matcher matcher = PERCENTAGE.matcher(line);
        return matcher.find() ? Optional.of(toBigDecimal(matcher.group(1))) : Optional.empty();
    }

    private static BigDecimal toBigDecimal(String german) {
        return new BigDecimal(german.replace(".", "").replace(',', '.'));
    }
}
