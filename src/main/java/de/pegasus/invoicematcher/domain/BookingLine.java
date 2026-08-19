package de.pegasus.invoicematcher.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.math.BigDecimal;

/**
 * Eine Buchungszeile im DATEV-Zwischenformat.
 *
 * @param sachkonto             Aufwands- bzw. Erloeskonto (SKR03)
 * @param sollHabenKennzeichen  "S" bei Aufwand, "H" bei Erloes
 * @param steuerschluessel      {@code null} bei Automatikkonten, sonst 8 (7%) bzw. 9 (19%)
 * @param umsatz                Nettobetrag der Zeile
 * @param buchungstext          Positionstext bzw. zusammengefasster Sammeltext
 */
@JsonPropertyOrder({"sachkonto", "soll_haben_kennzeichen", "steuerschluessel", "umsatz", "buchungstext"})
public record BookingLine(@JsonProperty("sachkonto") int sachkonto,
                          @JsonProperty("soll_haben_kennzeichen") String sollHabenKennzeichen,
                          @JsonProperty("steuerschluessel") Integer steuerschluessel,
                          @JsonProperty("umsatz") BigDecimal umsatz,
                          @JsonProperty("buchungstext") String buchungstext) {

    public static final String SOLL = "S";
    public static final String HABEN = "H";

    public static String kennzeichenFor(Direction direction) {
        return direction == Direction.AUSGANG ? HABEN : SOLL;
    }
}
