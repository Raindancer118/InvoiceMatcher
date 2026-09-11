package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.masterdata.partner.BusinessPartner;
import de.pegasus.invoicematcher.masterdata.partner.DiverseAccountRange;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Uebersetzt die freie Modellantwort der KI-Klassifikation in das feste DATEV-Zwischenformat.
 *
 * <p>Bewusst nur strukturelle Pruefungen, kein fachliches Nachrechnen: die KI entscheidet, dieser
 * Parser sorgt nur dafuer, dass das Ergebnis in die Form passt, auf die sich Pruefliste, Export
 * und DATEV-XML verlassen (siehe {@link ClassificationResult}, {@link DocumentType}).
 */
@Component
public class AiClassificationResponseParser {

    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*\\})\\s*```", Pattern.DOTALL);
    private static final BigDecimal REVIEW_CEILING = new BigDecimal("0.50");

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Das Ergebnis plus die Findings, die es fuer die Pruefliste begruenden. */
    public record Parsed(ClassificationResult result, List<Finding> findings) {
    }

    public Parsed parse(String rawText, PartnerProperties partners, String provider, String model) {
        JsonNode root = parseJson(rawText);

        DocumentType documentType = parseDocumentType(root);
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.info("ki", "KI-Klassifikation: " + provider + "/" + model));
        String begruendung = textOrNull(root, "begruendung");
        if (begruendung != null && !begruendung.isBlank()) {
            findings.add(Finding.info("ki", begruendung));
        }

        if (documentType == DocumentType.SONSTIGE) {
            return new Parsed(new ClassificationResult(DocumentType.SONSTIGE, null, null, null,
                    List.of(), null, confidence(root, documentType)), findings);
        }

        String belegdatum = textOrNull(root, "belegdatum");
        String belegfeld1 = textOrNull(root, "belegfeld_1");
        List<BookingLine> lines = documentType.isReview() ? List.of() : parseBookingLines(root);

        PersonenkontoResolution personenkonto = resolvePersonenkonto(root, partners, documentType);
        String buchungstextKopf = personenkonto.buchungstextKopf() != null
                ? personenkonto.buchungstextKopf()
                : textOrNull(root, "buchungstext_kopf");

        if (!documentType.isReview() && lines.isEmpty()) {
            documentType = documentType.toReview();
            findings.add(Finding.warn("ki",
                    "KI meldete einen buchbaren Belegtyp ohne Buchungszeilen - zur Pruefung markiert"));
        }
        if (documentType.isReview()) {
            lines = List.of();
        }

        ClassificationResult result = new ClassificationResult(documentType, belegdatum, belegfeld1,
                personenkonto.konto(), lines, buchungstextKopf, confidence(root, documentType));
        return new Parsed(result, findings);
    }

    private JsonNode parseJson(String rawText) {
        String json = extractJson(rawText);
        try {
            return mapper.readTree(json);
        } catch (RuntimeException e) {
            throw new AiClassificationParseException("Modellantwort ist kein gueltiges JSON: " + e.getMessage(), e);
        }
    }

    /** Manche Modelle liefern das JSON trotz Anweisung in einem Markdown-Codeblock. */
    private String extractJson(String rawText) {
        var matcher = CODE_FENCE.matcher(rawText.strip());
        return matcher.find() ? matcher.group(1) : rawText.strip();
    }

    private DocumentType parseDocumentType(JsonNode root) {
        String label = textOrNull(root, "document_type");
        if (label == null) {
            throw new AiClassificationParseException("Modellantwort ohne document_type");
        }
        for (DocumentType type : DocumentType.values()) {
            if (type.label().equalsIgnoreCase(label.strip())) {
                return type;
            }
        }
        throw new AiClassificationParseException("Unbekannter document_type in der Modellantwort: '" + label + "'");
    }

    private List<BookingLine> parseBookingLines(JsonNode root) {
        JsonNode array = root.path("buchungszeilen");
        if (!array.isArray()) {
            return List.of();
        }
        List<BookingLine> lines = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.path("sachkonto").isNumber() || !node.path("umsatz").isNumber()) {
                throw new AiClassificationParseException("Buchungszeile ohne gueltiges Sachkonto oder Umsatz");
            }
            Integer steuerschluessel = node.path("steuerschluessel").isNull()
                    || node.path("steuerschluessel").isMissingNode()
                    ? null : node.path("steuerschluessel").asInt();
            lines.add(new BookingLine(
                    node.path("sachkonto").asInt(),
                    textOrNull(node, "soll_haben_kennzeichen"),
                    steuerschluessel,
                    new BigDecimal(node.path("umsatz").asText()),
                    textOrNull(node, "buchungstext")));
        }
        return lines;
    }

    private record PersonenkontoResolution(Integer konto, String buchungstextKopf) {
    }

    private PersonenkontoResolution resolvePersonenkonto(JsonNode root, PartnerProperties partners,
                                                          DocumentType documentType) {
        boolean unbekannt = root.path("personenkonto_unbekannt").asBoolean(false);
        if (!unbekannt) {
            JsonNode konto = root.path("personenkonto");
            return new PersonenkontoResolution(konto.isNumber() ? konto.asInt() : null, null);
        }
        String name = textOrNull(root, "personenkonto_name");
        if (documentType.direction() == null) {
            return new PersonenkontoResolution(null, null);
        }
        DiverseAccountRange range = new DiverseAccountRange(
                partners.diverse().creditorStart(), partners.diverse().debitorStart());
        BusinessPartner diverse = range.accountFor(name, documentType.direction());
        String kopf = "Kreditor/Debitor unbekannt - Stammdaten pruefen: " + (name == null || name.isBlank()
                ? "unbekannter Partner" : name);
        return new PersonenkontoResolution(diverse.konto(), kopf);
    }

    private BigDecimal confidence(JsonNode root, DocumentType documentType) {
        JsonNode node = root.path("konfidenz_score");
        if (!node.isNumber()) {
            throw new AiClassificationParseException("Modellantwort ohne konfidenz_score");
        }
        double clamped = Math.clamp(node.asDouble(), 0.0, 1.0);
        BigDecimal score = BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
        return documentType.isReview() ? score.min(REVIEW_CEILING) : score;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
