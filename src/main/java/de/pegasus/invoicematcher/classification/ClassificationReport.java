package de.pegasus.invoicematcher.classification;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.SourceFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Das vollstaendige Ergebnis einer Belegverarbeitung.
 *
 * <p>{@code result} ist das DATEV-Zwischenformat und bleibt unveraendert, wie im Regelwerk
 * beschrieben. {@code findings} und {@code source} sind der Kontext, den eine Pruefoberflaeche
 * braucht - beides gehoert bewusst nicht in das Buchungsformat.
 */
public record ClassificationReport(@JsonProperty("dateiname") String filename,
                                   @JsonProperty("quelle") Source source,
                                   @JsonProperty("ergebnis") ClassificationResult result,
                                   @JsonProperty("hinweise") List<Finding> findings) {

    /**
     * Die erkannten Rohdaten des Belegs - unabhaengig davon, ob ein Buchungssatz entstand.
     */
    public record Source(SourceFormat format,
                         @JsonProperty("titel") String documentTitle,
                         @JsonProperty("aussteller") String sellerName,
                         @JsonProperty("ausstellerUstId") String sellerVatId,
                         @JsonProperty("empfaenger") String buyerName,
                         @JsonProperty("rechnungsdatum") LocalDate invoiceDate,
                         @JsonProperty("netto") BigDecimal net,
                         @JsonProperty("steuer") BigDecimal tax,
                         @JsonProperty("brutto") BigDecimal gross,
                         @JsonProperty("positionen") int lineCount) {
    }
}
