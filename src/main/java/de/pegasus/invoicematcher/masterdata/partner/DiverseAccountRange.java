package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Die 26 Sammelkonten je Belegrichtung - eines pro Anfangsbuchstabe.
 *
 * <p>Bewusst getrennt von den echten Partnernummern: ein unbekannter Geschaeftspartner erhaelt
 * ein Diverse-Konto, wird aber nie stillschweigend zu einem gepflegten Stammsatz.
 *
 * @param creditorStart Konto fuer "A" im Kreditorenbereich, z.B. 70001
 * @param debitorStart  Konto fuer "A" im Debitorenbereich, z.B. 10001
 */
public record DiverseAccountRange(int creditorStart, int debitorStart) {

    private static final int LETTERS = 26;

    public static DiverseAccountRange standard() {
        return new DiverseAccountRange(70001, 10001);
    }

    /** Das Sammelkonto zum Anfangsbuchstaben des Namens; ohne erkennbaren Buchstaben gilt "A". */
    public BusinessPartner accountFor(String name, Direction direction) {
        char letter = initialLetterOf(name);
        int offset = letter - 'A';
        int start = direction == Direction.AUSGANG ? debitorStart : creditorStart;
        return new BusinessPartner(start + offset, "Diverse " + letter, direction);
    }

    public boolean contains(int konto) {
        return isInRange(konto, creditorStart) || isInRange(konto, debitorStart);
    }

    private boolean isInRange(int konto, int start) {
        return konto >= start && konto < start + LETTERS;
    }

    /**
     * Umlaute werden auf ihren Grundbuchstaben zurueckgefuehrt ("Oelmuehle" und "Ölmühle"
     * landen im selben Sammelkonto); Ziffern und Sonderzeichen fallen auf "A" zurueck.
     */
    private static char initialLetterOf(String name) {
        if (name == null || name.isBlank()) {
            return 'A';
        }
        String ascii = Normalizer.normalize(name.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        for (char c : ascii.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                return c;
            }
        }
        return 'A';
    }
}
