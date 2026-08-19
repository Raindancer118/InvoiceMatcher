package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.export.DocumentStore;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.classification.ClassificationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Der Human-in-the-Loop: klassifiziert einen Beleg und entscheidet, ob das Ergebnis ohne
 * menschliche Pruefung weiterlaufen darf.
 *
 * <p>Zur Pruefung geht ein Beleg aus zwei Gruenden - weil das Regelwerk einen Mangel gefunden
 * hat, oder weil die Konfidenz unter der eingestellten Schwelle liegt. Der zweite Fall ist der
 * wichtigere: er faengt das ab, was formal korrekt, aber inhaltlich unsicher ist.
 */
@Service
public class ReviewService {

    private final ClassificationService classification;
    private final ReviewRepository repository;
    private final ReviewProperties properties;
    private final ReviewNotifier notifier;
    private final DocumentStore documents;

    public ReviewService(ClassificationService classification, ReviewRepository repository,
                         ReviewProperties properties, ReviewNotifier notifier, DocumentStore documents) {
        this.classification = classification;
        this.repository = repository;
        this.properties = properties;
        this.notifier = notifier;
        this.documents = documents;
    }

    public ReviewCase submit(SourceDocument document) {
        ClassificationReport report = classification.classify(document);
        ReviewStatus status = properties.needsReview(report.result())
                ? ReviewStatus.OFFEN
                : ReviewStatus.AUTOMATISCH_FREIGEGEBEN;
        ReviewCase reviewCase = repository.save(
                ReviewCase.opened(UUID.randomUUID().toString(), report, status, Instant.now()));
        documents.store(reviewCase.id(), document);
        if (status == ReviewStatus.OFFEN) {
            notifier.notifyPending(reviewCase);
        }
        return reviewCase;
    }

    public List<ReviewCase> openCases() {
        return repository.findByStatus(ReviewStatus.OFFEN);
    }

    public List<ReviewCase> allCases() {
        return repository.findAll();
    }

    /** Freigegebene Belege, die noch nicht an DATEV uebergeben wurden. */
    public List<ReviewCase> exportableCases() {
        return repository.findAll().stream().filter(ReviewCase::isExportable).toList();
    }

    /** Haelt fest, dass ein Beleg uebergeben wurde - ein zweiter Export wuerde doppelt buchen. */
    public ReviewCase markExported(String id, Instant at) {
        return repository.save(find(id).exported(at));
    }

    public ReviewCase find(String id) {
        return repository.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
    }

    public ReviewCase approve(String id, String decidedBy, ReviewDecision.Correction correction) {
        ReviewCase reviewCase = requireOpen(id);
        ClassificationReport report = correction == null
                ? reviewCase.report()
                : applyCorrection(reviewCase.report(), correction);
        return repository.save(reviewCase.decided(ReviewStatus.FREIGEGEBEN, report, decidedBy,
                correction == null ? null : "Vorschlag korrigiert", Instant.now()));
    }

    public ReviewCase reject(String id, String decidedBy, String reason) {
        ReviewCase reviewCase = requireOpen(id);
        return repository.save(reviewCase.decided(ReviewStatus.ABGELEHNT, reviewCase.report(),
                decidedBy, reason, Instant.now()));
    }

    private ReviewCase requireOpen(String id) {
        ReviewCase reviewCase = find(id);
        if (reviewCase.status().isDecided()) {
            throw new ReviewStateException("Pruefvorgang '" + id + "' wurde bereits entschieden ("
                    + reviewCase.status() + ")");
        }
        return reviewCase;
    }

    /** Eine Korrektur ersetzt nur, was der Mensch tatsaechlich angefasst hat. */
    private ClassificationReport applyCorrection(ClassificationReport report,
                                                 ReviewDecision.Correction correction) {
        ClassificationResult original = report.result();
        ClassificationResult corrected = new ClassificationResult(
                correction.documentType() != null ? correction.documentType() : original.documentType(),
                original.belegdatum(),
                original.belegfeld1(),
                original.personenkonto(),
                correction.buchungszeilen() != null ? correction.buchungszeilen() : original.buchungszeilen(),
                original.buchungstextKopf(),
                original.konfidenzScore());
        return new ClassificationReport(report.filename(), report.source(), corrected, report.findings());
    }
}
