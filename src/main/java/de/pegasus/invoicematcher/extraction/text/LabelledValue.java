package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest den Wert hinter einer Feldbeschriftung ({@code "Rechnungs-Nr.:   5007"}).
 *
 * <p>Nach dem Label wird bis zum naechsten Spaltenumbruch gelesen; damit bleiben weitere
 * Spalten derselben layout-erhaltenden Textzeile aussen vor.
 */
final class LabelledValue {

    private static final Pattern COLUMN_BREAK = Pattern.compile(" {3,}");

    private LabelledValue() {
    }

    static Optional<String> find(DocumentText text, Pattern label) {
        for (String line : text.lines()) {
            Optional<String> value = findIn(line, label);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    static Optional<String> findIn(String line, Pattern label) {
        Matcher matcher = label.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String tail = line.substring(matcher.end()).stripLeading();
        tail = tail.startsWith(":") ? tail.substring(1).stripLeading() : tail;
        String value = COLUMN_BREAK.split(tail, 2)[0].strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
