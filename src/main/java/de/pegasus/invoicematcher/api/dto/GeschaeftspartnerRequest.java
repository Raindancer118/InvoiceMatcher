package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.PartnerProperties;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/** Die Geschaeftspartnerliste als Roh-JSON aus der Einstellungen-Oberflaeche. */
public record GeschaeftspartnerRequest(@NotBlank String json) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public PartnerProperties toPartnerProperties() {
        try {
            return MAPPER.readValue(json, PartnerProperties.class);
        } catch (MismatchedInputException e) {
            throw new IllegalArgumentException("Partnerliste-JSON passt nicht zur erwarteten Form: "
                    + e.getMessage());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Partnerliste ist kein gueltiges JSON: " + e.getMessage());
        }
    }
}
