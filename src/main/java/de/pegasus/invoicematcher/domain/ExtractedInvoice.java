package de.pegasus.invoicematcher.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Das formatunabhaengige Ergebnis der Belegextraktion. Jeder {@code InvoiceExtractor} liefert
 * dieses Modell, egal ob die Daten aus UBL, CII oder aus PDF-Text stammen. Alle Felder duerfen
 * {@code null} sein - genau daran erkennt das Regelwerk fehlende Pflichtangaben.
 */
public record ExtractedInvoice(SourceFormat sourceFormat,
                               String documentTitle,
                               String invoiceNumber,
                               LocalDate invoiceDate,
                               Party seller,
                               Party buyer,
                               MonetaryTotals totals,
                               List<InvoiceLine> lines,
                               String rawText) {

    public ExtractedInvoice {
        seller = seller == null ? Party.UNKNOWN : seller;
        buyer = buyer == null ? Party.UNKNOWN : buyer;
        totals = totals == null ? MonetaryTotals.EMPTY : totals;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public Optional<String> title() {
        return Optional.ofNullable(documentTitle).filter(t -> !t.isBlank());
    }

    /** Der Beteiligte, dessen Personenkonto zu bestimmen ist. */
    public Party counterparty(Direction direction) {
        return direction == Direction.AUSGANG ? buyer : seller;
    }

    public static Builder builder(SourceFormat sourceFormat) {
        return new Builder(sourceFormat);
    }

    public static final class Builder {
        private final SourceFormat sourceFormat;
        private String documentTitle;
        private String invoiceNumber;
        private LocalDate invoiceDate;
        private Party seller = Party.UNKNOWN;
        private Party buyer = Party.UNKNOWN;
        private MonetaryTotals totals = MonetaryTotals.EMPTY;
        private List<InvoiceLine> lines = List.of();
        private String rawText = "";

        private Builder(SourceFormat sourceFormat) {
            this.sourceFormat = sourceFormat;
        }

        public Builder documentTitle(String value) {
            this.documentTitle = value;
            return this;
        }

        public Builder invoiceNumber(String value) {
            this.invoiceNumber = value;
            return this;
        }

        public Builder invoiceDate(LocalDate value) {
            this.invoiceDate = value;
            return this;
        }

        public Builder seller(Party value) {
            this.seller = value;
            return this;
        }

        public Builder buyer(Party value) {
            this.buyer = value;
            return this;
        }

        public Builder totals(MonetaryTotals value) {
            this.totals = value;
            return this;
        }

        public Builder lines(List<InvoiceLine> value) {
            this.lines = value;
            return this;
        }

        public Builder rawText(String value) {
            this.rawText = value;
            return this;
        }

        public ExtractedInvoice build() {
            return new ExtractedInvoice(sourceFormat, documentTitle, invoiceNumber, invoiceDate,
                    seller, buyer, totals, lines, rawText);
        }
    }
}
