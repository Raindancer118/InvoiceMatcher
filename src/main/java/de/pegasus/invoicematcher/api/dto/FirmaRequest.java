package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.CompanyProperties;
import jakarta.validation.constraints.NotBlank;

/** Firmendaten aus der Einstellungen-Oberflaeche. */
public record FirmaRequest(@NotBlank String name, String ustIdNr, String steuernummer) {

    public CompanyProperties toCompanyProperties() {
        return new CompanyProperties(name, ustIdNr, steuernummer);
    }
}
