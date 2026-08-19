package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;

/**
 * Bildet ein konkretes EN16931-Syntaxprofil auf das interne Belegmodell ab.
 *
 * <p>Neue Syntaxen (z.B. UBL CreditNote oder Order-X) lassen sich als weitere Implementierung
 * ergaenzen, ohne den Rest der Extraktionskette zu beruehren.
 */
public interface XmlInvoiceMapping {

    /** Der lokale Name des Wurzelelements, das diese Abbildung bedient. */
    boolean supports(XmlReader root);

    ExtractedInvoice map(XmlReader root, SourceFormat format, String rawXml);
}
