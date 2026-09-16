package de.pegasus.invoicematcher.review;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.export.DocumentStore;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Der Human-in-the-Loop: klassifiziert einen Beleg und entscheidet, ob das Ergebnis ohne
 * menschliche Pruefung weiterlaufen darf.
 *
 * <p>Zur Pruefung geht ein Beleg aus zwei Gruenden - weil das Regelwerk einen Mangel gefunden
 * hat, oder weil die Konfidenz unter der eingestellten Schwelle liegt. Der zweite Fall ist der
 * wichtigere: er faengt das ab, was formal korrekt, aber inhaltlich unsicher ist.
 *
 * <p>Die Annahme ist von der Klassifikation getrennt: {@link #submit} legt den Beleg ab und
 * gibt ihn als {@link ReviewStatus#IN_ARBEIT} zurueck, die Klassifikation laeuft danach im
 * Hintergrund. Wer einen Stapel einreicht, wartet sonst zwei KI-Anfragen pro Beleg lang vor
 * einem haengenden Formular - und ein Abbruch dabei saehe aus, als waere der Beleg verloren.
 */
@Service
public class ReviewService {

    /** Der Name des Hintergrund-Ausfuehrers; siehe {@code ReviewConfiguration}. */
    public static final String BELEGVERARBEITUNG = "belegVerarbeitung";

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ClassificationService classification;
    private final ReviewRepository repository;
    private final ReviewProperties properties;
    private final ReviewNotifier notifier;
    private final DocumentStore documents;
    private final Executor verarbeitung;

    public ReviewService(ClassificationService classification, ReviewRepository repository,
                         ReviewProperties properties, ReviewNotifier notifier, DocumentStore documents,
                         @Qualifier(BELEGVERARBEITUNG) Executor verarbeitung) {
        this.classification = classification;
        this.repository = repository;
        this.properties = properties;
        this.notifier = notifier;
        this.documents = documents;
        this.verarbeitung = verarbeitung;
    }

    /**
     * Nimmt den Beleg an: lesen, ablegen, vermerken, zurueckmelden. Die Kontierung laeuft danach.
     *
     * <p>Gelesen wird sofort, kontiert im Hintergrund. Das Lesen ist schnell und rein oertlich,
     * und nur dabei stellt sich heraus, ob der Beleg ueberhaupt verwertbar ist - diese Antwort
     * gehoert an das Formular, nicht in einen Hintergrundlauf, den niemand mehr anschaut. Die
     * beiden KI-Anfragen dagegen dauern und haben mit dem Einreichen nichts mehr zu tun.
     *
     * <p>Zuerst das Original ablegen, dann den Vorgang eintragen - andersherum gaebe es einen
     * Moment lang einen Vorgang ohne Beleg, und genau den wuerde die Pruefliste anzeigen.
     */
    public ReviewCase submit(SourceDocument document) {
        ExtractedInvoice gelesen = classification.extract(document);
        String id = UUID.randomUUID().toString();
        documents.store(id, document);
        ReviewCase angenommen = repository.save(ReviewCase.opened(id,
                ClassificationReport.wirdVerarbeitet(document.filename()), ReviewStatus.IN_ARBEIT, Instant.now()));
        verarbeitung.execute(() -> klassifiziere(id, document, gelesen));
        return angenommen;
    }

    /**
     * Der Hintergrundschritt. Scheitert er, bleibt der Beleg als offener Pruefvorgang liegen -
     * verworfen wird er nie, dafuer ist er zu wertvoll.
     */
    private void klassifiziere(String id, SourceDocument document, ExtractedInvoice gelesen) {
        ClassificationReport report;
        try {
            report = classification.classify(document, gelesen);
        } catch (RuntimeException e) {
            log.warn("Beleg '{}' konnte nicht klassifiziert werden: {}", document.filename(), e.getMessage());
            ClassificationReport leer = ClassificationReport.wirdVerarbeitet(document.filename());
            report = new ClassificationReport(leer.filename(), leer.source(), leer.result(),
                    List.of(Finding.violation("annahme",
                            "Beleg konnte nicht verarbeitet werden: " + e.getMessage())), null);
        }
        ReviewStatus status = properties.needsReview(report.result())
                ? ReviewStatus.OFFEN
                : ReviewStatus.AUTOMATISCH_FREIGEGEBEN;
        ReviewCase fertig = repository.save(find(id).classified(report, status, Instant.now()));
        if (status == ReviewStatus.OFFEN) {
            notifier.notifyPending(fertig);
        }
    }

    /**
     * Das Original zu einem Vorgang.
     *
     * <p>Wer prueft, vergleicht den Vorschlag mit dem Beleg - ohne das Belegbild ist die
     * Pruefliste eine Liste von Behauptungen. Fehlt das Bild, ist der Vorgang unbekannt und
     * nicht etwa leer: ein Vorgang ohne sein Original duerfte es gar nicht geben.
     */
    public SourceDocument document(String id) {
        find(id);
        return documents.load(id)
                .orElseThrow(() -> new ReviewNotFoundException("Zu Vorgang " + id + " liegt kein Beleg vor"));
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
        if (reviewCase.status() == ReviewStatus.IN_ARBEIT) {
            throw new ReviewStateException("Pruefvorgang '" + id
                    + "' wird noch klassifiziert und laesst sich deshalb nicht entscheiden");
        }
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
        requireBookingLines(corrected);
        return new ClassificationReport(report.filename(), report.source(), corrected, report.findings(),
                report.gedanken());
    }

    /**
     * Ein Beleg, den die Pruefung als buchbar einstuft, braucht auch Buchungszeilen.
     *
     * <p>Ohne diese Pruefung liefe er als reine Ablage nach DATEV: der Pruefer haette den
     * Belegtyp korrigiert, die Buchung waere aber stillschweigend unterblieben.
     */
    private void requireBookingLines(ClassificationResult corrected) {
        if (corrected.documentType().carriesBookingData() && corrected.buchungszeilen().isEmpty()) {
            throw new ReviewStateException("Belegtyp '" + corrected.documentType().label()
                    + "' erwartet einen Buchungssatz, es wurden aber keine Buchungszeilen uebergeben");
        }
    }
}
