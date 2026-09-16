package de.pegasus.invoicematcher.classification;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.SourceFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Das vollstaendige Ergebnis einer Belegverarbeitung.
 *
 * <p>{@code result} ist das DATEV-Zwischenformat und bleibt unveraendert, wie im Regelwerk
 * beschrieben. {@code findings}, {@code source} und {@code gedanken} sind der Kontext, den eine
 * Pruefoberflaeche braucht - alles drei gehoert bewusst nicht in das Buchungsformat.
 *
 * <p>{@code gedanken} ist {@code null}, wenn die erklaerende Zweitanfrage ausfiel oder es (ohne
 * verwertbare Klassifikation) nichts zu erklaeren gab - siehe {@link ClassificationRationale}.
 */
public record ClassificationReport(@JsonProperty("dateiname") String filename,
                                   @JsonProperty("quelle") Source source,
                                   @JsonProperty("ergebnis") ClassificationResult result,
                                   @JsonProperty("hinweise") List<Finding> findings,
                                   @JsonProperty("gedanken") ClassificationRationale gedanken) {

    /**
     * Der Bericht eines gerade erst angenommenen Belegs: Dateiname und sonst nichts.
     *
     * <p>Es gibt ihn, damit ein Vorgang nie ohne Bericht existiert - die Pruefliste, die Ablage
     * und der Export verlassen sich darauf. Sobald die Klassifikation durch ist, ersetzt ihr
     * Ergebnis diesen Platzhalter vollstaendig.
     */
    public static ClassificationReport wirdVerarbeitet(String filename) {
        return new ClassificationReport(filename,
                new Source(null, null, null, null, null, null, null, null, null, 0),
                new ClassificationResult(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG, null, null, null,
                        List.of(), null, BigDecimal.ZERO),
                List.of(Finding.info("annahme", "Beleg angenommen - die Klassifikation laeuft noch")),
                null);
    }

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
