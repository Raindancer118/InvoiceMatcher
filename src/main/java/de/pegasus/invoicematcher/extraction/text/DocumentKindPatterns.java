package de.pegasus.invoicematcher.extraction.text;

import java.util.regex.Pattern;

/**
 * Bekannte Belegarten am Zeilenanfang - die gemeinsame Erkennung fuer {@link DocumentTitleExtractor}
 * und fuer alles, was pruefen will, ob ein Text ueberhaupt eine lesbare Belegueberschrift traegt
 * (z.B. die Entscheidung, eine zweite Zeichenerkennung anzufragen).
 *
 * <p>Die Wortgrenze ist entscheidend: {@code "Rechnungsdatum"} und {@code "Rechnungsnummer"}
 * duerfen einen Bescheid nicht in eine Rechnung verwandeln (Beleg 19).
 */
public final class DocumentKindPatterns {

    private static final Pattern DOCUMENT_KIND = Pattern.compile(
            "^(?:rechnung|gutschrift|rechnungskorrektur|stornorechnung|proforma[- ]?rechnung"
                    + "|lieferschein|angebot|auftragsbest(?:ä|ae)tigung|bestellung|mahnung"
                    + "|kostenvoranschlag|quittung|zahlungsavis)\\b"
                    + "|^\\p{L}*(?:bescheid|vertrag|bestätigung)\\b",
            Pattern.CASE_INSENSITIVE);

    private DocumentKindPatterns() {
    }

    public static boolean matches(String candidate) {
        return DOCUMENT_KIND.matcher(candidate).find();
    }
}
