package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.CompanyProperties;

/** Firmendaten fuer die Einstellungen-Oberflaeche. */
public record FirmaResponse(String name, String ustIdNr, String steuernummer) {

    public static FirmaResponse of(CompanyProperties company) {
        return new FirmaResponse(company.name(), company.vatId(), company.taxNumber());
    }
}
