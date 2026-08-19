package de.pegasus.invoicematcher.masterdata.partner;

/**
 * Das Ergebnis der Personenkontenermittlung samt Begruendung.
 *
 * <p>Die Qualitaet fliesst in den Konfidenzwert ein: eine Zuordnung ueber die USt-IdNr. ist
 * belastbar, eine ueber den Namen nur wahrscheinlich, ein Diverse-Konto gar nicht.
 */
public record PartnerMatch(BusinessPartner partner, Quality quality) {

    public enum Quality {
        /** Treffer ueber USt-IdNr. oder Steuernummer - eindeutig. */
        STEUERNUMMER,
        /** Treffer ueber den Namen - wahrscheinlich, aber nicht beweisbar. */
        NAME,
        /** Kein Stammsatz gefunden; es greift das Sammelkonto nach Anfangsbuchstabe. */
        DIVERSE
    }

    public boolean isKnown() {
        return quality != Quality.DIVERSE;
    }
}
