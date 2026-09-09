package de.pegasus.invoicematcher.export;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.export.datev.DocumentEntry;
import de.pegasus.invoicematcher.export.datev.DocumentXmlWriter;
import de.pegasus.invoicematcher.export.datev.LedgerXmlWriter;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.review.ReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Schnuert das DATEV Document-Package: Belegbilder, Belegsatzdaten und die
 * Dokumentenverwaltungsdatei in einem ZIP-Archiv.
 *
 * <p>Ob ein Beleg mit Buchungssatz oder nur zur Ablage uebergeben wird, entscheidet allein sein
 * Belegtyp - genau so, wie es das Regelwerk vorsieht. Exportierte Vorgaenge werden vermerkt,
 * denn eine zweite Uebergabe wuerde denselben Beleg ein zweites Mal buchen.
 */
@Service
public class DatevExportService {

    private static final Logger log = LoggerFactory.getLogger(DatevExportService.class);

    private static final String DOCUMENT_XML = "document.xml";
    private static final String LEDGER_PREFIX = "Rechnungsdaten_";
    private static final DateTimeFormatter PACKAGE_STAMP = DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss");
    private static final DateTimeFormatter INTERNAL_DATE = DateTimeFormatter.ofPattern("ddMMuuuu");
    private static final java.util.regex.Pattern UNSAFE_NAME = java.util.regex.Pattern.compile("[^A-Za-z0-9._-]+");

    private final ReviewService review;
    private final DocumentStore documents;
    private final DocumentXmlWriter documentXml;
    private final LedgerXmlWriter ledgerXml;

    public DatevExportService(ReviewService review, DocumentStore documents,
                              DocumentXmlWriter documentXml, LedgerXmlWriter ledgerXml) {
        this.review = review;
        this.documents = documents;
        this.documentXml = documentXml;
        this.ledgerXml = ledgerXml;
    }

    /** Uebergibt alle freigegebenen, noch nicht exportierten Belege. */
    public DatevPackage exportPending() {
        List<ReviewCase> pending = review.exportableCases();
        if (pending.isEmpty()) {
            throw new NothingToExportException(
                    "Kein freigegebener Beleg offen - alle vorhandenen wurden bereits exportiert oder warten auf Pruefung");
        }
        return buildPackage(pending);
    }

    public DatevPackage export(List<String> caseIds) {
        return export(caseIds, false);
    }

    /** Erneute Uebergaben verlangen eine ausdrueckliche Auswahl; die erste Uebergabe bleibt vermerkt. */
    public DatevPackage export(List<String> caseIds, boolean repeat) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new NothingToExportException("Bitte mindestens einen Vorgang zum Export auswählen");
        }
        List<ReviewCase> selected = caseIds.stream().distinct().map(review::find).toList();
        if (repeat && selected.stream().anyMatch(c -> c.status() != ReviewStatus.FREIGEGEBEN
                && c.status() != ReviewStatus.AUTOMATISCH_FREIGEGEBEN)) {
            throw new NothingToExportException("Erneuter Export ist nur für freigegebene Vorgänge möglich");
        }
        List<ReviewCase> cases = repeat ? selected : selected.stream().filter(ReviewCase::isExportable).toList();
        if (cases.isEmpty()) {
            throw new NothingToExportException("Keiner der angegebenen Vorgaenge ist freigegeben und noch offen");
        }
        return buildPackage(cases);
    }

    private DatevPackage buildPackage(List<ReviewCase> cases) {
        LocalDateTime createdAt = LocalDateTime.now();
        List<PackagedCase> packaged = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (ReviewCase reviewCase : cases) {
            packaged.add(packageCase(reviewCase, usedNames));
        }

        byte[] zip = zip(packaged, createdAt);
        Instant exportedAt = Instant.now();
        cases.stream().filter(c -> c.exportedAt() == null)
                .forEach(c -> review.markExported(c.id(), exportedAt));

        log.info("DATEV-Paket mit {} Beleg(en) erzeugt, davon {} mit Buchungssatz",
                packaged.size(), packaged.stream().filter(entry -> entry.ledger() != null).count());
        return new DatevPackage("DATEV_Belege_" + createdAt.format(PACKAGE_STAMP) + ".zip", zip,
                packaged.stream().map(PackagedCase::caseId).toList());
    }

    private PackagedCase packageCase(ReviewCase reviewCase, Set<String> usedNames) {
        SourceDocument document = documents.load(reviewCase.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Belegbild zu Vorgang " + reviewCase.id() + " ist nicht mehr verfuegbar"));
        String imageName = uniqueName(sanitize(document.filename()), usedNames);

        ClassificationReport report = reviewCase.report();
        ClassificationResult result = report.result();
        if (!carriesBookingData(result)) {
            return new PackagedCase(reviewCase.id(), imageName, document.content(), null, null, null);
        }
        String ledgerName = uniqueName(
                LEDGER_PREFIX + sanitize(result.belegfeld1()) + ".xml", usedNames);
        return new PackagedCase(reviewCase.id(), imageName, document.content(), ledgerName,
                ledgerXml.write(report),
                new DocumentEntry(imageName, ledgerName,
                        DocumentEntry.LedgerKind.of(result.documentType().direction()),
                        LocalDate.parse(result.belegdatum(), INTERNAL_DATE)));
    }

    /** Ein Buchungssatz entsteht nur bei einem buchbaren Belegtyp mit Buchungszeilen. */
    private boolean carriesBookingData(ClassificationResult result) {
        return result.documentType().carriesBookingData()
                && !result.buchungszeilen().isEmpty()
                && result.belegdatum() != null;
    }

    private byte[] zip(List<PackagedCase> packaged, LocalDateTime createdAt) {
        List<DocumentEntry> entries = packaged.stream()
                .map(entry -> entry.documentEntry() != null
                        ? entry.documentEntry()
                        : new DocumentEntry(entry.imageName(), null, null, null))
                .toList();

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8)) {
            write(zip, DOCUMENT_XML, documentXml.write(entries, createdAt).getBytes(StandardCharsets.UTF_8));
            for (PackagedCase entry : packaged) {
                write(zip, entry.imageName(), entry.imageContent());
                if (entry.ledger() != null) {
                    write(zip, entry.ledgerName(), entry.ledger().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("DATEV-Paket konnte nicht geschrieben werden", e);
        }
        return target.toByteArray();
    }

    private void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    /** DATEV erlaubt im Paket keine Sonderzeichen in Dateinamen. */
    private static String sanitize(String name) {
        String cleaned = UNSAFE_NAME.matcher(name == null ? "" : name.strip()).replaceAll("_");
        return cleaned.isBlank() ? "Beleg" : cleaned;
    }

    /** Zwei Belege mit demselben Dateinamen wuerden sich im Archiv gegenseitig ueberschreiben. */
    private static String uniqueName(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        for (int suffix = 2; ; suffix++) {
            String candidate = String.format(Locale.ROOT, "%s_%d%s", base, suffix, extension);
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }

    private record PackagedCase(String caseId, String imageName, byte[] imageContent,
                                String ledgerName, String ledger, DocumentEntry documentEntry) {
    }
}
