package de.pegasus.invoicematcher.extraction.text;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Der Belegtext als Zeilenmodell: Leerzeilen sind entfernt, Zeilen sind rechts- und
 * linksbuendig getrimmt, und breite Whitespace-Luecken werden als Spaltentrenner verstanden.
 *
 * <p>Layout-erhaltende PDF-Textextraktion stellt nebeneinander stehende Textbloecke als eine
 * Zeile mit grossen Luecken dar. {@link #columnsOf(int)} macht diese Bloecke wieder trennbar.
 */
public final class DocumentText {

    private static final Pattern COLUMN_SEPARATOR = Pattern.compile(" {3,}");

    private final String raw;
    private final List<String> lines;

    private DocumentText(String raw, List<String> lines) {
        this.raw = raw;
        this.lines = lines;
    }

    public static DocumentText of(String raw) {
        String source = raw == null ? "" : raw;
        List<String> lines = source.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        return new DocumentText(source, lines);
    }

    /** Der unveraenderte Ausgangstext, z.B. fuer Volltextsuchen oder das Review-UI. */
    public String raw() {
        return raw;
    }

    public List<String> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Die ersten {@code count} nicht-leeren Zeilen; kuerzer, wenn der Beleg weniger hat. */
    public List<String> leadingLines(int count) {
        return lines.subList(0, Math.min(count, lines.size()));
    }

    public String lineAt(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }

    /** Zerlegt eine Zeile an breiten Whitespace-Luecken in ihre visuellen Spalten. */
    public List<String> columnsOf(int index) {
        String line = lineAt(index);
        return line.isEmpty() ? List.of() : List.of(COLUMN_SEPARATOR.split(line));
    }

    public String firstColumnOf(int index) {
        List<String> columns = columnsOf(index);
        return columns.isEmpty() ? "" : columns.getFirst().strip();
    }

    public Optional<String> findFirstLine(Predicate<String> predicate) {
        return lines.stream().filter(predicate).findFirst();
    }

    public OptionalIndex findFirstIndex(Predicate<String> predicate) {
        for (int i = 0; i < lines.size(); i++) {
            if (predicate.test(lines.get(i))) {
                return OptionalIndex.of(i);
            }
        }
        return OptionalIndex.empty();
    }

    /** Ein {@code Optional<int>} ohne Boxing-Umweg ueber {@code Integer}. */
    public record OptionalIndex(int value, boolean present) {
        public static OptionalIndex of(int value) {
            return new OptionalIndex(value, true);
        }

        public static OptionalIndex empty() {
            return new OptionalIndex(-1, false);
        }
    }
}
