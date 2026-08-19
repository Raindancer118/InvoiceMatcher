package de.pegasus.invoicematcher.extraction.einvoice;

import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Einbettungszugriff ueber die Mustang-Bibliothek (ZUGFeRD/Factur-X-Referenzimplementierung). */
@Component
public class MustangEmbeddedXmlSource implements EmbeddedXmlSource {

    @Override
    public Optional<byte[]> extractXml(byte[] pdf) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(pdf)) {
            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
            importer.setInputStream(in);
            if (!importer.hasXMLFileAttachment()) {
                return Optional.empty();
            }
            String xml = importer.getUTF8();
            return xml == null || xml.isBlank()
                    ? Optional.empty()
                    : Optional.of(xml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Ein PDF ohne gueltige Einbettung ist kein Fehler, sondern ein reines PDF.
            return Optional.empty();
        }
    }
}
