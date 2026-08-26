package de.pegasus.invoicematcher.extraction.text;

/**
 * Der Text eines PDF-Belegs samt der Herkunft dieses Textes.
 *
 * <p>Ob der Text aus der Textebene des PDF stammt oder aus einer Zeichenerkennung, entscheidet
 * ueber seine Verlaesslichkeit - und damit ueber die Konfidenz des Ergebnisses. Ein blosser
 * String als Rueckgabewert wuerde diesen Unterschied verschlucken.
 *
 * @param text       der ausgelesene Belegtext
 * @param recognized {@code true}, wenn der Text aus einer Zeichenerkennung stammt
 */
public record PdfText(String text, boolean recognized) {

    /** Text aus der Textebene des PDF - so, wie ihn der Erzeuger hinterlegt hat. */
    public static PdfText embedded(String text) {
        return new PdfText(text, false);
    }

    /** Text aus einer Zeichenerkennung ueber dem Seitenbild. */
    public static PdfText recognized(String text) {
        return new PdfText(text, true);
    }
}
