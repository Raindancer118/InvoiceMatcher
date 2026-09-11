package de.pegasus.invoicematcher.domain;

/**
 * Die DATEV-Steuerschluessel, wie sie im Zwischenformat ({@link BookingLine#steuerschluessel()})
 * und in der KI-Antwort erscheinen. Automatikkonten leiten den Steuerschluessel selbst ab - dort
 * bleibt er {@code null}, ein zusaetzlich gesetzter Schluessel wuerde zu einer doppelten
 * Steuerermittlung fuehren.
 */
public final class TaxKeys {

    public static final int KEY_REDUCED = 8;
    public static final int KEY_STANDARD = 9;

    private TaxKeys() {
    }
}
