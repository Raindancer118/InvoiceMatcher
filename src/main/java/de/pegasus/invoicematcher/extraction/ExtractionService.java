package de.pegasus.invoicematcher.extraction;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.extraction.einvoice.EInvoiceXmlParser;
import de.pegasus.invoicematcher.extraction.einvoice.MustangEmbeddedXmlSource;
import de.pegasus.invoicematcher.extraction.einvoice.XmlInvoiceExtractor;
import de.pegasus.invoicematcher.extraction.einvoice.ZugferdPdfExtractor;
import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.extraction.text.PdfTextInvoiceExtractor;
import de.pegasus.invoicematcher.extraction.text.TextInvoiceAssembler;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.List;

/** Waehlt fuer jeden Beleg den ersten passenden Extraktor in Prioritaetsreihenfolge. */
@Service
public class ExtractionService {

    private final List<InvoiceExtractor> extractors;

    public ExtractionService(List<InvoiceExtractor> extractors) {
        List<InvoiceExtractor> ordered = new java.util.ArrayList<>(extractors);
        ordered.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.extractors = List.copyOf(ordered);
    }

    /** Der Standardaufbau ohne Spring-Kontext - fuer Tests und eingebettete Nutzung. */
    public static ExtractionService standard() {
        EInvoiceXmlParser xmlParser = new EInvoiceXmlParser();
        return new ExtractionService(List.of(
                new XmlInvoiceExtractor(xmlParser),
                new ZugferdPdfExtractor(new MustangEmbeddedXmlSource(), xmlParser),
                new PdfTextInvoiceExtractor(new PdfBoxTextSource(), new TextInvoiceAssembler())));
    }

    public ExtractedInvoice extract(SourceDocument document) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(document))
                .findFirst()
                .orElseThrow(() -> new DocumentExtractionException(
                        "Kein Extraktor fuer '" + document.filename() + "' - unterstuetzt werden PDF, ZUGFeRD und XML"))
                .extract(document);
    }
}
