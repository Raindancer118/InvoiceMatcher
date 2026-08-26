package de.pegasus.invoicematcher.domain;

/** Herkunftsformat des Belegs; bestimmt die Zuverlaessigkeit der extrahierten Daten. */
public enum SourceFormat {
    /** PDF mit eigener Textebene, aber ohne strukturierte Rechnungsdaten. */
    PDF(false, false),
    /** Gescanntes PDF ohne Textebene; der Text stammt aus der Zeichenerkennung. */
    SCAN(false, true),
    /** PDF/A-3 mit eingebettetem CII-XML (ZUGFeRD / Factur-X). */
    ZUGFERD(true, false),
    /** Eigenstaendiges XML nach EN16931 (UBL oder CII). */
    XRECHNUNG(true, false);

    private final boolean structured;
    private final boolean recognized;

    SourceFormat(boolean structured, boolean recognized) {
        this.structured = structured;
        this.recognized = recognized;
    }

    /** Strukturierte Formate liefern maschinenlesbare Felder statt Textheuristik. */
    public boolean isStructured() {
        return structured;
    }

    /** Erkannter Text kann sich verlesen haben - ein Risiko, das eine Textebene nicht traegt. */
    public boolean isRecognized() {
        return recognized;
    }
}
