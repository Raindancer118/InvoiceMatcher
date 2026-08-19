package de.pegasus.invoicematcher.extraction.text;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest das Rechnungsdatum. Bewusst nur explizit als Rechnungs-/Belegdatum beschriftete Werte:
 * ein Lieferdatum ersetzt die Pflichtangabe nicht (Regel 1.2).
 */
public final class InvoiceDateExtractor implements TextFieldExtractor<LocalDate> {

    private static final Pattern LABEL = Pattern.compile(
            "(?:rechnungsdatum|belegdatum|\\bdatum\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern GERMAN_DATE = Pattern.compile("\\b(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\b");

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d.M.uuuu");

    @Override
    public Optional<LocalDate> extract(DocumentText text) {
        return LabelledValue.find(text, LABEL).flatMap(InvoiceDateExtractor::parse);
    }

    private static Optional<LocalDate> parse(String value) {
        Matcher matcher = GERMAN_DATE.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(matcher.group(1), FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
