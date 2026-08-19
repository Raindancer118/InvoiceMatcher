package de.pegasus.invoicematcher.review;

/** Der Bearbeitungsstand eines Belegs im Human-in-the-Loop. */
public enum ReviewStatus {
    /** Wartet auf eine menschliche Entscheidung. */
    OFFEN,
    /** Konfidenz und Regelwerk waren eindeutig - keine Pruefung noetig. */
    AUTOMATISCH_FREIGEGEBEN,
    /** Von einem Menschen freigegeben. */
    FREIGEGEBEN,
    /** Von einem Menschen abgelehnt; der Beleg wird nicht gebucht. */
    ABGELEHNT;

    public boolean isDecided() {
        return this != OFFEN;
    }
}
