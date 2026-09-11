package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.AccountingProperties;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/** Der Kontenrahmen als Roh-JSON aus der Einstellungen-Oberflaeche. */
public record KontenrahmenRequest(@NotBlank String json) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public AccountingProperties toAccountingProperties() {
        try {
            return MAPPER.readValue(json, AccountingProperties.class);
        } catch (MismatchedInputException e) {
            throw new IllegalArgumentException("Kontenrahmen-JSON passt nicht zur erwarteten Form: "
                    + e.getMessage());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Kontenrahmen ist kein gueltiges JSON: " + e.getMessage());
        }
    }
}
