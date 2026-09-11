package de.pegasus.invoicematcher.classification;

import de.pegasus.invoicematcher.classification.ai.AiClassificationResponseParser;
import de.pegasus.invoicematcher.classification.ai.InvoiceClassificationPrompt;
import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.extraction.ocr.PdfPageRenderer;
import io.github.raindancer118.aigateway.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Der fachliche Einstiegspunkt: Beleg hinein, Klassifikation und Vorkontierung heraus.
 *
 * <p>Die Klassifikation trifft die KI direkt (siehe {@code Für Toms/Tom.docx}, die selbst als
 * Prompt geschrieben ist) - kein deterministisches Regelwerk mehr dahinter. Schlagen beide
 * Eskalationsstufen (Groq, Gemini) fehl, wird der Beleg nicht verworfen, sondern zur manuellen
 * Pruefung markiert: das ist Fehlerbehandlung fuer einen Totalausfall, keine Zweitentscheidung.
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    /** Seiten werden nur fuer gescannte Belege gerendert - vision-fähige Modelle sehen dann das Original. */
    private static final int SCAN_DPI = 200;
    private static final int MAX_SCAN_PAGES = 3;

    private final ExtractionService extraction;
    private final InvoiceClassifier classifier;
    private final AiClassificationResponseParser parser;
    private final Supplier<CompanyProperties> company;
    private final Supplier<AccountingProperties> accounting;
    private final Supplier<PartnerProperties> partners;
    private final PdfPageRenderer pageRenderer;

    /**
     * Die Settings-Zugriffe sind als {@link Supplier} typisiert, nicht als konkrete
     * {@code *SettingsService}-Klassen: die implementieren {@link Supplier} bereits genau dafuer
     * (siehe deren Klassendoku), und diese Klasse braucht nur den aktuellen Wert, keine
     * Persistenzschicht.
     */
    public ClassificationService(ExtractionService extraction, InvoiceClassifier classifier,
                                 AiClassificationResponseParser parser, Supplier<CompanyProperties> company,
                                 Supplier<AccountingProperties> accounting, Supplier<PartnerProperties> partners) {
        this.extraction = extraction;
        this.classifier = classifier;
        this.parser = parser;
        this.company = company;
        this.accounting = accounting;
        this.partners = partners;
        this.pageRenderer = new PdfPageRenderer(SCAN_DPI, MAX_SCAN_PAGES);
    }

    public ClassificationReport classify(SourceDocument document) {
        ExtractedInvoice invoice = extraction.extract(document);
        List<BufferedImage> pages = invoice.sourceFormat().isRecognized() ? renderPages(document) : List.of();
        PartnerProperties currentPartners = partners.get();
        ChatRequest request = InvoiceClassificationPrompt.build(invoice, company.get(), accounting.get(),
                currentPartners, pages);

        ClassificationResult result;
        List<Finding> findings;
        Optional<InvoiceClassifier.Result> response = classifier.classify(request);
        if (response.isPresent()) {
            try {
                AiClassificationResponseParser.Parsed parsed = parser.parse(response.get().content(),
                        currentPartners, response.get().provider(), response.get().model());
                result = parsed.result();
                findings = parsed.findings();
            } catch (RuntimeException e) {
                log.warn("KI-Antwort fuer '{}' konnte nicht verarbeitet werden: {}", document.filename(),
                        e.getMessage());
                result = unclassifiable();
                findings = List.of(Finding.violation("ki", "KI-Antwort war nicht verwertbar: " + e.getMessage()));
            }
        } else {
            log.warn("KI-Klassifikation fuer '{}' nicht verfuegbar - zur manuellen Pruefung markiert",
                    document.filename());
            result = unclassifiable();
            findings = List.of(Finding.violation("ki", "KI-Klassifikation nicht verfuegbar (kein Schluessel "
                    + "hinterlegt oder beide Modelle nicht erreichbar) - Beleg zur manuellen Pruefung"));
        }

        log.info("Beleg '{}' klassifiziert als {} (Konfidenz {})",
                document.filename(), result.documentType().label(), result.konfidenzScore());
        return new ClassificationReport(document.filename(), summaryOf(invoice), result, findings);
    }

    /** Ohne verwertbare KI-Antwort bleibt nur die manuelle Pruefung - Richtung EINGANG als sicherer Default. */
    private ClassificationResult unclassifiable() {
        return new ClassificationResult(DocumentType.RECHNUNGSEINGANG_ZUR_PRUEFUNG, null, null, null,
                List.of(), null, BigDecimal.ZERO);
    }

    private List<BufferedImage> renderPages(SourceDocument document) {
        try {
            return pageRenderer.render(document.content());
        } catch (RuntimeException e) {
            log.warn("Beleg '{}' konnte nicht gerastert werden, KI-Klassifikation laeuft ohne Bild: {}",
                    document.filename(), e.getMessage());
            return List.of();
        }
    }

    private ClassificationReport.Source summaryOf(ExtractedInvoice invoice) {
        return new ClassificationReport.Source(
                invoice.sourceFormat(),
                invoice.documentTitle(),
                invoice.seller().name(),
                invoice.seller().vatId(),
                invoice.buyer().name(),
                invoice.invoiceDate(),
                invoice.totals().net(),
                invoice.totals().tax(),
                invoice.totals().gross(),
                invoice.lines().size());
    }
}
