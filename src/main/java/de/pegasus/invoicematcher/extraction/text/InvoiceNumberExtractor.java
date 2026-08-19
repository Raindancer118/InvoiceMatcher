package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;
import java.util.regex.Pattern;

/** Liest die Rechnungsnummer (DATEV: {@code belegfeld_1}). */
public final class InvoiceNumberExtractor implements TextFieldExtractor<String> {

    private static final Pattern LABEL = Pattern.compile(
            "(?:rechnungs[\\s-]?nummer|rechnungs[\\s-]?nr\\.?|rechnung\\s+nr\\.?|beleg[\\s-]?nr\\.?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9./-]*");

    @Override
    public Optional<String> extract(DocumentText text) {
        return LabelledValue.find(text, LABEL)
                .map(VALUE::matcher)
                .filter(java.util.regex.Matcher::find)
                .map(java.util.regex.Matcher::group);
    }
}
