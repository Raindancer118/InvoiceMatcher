package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EInvoiceXmlParserTest {

    private static final String UBL = """
            <?xml version="1.0" encoding="utf-8"?>
            <Invoice xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                     xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
              <cbc:ID>7547</cbc:ID>
              <cbc:IssueDate>2026-05-06</cbc:IssueDate>
              <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
              <cac:AccountingSupplierParty><cac:Party>
                <cac:PartyTaxScheme><cbc:CompanyID>DE 815000921</cbc:CompanyID>
                  <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme>
                <cac:PartyTaxScheme><cbc:CompanyID>4411228895</cbc:CompanyID>
                  <cac:TaxScheme><cbc:ID>FC</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme>
                <cac:PartyLegalEntity><cbc:RegistrationName>Holzhandel Spanexempel</cbc:RegistrationName></cac:PartyLegalEntity>
              </cac:Party></cac:AccountingSupplierParty>
              <cac:AccountingCustomerParty><cac:Party>
                <cac:PartyLegalEntity><cbc:RegistrationName>Muster zu Testzwecken GmbH</cbc:RegistrationName></cac:PartyLegalEntity>
              </cac:Party></cac:AccountingCustomerParty>
              <cac:TaxTotal><cbc:TaxAmount currencyID="EUR">102.36</cbc:TaxAmount></cac:TaxTotal>
              <cac:LegalMonetaryTotal>
                <cbc:TaxExclusiveAmount currencyID="EUR">538.72</cbc:TaxExclusiveAmount>
                <cbc:TaxInclusiveAmount currencyID="EUR">641.08</cbc:TaxInclusiveAmount>
              </cac:LegalMonetaryTotal>
              <cac:InvoiceLine>
                <cbc:InvoicedQuantity unitCode="H87">5</cbc:InvoicedQuantity>
                <cbc:LineExtensionAmount currencyID="EUR">82.05</cbc:LineExtensionAmount>
                <cac:Item><cbc:Name>Pressholzplatte quadratisch</cbc:Name>
                  <cac:ClassifiedTaxCategory><cbc:Percent>19</cbc:Percent></cac:ClassifiedTaxCategory></cac:Item>
                <cac:Price><cbc:PriceAmount currencyID="EUR">16.41</cbc:PriceAmount></cac:Price>
              </cac:InvoiceLine>
              <cac:InvoiceLine>
                <cbc:InvoicedQuantity unitCode="H87">25</cbc:InvoicedQuantity>
                <cbc:LineExtensionAmount currencyID="EUR">62.50</cbc:LineExtensionAmount>
                <cac:Item><cbc:Name>Latten</cbc:Name>
                  <cac:ClassifiedTaxCategory><cbc:Percent>19</cbc:Percent></cac:ClassifiedTaxCategory></cac:Item>
                <cac:Price><cbc:PriceAmount currencyID="EUR">2.50</cbc:PriceAmount></cac:Price>
              </cac:InvoiceLine>
            </Invoice>
            """;

    private static final String CII = """
            <?xml version="1.0" encoding="utf-8"?>
            <rsm:CrossIndustryInvoice
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
                xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100"
                xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100">
              <rsm:ExchangedDocument>
                <ram:ID>21006</ram:ID>
                <ram:Name>RECHNUNG</ram:Name>
                <ram:TypeCode>380</ram:TypeCode>
                <ram:IssueDateTime><udt:DateTimeString format="102">20260512</udt:DateTimeString></ram:IssueDateTime>
              </rsm:ExchangedDocument>
              <rsm:SupplyChainTradeTransaction>
                <ram:IncludedSupplyChainTradeLineItem>
                  <ram:SpecifiedTradeProduct><ram:Name>Echtholzfurnierkanten Kiefer 18 mm</ram:Name></ram:SpecifiedTradeProduct>
                  <ram:SpecifiedLineTradeAgreement>
                    <ram:NetPriceProductTradePrice><ram:ChargeAmount>0.80</ram:ChargeAmount></ram:NetPriceProductTradePrice>
                  </ram:SpecifiedLineTradeAgreement>
                  <ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode="MTR">20</ram:BilledQuantity></ram:SpecifiedLineTradeDelivery>
                  <ram:SpecifiedLineTradeSettlement>
                    <ram:ApplicableTradeTax><ram:TypeCode>VAT</ram:TypeCode><ram:RateApplicablePercent>19</ram:RateApplicablePercent></ram:ApplicableTradeTax>
                    <ram:SpecifiedTradeSettlementLineMonetarySummation><ram:LineTotalAmount>16.00</ram:LineTotalAmount></ram:SpecifiedTradeSettlementLineMonetarySummation>
                  </ram:SpecifiedLineTradeSettlement>
                </ram:IncludedSupplyChainTradeLineItem>
                <ram:ApplicableHeaderTradeAgreement>
                  <ram:SellerTradeParty>
                    <ram:Name>Kreditortestrium GmbH</ram:Name>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="VA">DE 780874525</ram:ID></ram:SpecifiedTaxRegistration>
                    <ram:SpecifiedTaxRegistration><ram:ID schemeID="FC">181/815/52561</ram:ID></ram:SpecifiedTaxRegistration>
                  </ram:SellerTradeParty>
                  <ram:BuyerTradeParty><ram:Name>Muster zu Testzwecken GmbH</ram:Name></ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                  <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                    <ram:LineTotalAmount>335.58</ram:LineTotalAmount>
                    <ram:TaxBasisTotalAmount>335.58</ram:TaxBasisTotalAmount>
                    <ram:TaxTotalAmount currencyID="EUR">63.76</ram:TaxTotalAmount>
                    <ram:GrandTotalAmount>399.34</ram:GrandTotalAmount>
                  </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                </ram:ApplicableHeaderTradeSettlement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CrossIndustryInvoice>
            """;

    private final EInvoiceXmlParser parser = new EInvoiceXmlParser();

    @Test
    void mapsUblInvoiceIncludingDeclaredTotals() {
        ExtractedInvoice invoice = parser.parse(UBL.getBytes(StandardCharsets.UTF_8), SourceFormat.XRECHNUNG);

        assertThat(invoice.invoiceNumber()).isEqualTo("7547");
        assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 5, 6));
        assertThat(invoice.documentTitle()).isEqualTo("Rechnung");
        assertThat(invoice.seller().name()).isEqualTo("Holzhandel Spanexempel");
        assertThat(invoice.seller().vatId()).isEqualTo("DE 815000921");
        assertThat(invoice.seller().taxNumber()).isEqualTo("4411228895");
        assertThat(invoice.buyer().name()).isEqualTo("Muster zu Testzwecken GmbH");
        assertThat(invoice.totals().net()).isEqualByComparingTo("538.72");
        assertThat(invoice.totals().tax()).isEqualByComparingTo("102.36");
        assertThat(invoice.totals().gross()).isEqualByComparingTo("641.08");
        assertThat(invoice.lines()).hasSize(2);
        assertThat(invoice.lines().getFirst().description()).isEqualTo("Pressholzplatte quadratisch");
        assertThat(invoice.lines().getFirst().unitNetPrice()).isEqualByComparingTo("16.41");
        assertThat(invoice.lines().getFirst().netAmount()).isEqualByComparingTo("82.05");
        assertThat(invoice.lines().getFirst().vatRate()).isEqualByComparingTo("19");
    }

    @Test
    void mapsCiiInvoiceIncludingCompactIssueDate() {
        ExtractedInvoice invoice = parser.parse(CII.getBytes(StandardCharsets.UTF_8), SourceFormat.ZUGFERD);

        assertThat(invoice.invoiceNumber()).isEqualTo("21006");
        assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2026, 5, 12));
        assertThat(invoice.documentTitle()).isEqualTo("RECHNUNG");
        assertThat(invoice.seller().name()).isEqualTo("Kreditortestrium GmbH");
        assertThat(invoice.seller().vatId()).isEqualTo("DE 780874525");
        assertThat(invoice.seller().taxNumber()).isEqualTo("181/815/52561");
        assertThat(invoice.totals().net()).isEqualByComparingTo("335.58");
        assertThat(invoice.totals().tax()).isEqualByComparingTo("63.76");
        assertThat(invoice.totals().gross()).isEqualByComparingTo("399.34");
        assertThat(invoice.lines()).singleElement().satisfies(line -> {
            assertThat(line.description()).isEqualTo("Echtholzfurnierkanten Kiefer 18 mm");
            assertThat(line.quantity()).isEqualByComparingTo("20");
            assertThat(line.unitNetPrice()).isEqualByComparingTo("0.80");
            assertThat(line.netAmount()).isEqualByComparingTo("16.00");
            assertThat(line.vatRate()).isEqualByComparingTo("19");
        });
    }

    @Test
    void derivesGutschriftTitleFromTypeCodeWhenTheDocumentCarriesNoName() {
        String creditNote = UBL.replace("<cbc:InvoiceTypeCode>380", "<cbc:InvoiceTypeCode>381");

        assertThat(parser.parse(creditNote.getBytes(StandardCharsets.UTF_8), SourceFormat.XRECHNUNG)
                .documentTitle()).isEqualTo("Gutschrift");
    }

    @Test
    void rejectsXmlThatIsNeitherUblNorCii() {
        byte[] foreign = "<?xml version=\"1.0\"?><nonsense/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(foreign, SourceFormat.XRECHNUNG))
                .isInstanceOf(UnsupportedEInvoiceException.class)
                .hasMessageContaining("nonsense");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parser.parse("<Invoice>".getBytes(StandardCharsets.UTF_8), SourceFormat.XRECHNUNG))
                .isInstanceOf(UnsupportedEInvoiceException.class);
    }
}
