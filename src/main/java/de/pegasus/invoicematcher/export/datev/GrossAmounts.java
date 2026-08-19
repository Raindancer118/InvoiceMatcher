package de.pegasus.invoicematcher.export.datev;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rechnet Nettobetraege je Buchungszeile in Bruttobetraege um.
 *
 * <p>DATEV erwartet in {@code amount} den Rechnungsbetrag und lehnt ein Paket ab, dessen
 * {@code consolidatedAmount} nicht exakt der Summe der Zeilen entspricht. Beim zeilenweisen
 * Runden entstehen aber Cent-Differenzen zum ausgewiesenen Bruttobetrag des Belegs. Die
 * Restdifferenz wird deshalb der betragsstaerksten Zeile zugeschlagen - dort faellt sie
 * relativ am wenigsten ins Gewicht.
 */
final class GrossAmounts {

    /** Je Zeile ist hoechstens ein Cent Rundungsdifferenz erklaerbar. */
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private GrossAmounts() {
    }

    /**
     * @param net             Nettobetraege in Zeilenreihenfolge
     * @param rates           zugehoerige Steuersaetze in Prozent, Eintraege duerfen {@code null} sein
     * @param declaredGross   auf dem Beleg ausgewiesener Bruttobetrag, darf {@code null} sein
     * @return Bruttobetraege in derselben Reihenfolge
     */
    static List<BigDecimal> from(List<BigDecimal> net, List<BigDecimal> rates, BigDecimal declaredGross) {
        List<BigDecimal> gross = new ArrayList<>(net.size());
        for (int i = 0; i < net.size(); i++) {
            gross.add(grossOf(net.get(i), rates.get(i)));
        }
        return declaredGross == null ? gross : alignWith(gross, declaredGross);
    }

    private static BigDecimal grossOf(BigDecimal net, BigDecimal rate) {
        BigDecimal factor = rate == null
                ? BigDecimal.ONE
                : BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return net.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Gleicht eine reine Rundungsdifferenz aus; groessere Abweichungen bleiben sichtbar. */
    private static List<BigDecimal> alignWith(List<BigDecimal> gross, BigDecimal declaredGross) {
        BigDecimal sum = gross.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal residual = declaredGross.subtract(sum);
        BigDecimal tolerance = CENT.multiply(BigDecimal.valueOf(gross.size()));
        if (residual.signum() == 0 || residual.abs().compareTo(tolerance) > 0) {
            return gross;
        }
        int largest = indexOfLargest(gross);
        List<BigDecimal> aligned = new ArrayList<>(gross);
        aligned.set(largest, aligned.get(largest).add(residual));
        return aligned;
    }

    private static int indexOfLargest(List<BigDecimal> amounts) {
        int largest = 0;
        for (int i = 1; i < amounts.size(); i++) {
            if (amounts.get(i).abs().compareTo(amounts.get(largest).abs()) > 0) {
                largest = i;
            }
        }
        return largest;
    }
}
