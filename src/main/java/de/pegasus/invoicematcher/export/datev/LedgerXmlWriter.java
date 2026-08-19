package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.config.DatevExportProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.Direction;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static de.pegasus.invoicematcher.export.datev.XmlWriting.attribute;
import static de.pegasus.invoicematcher.export.datev.XmlWriting.optionalElement;
import static de.pegasus.invoicematcher.export.datev.XmlWriting.unchecked;

/**
 * Schreibt die Belegsatzdatendatei ({@code LedgerImport}) der DATEV XML-Schnittstelle online.
 *
 * <p>Uebersetzt das interne Zwischenformat in die DATEV-Feldnamen:
 * {@code personenkonto} wird zu {@code bpAccountNo}, das Sachkonto zum Gegenkonto
 * {@code accountNo}, der Steuerschluessel zu {@code buCode}, {@code belegfeld_1} zu
 * {@code invoiceId}. Die Betraege werden brutto ausgewiesen, weil DATEV in {@code amount} den
 * Rechnungsbetrag erwartet.
 */
@Component
public class LedgerXmlWriter {

    private static final String NAMESPACE = "http://xml.datev.de/bedi/tps/ledger/v060";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION =
            NAMESPACE + " Belegverwaltung_online_ledger_import_v060.xsd";
    private static final String VERSION = "6.0";
    private static final String CURRENCY = "EUR";
    /** Das Schema begrenzt die USt-IdNr. auf 15 Zeichen ohne Sonderzeichen. */
    private static final int MAX_VAT_ID_LENGTH = 15;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** Das interne Belegdatum liegt als TTMMJJJJ vor, DATEV erwartet es nach ISO. */
    private static final DateTimeFormatter INTERNAL_DATE = DateTimeFormatter.ofPattern("ddMMuuuu");

    private final DatevExportProperties properties;
    private final VatRateResolver vatRates;

    public LedgerXmlWriter(DatevExportProperties properties, VatRateResolver vatRates) {
        this.properties = properties;
        this.vatRates = vatRates;
    }

    public String write(ClassificationReport report) {
        ClassificationResult result = report.result();
        if (!result.documentType().carriesBookingData() || result.buchungszeilen().isEmpty()) {
            throw new IllegalArgumentException(
                    "Beleg '" + report.filename() + "' traegt keine Buchungsdaten und hat keine Belegsatzdatendatei");
        }
        Direction direction = result.documentType().direction();
        LocalDate bookingDate = LocalDate.parse(result.belegdatum(), INTERNAL_DATE);
        List<BigDecimal> gross = grossAmounts(report);

        return XmlWriting.document(NAMESPACE, writer -> unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "LedgerImport");
            writer.writeDefaultNamespace(NAMESPACE);
            writer.writeNamespace("xsi", XSI);
            writer.writeAttribute(XSI, "schemaLocation", SCHEMA_LOCATION);
            attribute(writer, "version", VERSION);
            attribute(writer, "generator_info", properties.generatingSystem());
            attribute(writer, "generating_system", properties.generatingSystem());
            attribute(writer, "xml_data", properties.xmlDataNotice());

            writeConsolidate(writer, report, direction, bookingDate, gross);

            writer.writeEndElement();
        }));
    }

    private void writeConsolidate(XMLStreamWriter writer, ClassificationReport report, Direction direction,
                                  LocalDate bookingDate, List<BigDecimal> gross) {
        unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "consolidate");
            attribute(writer, "consolidatedAmount", sum(gross).toPlainString());
            attribute(writer, "consolidatedDate", bookingDate.format(ISO_DATE));
            attribute(writer, "consolidatedInvoiceId", report.result().belegfeld1());
            attribute(writer, "consolidatedCurrencyCode", CURRENCY);

            List<BookingLine> lines = report.result().buchungszeilen();
            for (int i = 0; i < lines.size(); i++) {
                writeLedgerEntry(writer, report, direction, bookingDate, lines.get(i), gross.get(i));
            }
            writer.writeEndElement();
        });
    }

    private void writeLedgerEntry(XMLStreamWriter writer, ClassificationReport report, Direction direction,
                                  LocalDate bookingDate, BookingLine line, BigDecimal gross) {
        unchecked(() -> {
            String element = DocumentEntry.LedgerKind.of(direction).extensionType();
            writer.writeStartElement(NAMESPACE, element);

            // Reihenfolge folgt dem Schema und darf nicht veraendert werden.
            optionalElement(writer, NAMESPACE, "date", bookingDate.format(ISO_DATE));
            optionalElement(writer, NAMESPACE, "amount", gross.toPlainString());
            optionalElement(writer, NAMESPACE, "accountNo", line.sachkonto());
            optionalElement(writer, NAMESPACE, "buCode", line.steuerschluessel());
            optionalElement(writer, NAMESPACE, "tax", vatRates.rateOf(line).map(LedgerXmlWriter::rate).orElse(null));
            optionalElement(writer, NAMESPACE, "information", report.result().buchungstextKopf());
            optionalElement(writer, NAMESPACE, "currencyCode", CURRENCY);
            optionalElement(writer, NAMESPACE, "invoiceId", report.result().belegfeld1());
            optionalElement(writer, NAMESPACE, "bookingText", line.buchungstext());
            optionalElement(writer, NAMESPACE, "vatId", counterpartyVatId(report, direction));
            optionalElement(writer, NAMESPACE, "bpAccountNo", report.result().personenkonto());
            if (direction == Direction.AUSGANG) {
                optionalElement(writer, NAMESPACE, "customerName", report.source().buyerName());
            } else {
                optionalElement(writer, NAMESPACE, "supplierName", report.source().sellerName());
            }

            writer.writeEndElement();
        });
    }

    /** Die USt-IdNr. des Geschaeftspartners, nicht die eigene. */
    private String counterpartyVatId(ClassificationReport report, Direction direction) {
        return direction == Direction.AUSGANG ? null : normalizeVatId(report.source().sellerVatId());
    }

    /**
     * Belege drucken die USt-IdNr. haeufig mit Leerzeichen ("DE 404 131 066"). DATEV fuehrt sie
     * ohne; passt sie danach nicht mehr in das Feld, bleibt es lieber leer als falsch gefuellt.
     */
    static String normalizeVatId(String vatId) {
        if (vatId == null) {
            return null;
        }
        String compact = vatId.replaceAll("\\s", "");
        return compact.isEmpty() || compact.length() > MAX_VAT_ID_LENGTH ? null : compact;
    }

    /**
     * Der auf dem Beleg ausgewiesene Bruttobetrag ist die Richtschnur: die zeilenweise
     * gerundeten Betraege werden auf ihn ausgerichtet, damit Zahlbetrag und Buchung
     * uebereinstimmen.
     */
    private List<BigDecimal> grossAmounts(ClassificationReport report) {
        List<BookingLine> lines = report.result().buchungszeilen();
        List<BigDecimal> net = lines.stream().map(BookingLine::umsatz).toList();
        List<BigDecimal> rates = lines.stream()
                .map(line -> vatRates.rateOf(line).orElse(null))
                .toList();
        return GrossAmounts.from(net, rates, report.source().gross());
    }

    /** Das Schema verlangt den Steuersatz mit genau zwei Nachkommastellen. */
    private static String rate(BigDecimal vatRate) {
        return vatRate.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
