package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.extraction.text.DocumentText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractTsvLayoutTest {

    private static final String HEADER =
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    /** Ein Wort im Format der TSV-Ausgabe; nur die Rahmenwerte und die Sicherheit zaehlen. */
    private static String word(int block, int left, int top, int width, double confidence, String text) {
        return "5\t1\t" + block + "\t1\t1\t1\t" + left + "\t" + top + "\t" + width + "\t30\t"
                + confidence + "\t" + text;
    }

    @Test
    void keepsWordsOfOneLineTogetherEvenWhenTesseractSplitsThemIntoBlocks() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "Rechnung"),
                word(2, 900, 102, 160, 95, "Rechnungsdatum"));

        DocumentText text = DocumentText.of(TesseractTsvLayout.toText(tsv));

        assertThat(text.lines()).hasSize(1);
        assertThat(text.lineAt(0)).matches("Rechnung\\s{3,}Rechnungsdatum");
    }

    @Test
    void restoresColumnGapsSoTheDocumentModelCanSplitThem() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "Firma"),
                word(2, 900, 100, 100, 95, "Steuernr."));

        DocumentText text = DocumentText.of(TesseractTsvLayout.toText(tsv));

        assertThat(text.columnsOf(0)).containsExactly("Firma", "Steuernr.");
    }

    @Test
    void separatesLinesByTheirVerticalPosition() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "oben"),
                word(1, 100, 200, 90, 95, "unten"));

        assertThat(DocumentText.of(TesseractTsvLayout.toText(tsv)).lines()).containsExactly("oben", "unten");
    }

    @Test
    void dropsSingleCharactersThatAreMoreLikelyImageNoise() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "Rechnung"),
                word(1, 400, 100, 20, 20, "~"));

        assertThat(TesseractTsvLayout.toText(tsv)).doesNotContain("~");
    }

    @Test
    void keepsAnUncertainAmountBecauseADroppedOneCostsAWholeInvoiceLine() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "Meter"),
                word(1, 400, 100, 60, 19, "0,77€"));

        assertThat(TesseractTsvLayout.toText(tsv)).contains("0,77€");
    }

    @Test
    void stripsTableRulesThatTesseractReadAsCharacters() {
        String tsv = String.join("\n",
                HEADER,
                word(1, 100, 100, 90, 95, "|Echtholzfurnierkanten"),
                word(1, 400, 100, 60, 95, "0,65€"),
                word(1, 500, 100, 10, 95, "|"),
                word(1, 560, 100, 50, 95, "19%"));

        DocumentText text = DocumentText.of(TesseractTsvLayout.toText(tsv));

        assertThat(text.lineAt(0)).doesNotContain("|");
        assertThat(text.lineAt(0)).startsWith("Echtholzfurnierkanten");
        assertThat(text.lineAt(0)).contains("0,65€").contains("19%");
    }

    @Test
    void returnsEmptyTextForAnEmptyPage() {
        assertThat(TesseractTsvLayout.toText(HEADER + "\n1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t")).isEmpty();
    }
}
