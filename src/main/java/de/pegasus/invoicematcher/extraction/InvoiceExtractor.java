package de.pegasus.invoicematcher.extraction;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import org.springframework.core.Ordered;

/**
 * Liest einen Beleg eines bestimmten Formats in das interne Belegmodell ein.
 *
 * <p>Die Reihenfolge entscheidet: strukturierte Formate muessen vor der Textheuristik greifen,
 * weil ein ZUGFeRD-PDF technisch auch ein reines PDF ist.
 */
public interface InvoiceExtractor extends Ordered {

    boolean supports(SourceDocument document);

    ExtractedInvoice extract(SourceDocument document);
}
