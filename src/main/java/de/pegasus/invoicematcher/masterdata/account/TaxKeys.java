package de.pegasus.invoicematcher.masterdata.account;

import java.math.BigDecimal;

/**
 * Uebersetzt einen Steuersatz in den DATEV-Steuerschluessel.
 *
 * <p>Automatikkonten leiten den Steuerschluessel selbst ab; ein zusaetzlich gesetzter
 * Schluessel wuerde dort zu einer doppelten Steuerermittlung fuehren.
 */
public final class TaxKeys {

    private static final BigDecimal REDUCED_RATE = new BigDecimal("7");
    private static final BigDecimal STANDARD_RATE = new BigDecimal("19");

    public static final int KEY_REDUCED = 8;
    public static final int KEY_STANDARD = 9;

    private TaxKeys() {
    }

    public static Integer forRate(BigDecimal vatRate, boolean automaticAccount) {
        if (automaticAccount || vatRate == null) {
            return null;
        }
        if (vatRate.compareTo(REDUCED_RATE) == 0) {
            return KEY_REDUCED;
        }
        if (vatRate.compareTo(STANDARD_RATE) == 0) {
            return KEY_STANDARD;
        }
        return null;
    }
}
