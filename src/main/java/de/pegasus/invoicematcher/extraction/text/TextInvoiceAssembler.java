package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.MonetaryTotals;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.domain.SourceFormat;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Setzt aus den einzelnen Feldextraktoren einen vollstaendigen Beleg zusammen.
 *
 * <p>Die Extraktoren kennen einander nicht; diese Klasse ist der einzige Ort, an dem ihre
 * Ergebnisse zusammenlaufen. Neue Felder werden hier eingehaengt, nicht in den Extraktoren.
 */
@Component
public class TextInvoiceAssembler {

    private final DocumentTitleExtractor title = new DocumentTitleExtractor();
    private final InvoiceNumberExtractor invoiceNumber = new InvoiceNumberExtractor();
    private final InvoiceDateExtractor invoiceDate = new InvoiceDateExtractor();
    private final SellerExtractor seller = new SellerExtractor();
    private final BuyerExtractor buyer = new BuyerExtractor();
    private final TotalsExtractor totals = new TotalsExtractor();
    private final LineItemExtractor lineItems = new LineItemExtractor();

    public ExtractedInvoice assemble(String rawText, SourceFormat format) {
        DocumentText text = DocumentText.of(rawText);
        return ExtractedInvoice.builder(format)
                .documentTitle(title.extract(text).orElse(null))
                .invoiceNumber(invoiceNumber.extract(text).orElse(null))
                .invoiceDate(invoiceDate.extract(text).orElse(null))
                .seller(seller.extract(text).orElse(Party.UNKNOWN))
                .buyer(buyer.extract(text).orElse(Party.UNKNOWN))
                .totals(totals.extract(text).orElse(MonetaryTotals.EMPTY))
                .lines(lineItems.extract(text).orElse(List.of()))
                .rawText(rawText)
                .build();
    }
}
