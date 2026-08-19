package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.InvoiceExtractor;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Eigenstaendige XRechnung: das XML ist der Beleg. */
@Component
@Order(XmlInvoiceExtractor.ORDER)
public class XmlInvoiceExtractor implements InvoiceExtractor {

    static final int ORDER = 10;

    private final EInvoiceXmlParser parser;

    public XmlInvoiceExtractor(EInvoiceXmlParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean supports(SourceDocument document) {
        return !document.isPdf() && document.isXml();
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document) {
        return parser.parse(document.content(), SourceFormat.XRECHNUNG);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
