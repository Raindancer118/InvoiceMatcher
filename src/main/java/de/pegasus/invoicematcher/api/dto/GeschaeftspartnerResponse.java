package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.PartnerProperties;
import tools.jackson.databind.json.JsonMapper;

/** Die Geschaeftspartnerliste als Roh-JSON fuer die Einstellungen-Oberflaeche. */
public record GeschaeftspartnerResponse(String json) {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .build();

    public static GeschaeftspartnerResponse of(PartnerProperties partners) {
        return new GeschaeftspartnerResponse(MAPPER.writeValueAsString(partners));
    }
}
