package de.pegasus.invoicematcher.rules;

import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Der Arbeitsstand einer Belegklassifikation, waehrend die Regeln durchlaufen.
 *
 * <p>Bewusst veraenderlich und bewusst nur innerhalb eines Durchlaufs sichtbar: die Regeln
 * bauen das Ergebnis gemeinsam auf, nach aussen gibt es nur das fertige, unveraenderliche
 * {@code ClassificationResult}.
 */
public final class ClassificationContext {

    /** DATEV erwartet das Belegdatum als TTMMJJJJ ohne Trennzeichen. */
    private static final DateTimeFormatter BELEGDATUM = DateTimeFormatter.ofPattern("ddMMuuuu");

    private final ExtractedInvoice invoice;
    private final List<Finding> findings = new ArrayList<>();

    private Direction direction = Direction.EINGANG;
    private DocumentType documentType = DocumentType.RECHNUNGSEINGANG;
    private Integer personenkonto;
    private String buchungstextKopf;
    private List<BookingLine> bookingLines = List.of();
    private boolean stopped;
    private double confidencePenalty;

    public ClassificationContext(ExtractedInvoice invoice) {
        this.invoice = invoice;
    }

    public ExtractedInvoice invoice() {
        return invoice;
    }

    public Direction direction() {
        return direction;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public Integer personenkonto() {
        return personenkonto;
    }

    public String buchungstextKopf() {
        return buchungstextKopf;
    }

    public List<BookingLine> bookingLines() {
        return bookingLines;
    }

    public List<Finding> findings() {
        return List.copyOf(findings);
    }

    /** Das Belegdatum im DATEV-Format, oder {@code null} bei "Sonstige" bzw. fehlendem Datum. */
    public String belegdatum() {
        LocalDate date = invoice.invoiceDate();
        return documentType == DocumentType.SONSTIGE || date == null ? null : date.format(BELEGDATUM);
    }

    public String belegfeld1() {
        return documentType == DocumentType.SONSTIGE ? null : invoice.invoiceNumber();
    }

    /** Summe aller Konfidenzabschlaege, die Regeln waehrend des Durchlaufs vergeben haben. */
    public double confidencePenalty() {
        return confidencePenalty;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void direction(Direction value) {
        this.direction = value;
        if (documentType != DocumentType.SONSTIGE) {
            this.documentType = documentType.isReview()
                    ? DocumentType.bookableFor(value).toReview()
                    : DocumentType.bookableFor(value);
        }
    }

    public void personenkonto(Integer konto) {
        this.personenkonto = konto;
    }

    public void buchungstextKopf(String text) {
        this.buchungstextKopf = text;
    }

    public void bookingLines(List<BookingLine> lines) {
        this.bookingLines = List.copyOf(lines);
    }

    /** Der Beleg ist erkennbar keine Rechnung: Abbruch ohne jegliche Buchungsdaten. */
    public void markAsSonstige(Finding finding) {
        this.documentType = DocumentType.SONSTIGE;
        this.personenkonto = null;
        this.buchungstextKopf = null;
        this.bookingLines = List.of();
        this.stopped = true;
        findings.add(finding);
    }

    /** Der Beleg ist eine Rechnung mit Mangel: Belegtyp wird zur Pruefung, Buchungszeilen bleiben leer. */
    public void requireReview(Finding finding) {
        this.documentType = documentType.toReview();
        this.bookingLines = List.of();
        findings.add(finding);
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
    }

    public void penalize(double penalty, Finding finding) {
        this.confidencePenalty += penalty;
        findings.add(finding);
    }

    public void penalize(double penalty) {
        this.confidencePenalty += penalty;
    }
}
