package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;

/**
 * Ein Personenkonto aus den Stammdaten.
 *
 * @param konto     Kreditor (70000 ff.) bzw. Debitor (10000 ff.)
 * @param name      Name laut Stammdaten
 * @param direction Belegrichtung, fuer die dieses Konto gilt
 * @param vatId     USt-IdNr., falls gepflegt
 * @param taxNumber Steuernummer, falls gepflegt
 */
public record BusinessPartner(int konto, String name, Direction direction, String vatId, String taxNumber) {

    public BusinessPartner(int konto, String name, Direction direction) {
        this(konto, name, direction, null, null);
    }
}
