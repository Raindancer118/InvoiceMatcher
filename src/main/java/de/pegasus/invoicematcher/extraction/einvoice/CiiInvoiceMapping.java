package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Abbildung fuer UN/CEFACT CII - das Syntaxprofil von ZUGFeRD und Factur-X. */
public final class CiiInvoiceMapping implements XmlInvoiceMapping {

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Override
    public boolean supports(XmlReader root) {
        return "CrossIndustryInvoice".equals(root.name());
    }

    @Override
    public ExtractedInvoice map(XmlReader root, SourceFormat format, String rawXml) {
        Optional<XmlReader> header = root.child("ExchangedDocument");
        Optional<XmlReader> agreement = root.child("SupplyChainTradeTransaction", "ApplicableHeaderTradeAgreement");

        return ExtractedInvoice.builder(format)
                .documentTitle(header.flatMap(h -> h.text("Name"))
                        .orElseGet(() -> InvoiceTypeCodes.toTitle(header.flatMap(h -> h.text("TypeCode")).orElse(null))))
                .invoiceNumber(header.flatMap(h -> h.text("ID")).orElse(null))
                .invoiceDate(header.flatMap(h -> h.text("IssueDateTime", "DateTimeString"))
                        .map(value -> LocalDate.parse(value, COMPACT_DATE)).orElse(null))
                .seller(party(agreement, "SellerTradeParty"))
                .buyer(party(agreement, "BuyerTradeParty"))
                .totals(totals(root))
                .lines(lines(root))
                .rawText(rawXml)
                .build();
    }

    private Party party(Optional<XmlReader> agreement, String role) {
        Optional<XmlReader> party = agreement.flatMap(a -> a.child(role));
        if (party.isEmpty()) {
            return Party.UNKNOWN;
        }
        XmlReader node = party.get();
        return new Party(
                node.text("Name").orElse(null),
                taxRegistration(node, "VA").orElse(null),
                taxRegistration(node, "FC").orElse(null));
    }

    /** CII unterscheidet USt-IdNr. ("VA") und Steuernummer ("FC") ueber das schemeID-Attribut. */
    private Optional<String> taxRegistration(XmlReader party, String schemeId) {
        return party.children("SpecifiedTaxRegistration").stream()
                .flatMap(registration -> registration.children("ID").stream())
                .filter(id -> id.attribute("schemeID").map(schemeId::equals).orElse(false))
                .findFirst()
                .map(XmlReader::value);
    }

    private MonetaryTotals totals(XmlReader root) {
        Optional<XmlReader> summation = root.child("SupplyChainTradeTransaction",
                "ApplicableHeaderTradeSettlement", "SpecifiedTradeSettlementHeaderMonetarySummation");
        return new MonetaryTotals(
                summation.flatMap(s -> s.decimal("TaxBasisTotalAmount")).orElse(null),
                summation.flatMap(s -> s.decimal("TaxTotalAmount")).orElse(null),
                summation.flatMap(s -> s.decimal("GrandTotalAmount")).orElse(null));
    }

    private List<InvoiceLine> lines(XmlReader root) {
        return root.child("SupplyChainTradeTransaction")
                .map(transaction -> transaction.children("IncludedSupplyChainTradeLineItem").stream()
                        .map(CiiInvoiceMapping::toInvoiceLine)
                        .toList())
                .orElseGet(List::of);
    }

    private static InvoiceLine toInvoiceLine(XmlReader item) {
        return new InvoiceLine(
                item.text("SpecifiedTradeProduct", "Name").orElse(null),
                item.child("SpecifiedLineTradeDelivery").flatMap(d -> d.decimal("BilledQuantity")).orElse(null),
                item.child("SpecifiedLineTradeAgreement", "NetPriceProductTradePrice")
                        .flatMap(price -> price.decimal("ChargeAmount")).orElse(null),
                item.child("SpecifiedLineTradeSettlement", "SpecifiedTradeSettlementLineMonetarySummation")
                        .flatMap(summation -> summation.decimal("LineTotalAmount")).orElse(null),
                item.child("SpecifiedLineTradeSettlement", "ApplicableTradeTax")
                        .flatMap(tax -> tax.decimal("RateApplicablePercent")).orElse(null));
    }
}
