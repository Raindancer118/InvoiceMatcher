package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.domain.Direction;

import java.time.LocalDate;

/**
 * Ein Eintrag der Dokumentenverwaltungsdatei: ein Belegbild, optional mit Belegsatzdaten.
 *
 * <p>Genau dieses "optional" ist der Schalter des Regelwerks: liegt eine Belegsatzdatendatei
 * bei, entsteht in DATEV ein Buchungssatz; fehlt sie, wird nur der Beleg abgelegt.
 *
 * @param imageFile  Dateiname des Belegbildes im Paket
 * @param ledgerFile Dateiname der Belegsatzdatendatei, {@code null} bei reiner Ablage
 * @param ledgerKind Richtung der Belegsatzdaten, {@code null} bei reiner Ablage
 * @param bookingDate Belegdatum; bestimmt den Buchungszeitraum
 */
public record DocumentEntry(String imageFile, String ledgerFile, LedgerKind ledgerKind, LocalDate bookingDate) {

    /** Die beiden Belegsatzarten, die dieser Dienst erzeugt. */
    public enum LedgerKind {
        EINGANG("accountsPayableLedger"),
        AUSGANG("accountsReceivableLedger");

        private final String extensionType;

        LedgerKind(String extensionType) {
            this.extensionType = extensionType;
        }

        public String extensionType() {
            return extensionType;
        }

        public static LedgerKind of(Direction direction) {
            return direction == Direction.AUSGANG ? AUSGANG : EINGANG;
        }
    }

    public boolean hasLedger() {
        return ledgerFile != null && ledgerKind != null && bookingDate != null;
    }
}
