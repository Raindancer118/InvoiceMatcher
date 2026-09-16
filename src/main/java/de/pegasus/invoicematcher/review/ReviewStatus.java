package de.pegasus.invoicematcher.review;

/** Der Bearbeitungsstand eines Belegs im Human-in-the-Loop. */
public enum ReviewStatus {
    /**
     * Angenommen und abgelegt, die Klassifikation laeuft noch.
     *
     * <p>Wer einen Beleg einreicht, soll nicht warten, bis die KI ihn durchgerechnet hat - der
     * Beleg ist mit der Annahme in Sicherheit, alles Weitere passiert im Hintergrund.
     */
    IN_ARBEIT,
    /** Wartet auf eine menschliche Entscheidung. */
    OFFEN,
    /** Konfidenz und Regelwerk waren eindeutig - keine Pruefung noetig. */
    AUTOMATISCH_FREIGEGEBEN,
    /** Von einem Menschen freigegeben. */
    FREIGEGEBEN,
    /** Von einem Menschen abgelehnt; der Beleg wird nicht gebucht. */
    ABGELEHNT;

    public boolean isDecided() {
        return this != OFFEN && this != IN_ARBEIT;
    }

    /** Nur ein fertig klassifizierter, offener Vorgang laesst sich entscheiden. */
    public boolean isOpenForDecision() {
        return this == OFFEN;
    }
}
