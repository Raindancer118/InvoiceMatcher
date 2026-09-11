package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.Finding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClassificationResponseParserTest {

    private final PartnerProperties partners = new PartnerProperties(
            new PartnerProperties.Diverse(70001, 10001),
            List.of(new PartnerProperties.KnownPartner(70101, "Friedrich Klausmuster",
                    Direction.EINGANG, "DE 404131066", "156/290/44989")));

    private final AiClassificationResponseParser parser = new AiClassificationResponseParser();

    @Test
    void parsesACompleteValidResponse() {
        String raw = """
                {
                  "document_type": "Rechnungseingang",
                  "belegdatum": "15082026",
                  "belegfeld_1": "RE-2026-9941",
                  "personenkonto": 70101,
                  "personenkonto_unbekannt": false,
                  "buchungszeilen": [
                    {"sachkonto": 3402, "soll_haben_kennzeichen": "S", "steuerschluessel": null,
                     "umsatz": 240.00, "buchungstext": "Kantholz Fichte 40mm"}
                  ],
                  "buchungstext_kopf": null,
                  "konfidenz_score": 0.93,
                  "begruendung": "Titel und Pflichtangaben vollstaendig, Rechenprobe stimmt"
                }
                """;

        AiClassificationResponseParser.Parsed parsed = parser.parse(raw, partners, "google-gemini", "gemini-2.5-pro");
        ClassificationResult result = parsed.result();

        assertThat(result.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG);
        assertThat(result.belegdatum()).isEqualTo("15082026");
        assertThat(result.belegfeld1()).isEqualTo("RE-2026-9941");
        assertThat(result.personenkonto()).isEqualTo(70101);
        assertThat(result.buchungszeilen()).hasSize(1);
        assertThat(result.buchungszeilen().get(0).sachkonto()).isEqualTo(3402);
        assertThat(result.buchungszeilen().get(0).umsatz()).isEqualByComparingTo("240.00");
        assertThat(result.konfidenzScore()).isEqualByComparingTo("0.93");
        assertThat(parsed.findings()).anySatisfy(f -> assertThat(f.message()).contains("gemini-2.5-pro"));
        assertThat(parsed.findings()).anySatisfy(f -> assertThat(f.message()).contains("Rechenprobe stimmt"));
    }

    @Test
    void resolvesAnUnknownPartnerToTheDiverseAccountDeterministically() {
        String raw = """
                {
                  "document_type": "Rechnungseingang",
                  "belegdatum": "01012026",
                  "belegfeld_1": "1",
                  "personenkonto": null,
                  "personenkonto_unbekannt": true,
                  "personenkonto_name": "Dietrich Holzhandel",
                  "buchungszeilen": [
                    {"sachkonto": 3400, "soll_haben_kennzeichen": "S", "steuerschluessel": null,
                     "umsatz": 100.00, "buchungstext": "x"}
                  ],
                  "konfidenz_score": 0.7
                }
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.personenkonto()).isEqualTo(70004); // 'D' -> 70001 + 3
        assertThat(result.buchungstextKopf()).contains("Dietrich Holzhandel");
    }

    @Test
    void forcesAllBookingFieldsToNullForSonstige() {
        String raw = """
                {
                  "document_type": "Sonstige",
                  "belegdatum": "01012026",
                  "belegfeld_1": "should-be-dropped",
                  "personenkonto": 70101,
                  "buchungszeilen": [{"sachkonto": 3400, "soll_haben_kennzeichen": "S",
                    "steuerschluessel": null, "umsatz": 1.00, "buchungstext": "x"}],
                  "konfidenz_score": 0.9
                }
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.documentType()).isEqualTo(DocumentType.SONSTIGE);
        assertThat(result.belegdatum()).isNull();
        assertThat(result.belegfeld1()).isNull();
        assertThat(result.personenkonto()).isNull();
        assertThat(result.buchungszeilen()).isEmpty();
    }

    @Test
    void clearsBookingLinesForAReviewType() {
        String raw = """
                {
                  "document_type": "Rechnungseingang zur Prüfung",
                  "belegdatum": "01012026",
                  "belegfeld_1": "1",
                  "personenkonto": 70101,
                  "buchungszeilen": [{"sachkonto": 3400, "soll_haben_kennzeichen": "S",
                    "steuerschluessel": null, "umsatz": 1.00, "buchungstext": "x"}],
                  "konfidenz_score": 0.9
                }
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        assertThat(result.buchungszeilen()).isEmpty();
        assertThat(result.konfidenzScore()).isLessThanOrEqualTo(new BigDecimal("0.50"));
    }

    @Test
    void downgradesToReviewWhenABookableTypeHasNoBookingLines() {
        String raw = """
                {
                  "document_type": "Rechnungseingang",
                  "belegdatum": "01012026",
                  "belegfeld_1": "1",
                  "personenkonto": 70101,
                  "buchungszeilen": [],
                  "konfidenz_score": 0.9
                }
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.documentType()).isEqualTo(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG);
        assertThat(result.buchungszeilen()).isEmpty();
    }

    @Test
    void stripsAMarkdownCodeFenceAroundTheJson() {
        String raw = """
                Hier ist das Ergebnis:
                ```json
                {"document_type": "Sonstige", "belegdatum": null, "belegfeld_1": null,
                 "personenkonto": null, "buchungszeilen": [], "konfidenz_score": 1.0}
                ```
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.documentType()).isEqualTo(DocumentType.SONSTIGE);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("das ist kein JSON", partners, "groq", "llama"))
                .isInstanceOf(AiClassificationParseException.class);
    }

    @Test
    void rejectsAnUnknownDocumentTypeLabel() {
        String raw = """
                {"document_type": "Irgendwas", "belegdatum": null, "belegfeld_1": null,
                 "personenkonto": null, "buchungszeilen": [], "konfidenz_score": 1.0}
                """;

        assertThatThrownBy(() -> parser.parse(raw, partners, "groq", "llama"))
                .isInstanceOf(AiClassificationParseException.class);
    }

    @Test
    void rejectsAMissingKonfidenzScore() {
        String raw = """
                {"document_type": "Sonstige", "belegdatum": null, "belegfeld_1": null,
                 "personenkonto": null, "buchungszeilen": []}
                """;

        assertThatThrownBy(() -> parser.parse(raw, partners, "groq", "llama"))
                .isInstanceOf(AiClassificationParseException.class);
    }

    @Test
    void clampsAnOutOfRangeKonfidenzScore() {
        String raw = """
                {"document_type": "Sonstige", "belegdatum": null, "belegfeld_1": null,
                 "personenkonto": null, "buchungszeilen": [], "konfidenz_score": 1.7}
                """;

        ClassificationResult result = parser.parse(raw, partners, "groq", "llama").result();

        assertThat(result.konfidenzScore()).isEqualByComparingTo("1.00");
    }
}
