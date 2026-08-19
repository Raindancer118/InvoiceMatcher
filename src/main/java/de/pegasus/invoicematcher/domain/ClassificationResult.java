package de.pegasus.invoicematcher.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Das interne Zwischenformat des Prototyps. Die Uebersetzung in die DATEV-XML-Schnittstelle
 * ist ein nachgelagerter Schritt und bewusst nicht Teil dieses Modells.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"document_type", "belegdatum", "belegfeld_1", "personenkonto",
        "buchungszeilen", "buchungstext_kopf", "konfidenz_score"})
public record ClassificationResult(@JsonProperty("document_type") DocumentType documentType,
                                   @JsonProperty("belegdatum") String belegdatum,
                                   @JsonProperty("belegfeld_1") String belegfeld1,
                                   @JsonProperty("personenkonto") Integer personenkonto,
                                   @JsonProperty("buchungszeilen") List<BookingLine> buchungszeilen,
                                   @JsonProperty("buchungstext_kopf") String buchungstextKopf,
                                   @JsonProperty("konfidenz_score") BigDecimal konfidenzScore) {

    public ClassificationResult {
        buchungszeilen = buchungszeilen == null ? List.of() : List.copyOf(buchungszeilen);
    }
}
