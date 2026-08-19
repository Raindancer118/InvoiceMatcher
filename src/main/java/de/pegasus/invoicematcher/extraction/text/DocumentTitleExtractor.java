package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bestimmt die Belegueberschrift - die Grundlage von Regel 1.1.
 *
 * <p>Gesucht wird in der linken Spalte der ersten Belegzeilen nach einer bekannten Belegart.
 * Die Wortgrenze ist entscheidend: {@code "Rechnungsdatum"} und {@code "Rechnungsnummer"}
 * duerfen einen Bescheid nicht in eine Rechnung verwandeln (Beleg 19).
 */
public final class DocumentTitleExtractor implements TextFieldExtractor<String> {

    /** Zeilen, in denen eine Ueberschrift ueberhaupt stehen kann. */
    private static final int HEADER_LINES = 30;

    private static final Pattern DOCUMENT_KIND = Pattern.compile(
            "^(?:rechnung|gutschrift|rechnungskorrektur|stornorechnung|proforma[- ]?rechnung"
                    + "|lieferschein|angebot|auftragsbest(?:ä|ae)tigung|bestellung|mahnung"
                    + "|kostenvoranschlag|quittung|zahlungsavis)\\b"
                    + "|^\\p{L}*(?:bescheid|vertrag|bestätigung)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Optional<String> extract(DocumentText text) {
        int limit = Math.min(HEADER_LINES, text.lines().size());
        for (int i = 0; i < limit; i++) {
            String candidate = text.firstColumnOf(i);
            if (DOCUMENT_KIND.matcher(candidate).find()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
