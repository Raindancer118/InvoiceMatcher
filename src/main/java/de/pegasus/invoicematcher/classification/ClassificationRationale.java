package de.pegasus.invoicematcher.classification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Die Gedanken der KI zu einem bereits kontierten Beleg - warum dieser Belegtyp, dieses
 * Personenkonto, diese Sachkonten.
 *
 * <p>Entsteht aus einer zweiten, getrennten Anfrage: die erste entscheidet, die zweite erklaert.
 * Getrennt, weil eine Begruendung im selben Aufruf die Entscheidung mitformt - das Modell
 * schreibt dann auf eine schon halb formulierte Erklaerung hin. Ausserdem darf die Erlaeuterung
 * ausfallen, ohne die Buchung zu gefaehrden: dieses Feld ist {@code null}, wenn die zweite
 * Anfrage scheitert (siehe {@code ClassificationRationaleService}).
 *
 * <p>Rein erklaerend und nie Teil des DATEV-Formats - was gebucht wird, steht ausschliesslich
 * in {@link de.pegasus.invoicematcher.domain.ClassificationResult}.
 *
 * @param model         Herkunft der Erlaeuterung, {@code provider/modell}
 * @param kurzfassung   ein bis zwei Saetze, die die Entscheidung tragen
 * @param schritte      die Herleitung entlang der Ablauflogik
 * @param unsicherheiten was das Modell selbst als wacklig ansieht - der Einstieg fuer den Pruefer
 * @param gegenprobe    die naechstbeste Alternative und warum sie verworfen wurde
 */
public record ClassificationRationale(@JsonProperty("modell") String model,
                                      @JsonProperty("kurzfassung") String kurzfassung,
                                      @JsonProperty("schritte") List<Schritt> schritte,
                                      @JsonProperty("unsicherheiten") List<String> unsicherheiten,
                                      @JsonProperty("gegenprobe") String gegenprobe) {

    public ClassificationRationale {
        schritte = schritte == null ? List.of() : List.copyOf(schritte);
        unsicherheiten = unsicherheiten == null ? List.of() : List.copyOf(unsicherheiten);
    }

    /** Ein Schritt der Herleitung, z.B. "Personenkonto" mit dem Weg zur Kontonummer. */
    public record Schritt(@JsonProperty("titel") String titel, @JsonProperty("text") String text) {
    }
}
