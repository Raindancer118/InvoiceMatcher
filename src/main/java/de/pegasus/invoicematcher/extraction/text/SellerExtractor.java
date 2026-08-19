package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.Party;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bestimmt den Rechnungssteller aus dem Briefkopf.
 *
 * <p>Bevorzugt wird die Absenderzeile ueber dem Anschriftenfeld ({@code "Name, Strasse, PLZ Ort"}),
 * weil sie den Firmennamen sauber begrenzt. Fehlt sie - etwa im schlanken Layout der
 * konstruierten Belege -, gilt die erste Zeile des Belegs.
 */
public final class SellerExtractor implements TextFieldExtractor<Party> {

    private static final int HEADER_LINES = 10;

    private static final Pattern SENDER_LINE = Pattern.compile("^(.+?),\\s*.+?,\\s*\\d{5}\\s+\\S");

    @Override
    public Optional<Party> extract(DocumentText text) {
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Party(
                name(text).orElse(null),
                TaxIdentifierExtractor.vatId(text).orElse(null),
                TaxIdentifierExtractor.taxNumber(text).orElse(null)));
    }

    private Optional<String> name(DocumentText text) {
        for (String line : text.leadingLines(HEADER_LINES)) {
            Matcher matcher = SENDER_LINE.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(1).strip());
            }
        }
        String first = text.firstColumnOf(0);
        return first.isEmpty() ? Optional.empty() : Optional.of(first);
    }
}
