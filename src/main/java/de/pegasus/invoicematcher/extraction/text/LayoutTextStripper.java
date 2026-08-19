package de.pegasus.invoicematcher.extraction.text;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein {@link PDFTextStripper}, der die horizontale Anordnung der Seite erhaelt.
 *
 * <p>PDFBox gibt standardmaessig jeden Abstand als ein einzelnes Leerzeichen aus. Damit gehen
 * genau die Informationen verloren, aus denen ein Belegleser Spalten erkennt: das
 * Anschriftenfeld links, die Kopfdaten rechts.
 *
 * <p>Rekonstruiert wird ueber die Luecke zwischen zwei Glyphen, gemessen in Vielfachen der
 * Leerzeichenbreite der jeweiligen Schrift. Eine absolute Rasterung nach x-Position waere
 * anfaellig fuer Unterschneidung und wuerde Woerter zerreissen.
 */
final class LayoutTextStripper extends PDFTextStripper {

    /** Luecken unterhalb dieses Anteils einer Leerzeichenbreite sind Unterschneidung, kein Abstand. */
    private static final float SPACE_THRESHOLD = 0.3f;

    /** Notbreite, falls die Schrift keine Leerzeichenbreite meldet. */
    private static final float FALLBACK_SPACE_WIDTH = 3.0f;

    private final List<TextPosition> currentLine = new ArrayList<>();

    LayoutTextStripper() throws IOException {
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) {
        currentLine.addAll(textPositions);
    }

    /** Wortabstaende ergeben sich aus den Glyphenpositionen und duerfen nicht doppelt entstehen. */
    @Override
    protected void writeWordSeparator() {
        // bewusst leer
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        flushLine();
        super.writeLineSeparator();
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        flushLine();
        super.endPage(page);
    }

    private void flushLine() throws IOException {
        if (currentLine.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        Float previousEnd = null;
        for (TextPosition position : currentLine) {
            float spaceWidth = spaceWidthOf(position);
            float start = position.getXDirAdj();
            line.append(" ".repeat(gapInSpaces(previousEnd, start, spaceWidth)));
            line.append(position.getUnicode());
            previousEnd = start + position.getWidthDirAdj();
        }
        currentLine.clear();
        super.writeString(line.toString().stripTrailing());
    }

    private static int gapInSpaces(Float previousEnd, float start, float spaceWidth) {
        float gap = previousEnd == null ? start : start - previousEnd;
        if (gap <= 0) {
            return 0;
        }
        int spaces = Math.round(gap / spaceWidth);
        boolean isSeparator = gap >= SPACE_THRESHOLD * spaceWidth;
        return isSeparator ? Math.max(spaces, 1) : 0;
    }

    private static float spaceWidthOf(TextPosition position) {
        float width = position.getWidthOfSpace();
        return width > 0 ? width : FALLBACK_SPACE_WIDTH;
    }
}
