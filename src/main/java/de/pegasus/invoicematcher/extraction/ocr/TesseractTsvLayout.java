package de.pegasus.invoicematcher.extraction.ocr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Baut aus der Wortliste einer Tesseract-TSV-Ausgabe wieder eine layouterhaltende Textseite.
 *
 * <p>Die reine Textausgabe von Tesseract trennt Woerter durch genau ein Leerzeichen und
 * verliert damit die Spaltenstruktur, aus der Anschriftenfeld und Kopfdaten getrennt werden -
 * dasselbe Problem, das {@code LayoutTextStripper} fuer PDFBox loest. Rekonstruiert wird hier
 * ueber die Wortrahmen: die mittlere Zeichenbreite der Seite ist das Raster, in das die Woerter
 * nach ihrer x-Position einsortiert werden.
 *
 * <p>Zeilen werden bewusst nicht ueber Tesseracts {@code line_num} gebildet. Nebeneinander
 * liegende Bloecke - Absender links, Kopfdaten rechts - zaehlt Tesseract getrennt; visuell
 * stehen sie aber auf einer Hoehe und muessen in einer Ausgabezeile landen.
 */
public final class TesseractTsvLayout {

    /**
     * Nur einzelne Zeichen werden nach ihrer Sicherheit gefiltert.
     *
     * <p>Fuer mehrstellige Woerter taugt der Wert nicht: der Einzelpreis "0,77" einer
     * Belegposition wird richtig gelesen und mit Sicherheit 0,0 gemeldet, weil die senkrechte
     * Tabellenlinie daran klebt. Eine Schwelle, die so etwas verwirft, kostet eine ganze
     * Belegposition - und damit die Sachkontierung, die daran haengt.
     *
     * <p>Einzelne Zeichen sind der umgekehrte Fall: Staub, Lochung und Linienreste erscheinen
     * fast immer als ein Zeichen, ein mehrstelliges Wort dagegen praktisch nie zufaellig.
     */
    private static final double MIN_CONFIDENCE_SINGLE_CHARACTER = 50.0;

    /** Zwei Woerter gehoeren zur selben Zeile, wenn ihre Mitten enger liegen als das hiervon. */
    private static final double LINE_TOLERANCE = 0.6;

    /** Luecken unterhalb dieses Anteils einer Zeichenbreite sind Satzabstand, kein Trenner. */
    private static final double SPACE_THRESHOLD = 0.3;

    /**
     * Senkrechte Tabellenlinien liest Tesseract als Zeichen. Sie stehen dann am Wortrand
     * ({@code |Echtholzfurnierkanten}) oder als eigenes "Wort" mitten in der Zeile und trennen
     * genau die Spalten, die der Positionsleser wieder zusammensetzen muss.
     */
    private static final Pattern RULE_CHARACTERS = Pattern.compile("^[|¦\\[\\]]+|[|¦\\[\\]]+$");

    private static final int TSV_COLUMNS = 12;
    private static final int WORD_LEVEL = 5;

    private TesseractTsvLayout() {
    }

    /** Ein erkanntes Wort mit seinem Rahmen auf der Seite. */
    record Word(int left, int top, int width, int height, String text) {

        int centerY() {
            return top + height / 2;
        }

        double characterWidth() {
            return text.isEmpty() ? width : (double) width / text.length();
        }
    }

    public static String toText(String tsv) {
        return render(parse(tsv));
    }

    static List<Word> parse(String tsv) {
        List<Word> words = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            String[] cells = line.split("\t", -1);
            if (cells.length < TSV_COLUMNS || !"5".equals(cells[0].strip())) {
                continue;
            }
            String text = withoutRuleCharacters(cells[11].strip());
            if (text.isEmpty()) {
                continue;
            }
            try {
                double confidence = Double.parseDouble(cells[10]);
                if (text.length() == 1 && confidence < MIN_CONFIDENCE_SINGLE_CHARACTER) {
                    continue;
                }
                words.add(new Word(Integer.parseInt(cells[6]), Integer.parseInt(cells[7]),
                        Integer.parseInt(cells[8]), Integer.parseInt(cells[9]), text));
            } catch (NumberFormatException e) {
                // Kopfzeile oder unvollstaendige Zeile - kein Wort.
            }
        }
        return words;
    }

    /**
     * Entfernt Tabellenlinien am Wortrand. Ein Wort, das nur aus ihnen besteht, faellt ganz
     * weg - seine Breite fehlt danach in der Zeile, was gewollt ist: es war nie Text.
     */
    private static String withoutRuleCharacters(String text) {
        return RULE_CHARACTERS.matcher(text).replaceAll("");
    }

    private static String render(List<Word> words) {
        if (words.isEmpty()) {
            return "";
        }
        double tolerance = medianHeight(words) * LINE_TOLERANCE;

        StringBuilder page = new StringBuilder();
        for (List<Word> line : groupIntoLines(words, tolerance)) {
            page.append(renderLine(line)).append('\n');
        }
        return page.toString();
    }

    /** Gruppiert nach visueller Hoehe, damit nebeneinander liegende Bloecke zusammenfinden. */
    private static List<List<Word>> groupIntoLines(List<Word> words, double tolerance) {
        List<Word> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingInt(Word::centerY).thenComparingInt(Word::left));

        List<List<Word>> lines = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        int reference = 0;
        for (Word word : sorted) {
            if (current.isEmpty()) {
                reference = word.centerY();
            } else if (Math.abs(word.centerY() - reference) > tolerance) {
                lines.add(sortedByX(current));
                current = new ArrayList<>();
                reference = word.centerY();
            }
            current.add(word);
        }
        if (!current.isEmpty()) {
            lines.add(sortedByX(current));
        }
        return lines;
    }

    private static List<Word> sortedByX(List<Word> line) {
        List<Word> sorted = new ArrayList<>(line);
        sorted.sort(Comparator.comparingInt(Word::left));
        return sorted;
    }

    /**
     * Gemessen wird die Luecke zum Vorgaenger, nicht die absolute x-Position. Ein Raster ueber
     * die ganze Seite sammelt Rundungsfehler ein und schiebt am Zeilenende zwei Leerzeichen
     * zwischen zwei Woerter, die zusammengehoeren - aus "Testzwecken GmbH" wuerden zwei Spalten.
     * Massstab ist die Zeichenbreite des jeweiligen Wortes: Ueberschrift und Fliesstext einer
     * Belegzeile haben unterschiedliche Schriftgrade.
     */
    private static String renderLine(List<Word> line) {
        StringBuilder text = new StringBuilder();
        Integer previousEnd = null;
        for (Word word : line) {
            text.append(" ".repeat(gapInSpaces(previousEnd, word)));
            text.append(word.text());
            previousEnd = word.left() + word.width();
        }
        return text.toString().stripTrailing();
    }

    private static int gapInSpaces(Integer previousEnd, Word word) {
        double characterWidth = Math.max(word.characterWidth(), 1.0);
        double gap = previousEnd == null ? word.left() : word.left() - previousEnd;
        if (gap <= 0) {
            return previousEnd == null ? 0 : 1;
        }
        int spaces = (int) Math.round(gap / characterWidth);
        return gap >= SPACE_THRESHOLD * characterWidth ? Math.max(spaces, 1) : (previousEnd == null ? 0 : 1);
    }

    private static double medianHeight(List<Word> words) {
        int[] heights = words.stream().mapToInt(Word::height).sorted().toArray();
        return Math.max(heights[heights.length / 2], 1);
    }
}
