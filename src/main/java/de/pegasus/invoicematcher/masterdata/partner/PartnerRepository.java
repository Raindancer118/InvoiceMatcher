package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;

import java.util.List;

/**
 * Zugriff auf die Geschaeftspartner-Stammdaten.
 *
 * <p>Als Port formuliert, damit die Stammdaten spaeter aus einer Datenbank oder direkt aus
 * DATEV kommen koennen, ohne die Kontierungslogik zu beruehren.
 */
public interface PartnerRepository {

    List<BusinessPartner> findAll(Direction direction);
}
