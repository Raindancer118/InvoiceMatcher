package de.pegasus.invoicematcher.classification;

import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.RuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Der fachliche Einstiegspunkt: Beleg hinein, Klassifikation und Vorkontierung heraus.
 *
 * <p>Haelt die drei Schritte auseinander - Extraktion, Regelwerk, Konfidenz - und ist selbst
 * frei von Fachlogik.
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final ExtractionService extraction;
    private final RuleEngine ruleEngine;
    private final ConfidenceCalculator confidence;

    public ClassificationService(ExtractionService extraction, RuleEngine ruleEngine,
                                 ConfidenceCalculator confidence) {
        this.extraction = extraction;
        this.ruleEngine = ruleEngine;
        this.confidence = confidence;
    }

    public ClassificationReport classify(SourceDocument document) {
        ExtractedInvoice invoice = extraction.extract(document);
        ClassificationContext context = ruleEngine.evaluate(invoice);
        ClassificationResult result = new ClassificationResult(
                context.documentType(),
                context.belegdatum(),
                context.belegfeld1(),
                context.personenkonto(),
                context.bookingLines(),
                context.buchungstextKopf(),
                confidence.calculate(context));
        log.info("Beleg '{}' klassifiziert als {} (Konfidenz {})",
                document.filename(), result.documentType().label(), result.konfidenzScore());
        return new ClassificationReport(document.filename(), summaryOf(invoice), result, context.findings());
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
