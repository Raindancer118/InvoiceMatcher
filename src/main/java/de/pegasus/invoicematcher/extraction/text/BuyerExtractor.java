package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.Party;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bestimmt den Rechnungsempfaenger ueber die Anrede des Anschriftenfeldes.
 *
 * <p>Fehlt die Anrede, bleibt das Ergebnis leer: der Empfaenger wird dann nicht geraten,
 * sondern die Belegrichtung ueber den Rechnungssteller bestimmt.
 */
public final class BuyerExtractor implements TextFieldExtractor<Party> {

    private static final int HEADER_LINES = 20;

    private static final Pattern ADDRESS_LABEL = Pattern.compile(
            "^(?:firma|herrn|herr|frau|an|kunde|kundin|rechnungsempf(?:ä|ae)nger)\\.?:?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Optional<Party> extract(DocumentText text) {
        int limit = Math.min(HEADER_LINES, text.lines().size()) - 1;
        for (int i = 0; i < limit; i++) {
            if (ADDRESS_LABEL.matcher(text.firstColumnOf(i)).matches()) {
                String name = text.firstColumnOf(i + 1);
                if (!name.isEmpty()) {
                    return Optional.of(new Party(name, null, null));
                }
            }
        }
        return Optional.empty();
    }
}
