package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.AccountingProperties;
import tools.jackson.databind.json.JsonMapper;

/** Der Kontenrahmen als Roh-JSON fuer die Einstellungen-Oberflaeche. */
public record KontenrahmenResponse(String json) {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .build();

    public static KontenrahmenResponse of(AccountingProperties accounting) {
        return new KontenrahmenResponse(MAPPER.writeValueAsString(accounting));
    }
}
