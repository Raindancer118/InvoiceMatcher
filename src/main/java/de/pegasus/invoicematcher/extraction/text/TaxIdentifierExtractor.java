package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest USt-IdNr. und Steuernummer. Beide stehen im Briefkopf des Ausstellers, weshalb der
 * jeweils erste Treffer im Beleg dem Rechnungssteller zugeordnet wird.
 */
final class TaxIdentifierExtractor {

    private static final Pattern VAT_ID_LABEL = Pattern.compile(
            "(?:u\\s?st\\.?[\\s-]?id\\.?[\\s-]?nr\\.?|umsatzsteuer[\\s-]?identifikationsnummer|vat[\\s-]?id)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VAT_ID_VALUE = Pattern.compile("[A-Z]{2}\\s?[0-9A-Z]{6,}");

    private static final Pattern TAX_NUMBER_LABEL = Pattern.compile(
            "(?:steuernummer|steuer[\\s-]?nr\\.?|st\\.?[\\s-]?nr\\.?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAX_NUMBER_VALUE = Pattern.compile("\\d[\\d /]{6,}\\d");

    private TaxIdentifierExtractor() {
    }

    static Optional<String> vatId(DocumentText text) {
        return firstMatch(text, VAT_ID_LABEL, VAT_ID_VALUE);
    }

    static Optional<String> taxNumber(DocumentText text) {
        return firstMatch(text, TAX_NUMBER_LABEL, TAX_NUMBER_VALUE);
    }

    private static Optional<String> firstMatch(DocumentText text, Pattern label, Pattern value) {
        for (String line : text.lines()) {
            Optional<String> candidate = LabelledValue.findIn(line, label);
            if (candidate.isPresent()) {
                Matcher matcher = value.matcher(candidate.get());
                if (matcher.find()) {
                    return Optional.of(matcher.group().strip());
                }
            }
        }
        return Optional.empty();
    }
}
