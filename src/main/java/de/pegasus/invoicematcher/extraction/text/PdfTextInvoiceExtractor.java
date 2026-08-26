package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;
import de.pegasus.invoicematcher.extraction.InvoiceExtractor;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Letzte Stufe der Formatweiche: ein PDF ohne strukturierte Rechnungsdaten wird ueber seinen
 * Text ausgewertet.
 */
@Component
@Order(PdfTextInvoiceExtractor.ORDER)
public class PdfTextInvoiceExtractor implements InvoiceExtractor {

    static final int ORDER = 30;

    private final PdfTextSource textSource;
    private final TextInvoiceAssembler assembler;

    public PdfTextInvoiceExtractor(PdfTextSource textSource, TextInvoiceAssembler assembler) {
        this.textSource = textSource;
        this.assembler = assembler;
    }

    @Override
    public boolean supports(SourceDocument document) {
        return document.isPdf();
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document) {
        PdfText text = textSource.extractText(document.content());
        return assembler.assemble(text.text(), text.recognized() ? SourceFormat.SCAN : SourceFormat.PDF);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
