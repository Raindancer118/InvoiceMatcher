package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.DocumentExtractionException;
import de.pegasus.invoicematcher.extraction.InvoiceExtractor;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ZUGFeRD-PDF: Belegbild und strukturierte Daten in einer Datei.
 *
 * <p>Greift vor der Textheuristik, denn die eingebetteten Daten sind der Textextraktion in
 * jedem Punkt ueberlegen.
 */
@Component
@Order(ZugferdPdfExtractor.ORDER)
public class ZugferdPdfExtractor implements InvoiceExtractor {

    static final int ORDER = 20;

    private final EmbeddedXmlSource embeddedXml;
    private final EInvoiceXmlParser parser;

    public ZugferdPdfExtractor(EmbeddedXmlSource embeddedXml, EInvoiceXmlParser parser) {
        this.embeddedXml = embeddedXml;
        this.parser = parser;
    }

    @Override
    public boolean supports(SourceDocument document) {
        return document.isPdf() && embeddedXml.extractXml(document.content()).isPresent();
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document) {
        byte[] xml = embeddedXml.extractXml(document.content())
                .orElseThrow(() -> new DocumentExtractionException(
                        "'" + document.filename() + "' enthaelt kein eingebettetes Rechnungs-XML"));
        return parser.parse(xml, SourceFormat.ZUGFERD);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
