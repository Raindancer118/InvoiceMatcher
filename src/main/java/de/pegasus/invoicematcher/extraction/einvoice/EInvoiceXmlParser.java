package de.pegasus.invoicematcher.extraction.einvoice;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.SourceFormat;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Waehlt die passende Syntaxabbildung fuer ein EN16931-XML und wendet sie an.
 *
 * <p>Die Liste der Abbildungen ist der einzige Erweiterungspunkt fuer weitere Syntaxen.
 */
public final class EInvoiceXmlParser {

    private final List<XmlInvoiceMapping> mappings;

    public EInvoiceXmlParser() {
        this(List.of(new UblInvoiceMapping(), new CiiInvoiceMapping()));
    }

    public EInvoiceXmlParser(List<XmlInvoiceMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    public ExtractedInvoice parse(byte[] xml, SourceFormat format) {
        XmlReader root = XmlReader.parse(xml);
        return mappings.stream()
                .filter(mapping -> mapping.supports(root))
                .findFirst()
                .orElseThrow(() -> new UnsupportedEInvoiceException(
                        "Unbekanntes Wurzelelement '" + root.name() + "' - weder UBL noch CII"))
                .map(root, format, new String(xml, StandardCharsets.UTF_8));
    }
}
