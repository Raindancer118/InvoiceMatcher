package de.pegasus.invoicematcher.domain;

/**
 * Eine nachvollziehbare Begruendung fuer das Klassifikationsergebnis. Findings sind nicht
 * Teil des DATEV-Zwischenformats, sondern speisen die Human-in-the-Loop-Pruefung.
 */
public record Finding(String ruleId, Severity severity, String message) {

    public enum Severity {
        /** Reine Information, ohne Einfluss auf den Belegtyp. */
        INFO,
        /** Auffaelligkeit, die den Konfidenzwert senkt, aber nicht zur Pruefung zwingt. */
        WARNUNG,
        /** Regelverstoss: der Beleg darf keine Buchungsdaten mitfuehren. */
        VERSTOSS
    }

    public static Finding info(String ruleId, String message) {
        return new Finding(ruleId, Severity.INFO, message);
    }

    public static Finding warn(String ruleId, String message) {
        return new Finding(ruleId, Severity.WARNUNG, message);
    }

    public static Finding violation(String ruleId, String message) {
        return new Finding(ruleId, Severity.VERSTOSS, message);
    }
}
