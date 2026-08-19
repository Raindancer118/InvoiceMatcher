package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Abbildung fuer UBL 2.1 - das Syntaxprofil der XRechnung. */
public final class UblInvoiceMapping implements XmlInvoiceMapping {

    private static final List<String> ROOTS = List.of("Invoice", "CreditNote");

    @Override
    public boolean supports(XmlReader root) {
        return ROOTS.contains(root.name());
    }

    @Override
    public ExtractedInvoice map(XmlReader root, SourceFormat format, String rawXml) {
        boolean creditNote = "CreditNote".equals(root.name());
        String typeCode = root.text(creditNote ? "CreditNoteTypeCode" : "InvoiceTypeCode").orElse(null);

        return ExtractedInvoice.builder(format)
                .documentTitle(creditNote ? "Gutschrift" : InvoiceTypeCodes.toTitle(typeCode))
                .invoiceNumber(root.text("ID").orElse(null))
                .invoiceDate(root.text("IssueDate").map(LocalDate::parse).orElse(null))
                .seller(party(root, "AccountingSupplierParty"))
                .buyer(party(root, "AccountingCustomerParty"))
                .totals(totals(root))
                .lines(lines(root, creditNote))
                .rawText(rawXml)
                .build();
    }

    private Party party(XmlReader root, String role) {
        Optional<XmlReader> party = root.child(role, "Party");
        if (party.isEmpty()) {
            return Party.UNKNOWN;
        }
        XmlReader node = party.get();
        return new Party(
                node.text("PartyLegalEntity", "RegistrationName")
                        .or(() -> node.text("PartyName", "Name"))
                        .orElse(null),
                companyId(node, "VAT").orElse(null),
                companyId(node, "FC").orElse(null));
    }

    /** UBL fuehrt USt-IdNr. und Steuernummer als gleichartige Schemata nebeneinander. */
    private Optional<String> companyId(XmlReader party, String scheme) {
        return party.children("PartyTaxScheme").stream()
                .filter(taxScheme -> taxScheme.text("TaxScheme", "ID").map(scheme::equals).orElse(false))
                .findFirst()
                .flatMap(taxScheme -> taxScheme.text("CompanyID"));
    }

    private MonetaryTotals totals(XmlReader root) {
        Optional<XmlReader> monetary = root.child("LegalMonetaryTotal");
        return new MonetaryTotals(
                monetary.flatMap(m -> m.decimal("TaxExclusiveAmount")).orElse(null),
                root.child("TaxTotal").flatMap(t -> t.decimal("TaxAmount")).orElse(null),
                monetary.flatMap(m -> m.decimal("TaxInclusiveAmount")).orElse(null));
    }

    private List<InvoiceLine> lines(XmlReader root, boolean creditNote) {
        String lineElement = creditNote ? "CreditNoteLine" : "InvoiceLine";
        String quantityElement = creditNote ? "CreditedQuantity" : "InvoicedQuantity";
        return root.children(lineElement).stream()
                .map(line -> new InvoiceLine(
                        line.text("Item", "Name").orElse(null),
                        line.decimal(quantityElement).orElse(null),
                        line.child("Price").flatMap(p -> p.decimal("PriceAmount")).orElse(null),
                        line.decimal("LineExtensionAmount").orElse(null),
                        line.child("Item", "ClassifiedTaxCategory").flatMap(c -> c.decimal("Percent")).orElse(null)))
                .toList();
    }
}
