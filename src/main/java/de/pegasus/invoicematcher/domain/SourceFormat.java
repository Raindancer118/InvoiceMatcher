package de.pegasus.invoicematcher.domain;

/** Herkunftsformat des Belegs; bestimmt die Zuverlaessigkeit der extrahierten Daten. */
public enum SourceFormat {
    /** Reines Bild-/Text-PDF ohne strukturierte Rechnungsdaten. */
    PDF(false),
    /** PDF/A-3 mit eingebettetem CII-XML (ZUGFeRD / Factur-X). */
    ZUGFERD(true),
    /** Eigenstaendiges XML nach EN16931 (UBL oder CII). */
    XRECHNUNG(true);

    private final boolean structured;

    SourceFormat(boolean structured) {
        this.structured = structured;
    }

    /** Strukturierte Formate liefern maschinenlesbare Felder statt Textheuristik. */
    public boolean isStructured() {
        return structured;
    }
}
