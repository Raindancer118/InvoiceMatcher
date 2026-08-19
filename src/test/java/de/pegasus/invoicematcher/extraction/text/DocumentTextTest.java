package de.pegasus.invoicematcher.extraction.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextTest {

    private static final String SAMPLE = """
            Friedrich Klausmuster

            Friedrich Klausmuster, Am Muster 80, 87587 Kaufbeuren

            Firma                                     Steuernr.: 156/290/44989
            Muster zu Testzwecken GmbH                USt-IdNr.: DE 404131066

            Rechnung                                  Rechnungsdatum:  04.01.2026
            """;

    @Test
    void keepsOnlyNonBlankLinesInOrder() {
        DocumentText text = DocumentText.of(SAMPLE);

        assertThat(text.lines()).hasSize(5);
        assertThat(text.lines().getFirst()).isEqualTo("Friedrich Klausmuster");
    }

    @Test
    void splitsLinesIntoColumnsAtWideGaps() {
        DocumentText text = DocumentText.of(SAMPLE);

        assertThat(text.columnsOf(2)).containsExactly("Firma", "Steuernr.: 156/290/44989");
        assertThat(text.firstColumnOf(4)).isEqualTo("Rechnung");
    }

    @Test
    void firstColumnOfSingleColumnLineIsTheWholeLine() {
        DocumentText text = DocumentText.of("Gutschrift\n");

        assertThat(text.firstColumnOf(0)).isEqualTo("Gutschrift");
    }

    @Test
    void findsFirstLineMatchingAPredicate() {
        DocumentText text = DocumentText.of(SAMPLE);

        assertThat(text.findFirstLine(line -> line.contains("USt-IdNr"))).isPresent();
        assertThat(text.findFirstLine(line -> line.contains("Skonto"))).isEmpty();
    }

    @Test
    void exposesLeadingLinesForHeaderOnlyLookups() {
        DocumentText text = DocumentText.of(SAMPLE);

        assertThat(text.leadingLines(2)).containsExactly(
                "Friedrich Klausmuster",
                "Friedrich Klausmuster, Am Muster 80, 87587 Kaufbeuren");
        assertThat(text.leadingLines(99)).hasSize(5);
    }

    @Test
    void normalisesWhitespaceButKeepsRawTextAvailable() {
        DocumentText text = DocumentText.of("  a \t b  \r\n\r\n c \n");

        assertThat(text.lines()).containsExactly("a \t b", "c");
        assertThat(text.raw()).contains("\r\n");
    }
}
