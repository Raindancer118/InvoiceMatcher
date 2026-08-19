package de.pegasus.invoicematcher.support;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Validiert erzeugte DATEV-Dateien gegen die offiziellen Schemata unter {@code datev-xsd}. */
public final class DatevSchema {

    public enum Kind {
        DOCUMENT("Document_v060.xsd"),
        LEDGER("Belegverwaltung_online_ledger_import_v060.xsd");

        private final String file;

        Kind(String file) {
            this.file = file;
        }
    }

    private DatevSchema() {
    }

    /** Wirft eine {@link AssertionError} mit der Schemameldung, wenn das XML nicht valide ist. */
    public static void assertValid(Kind kind, String xml) {
        try {
            URL schemaUrl = DatevSchema.class.getResource("/datev-xsd/" + kind.file);
            if (schemaUrl == null) {
                throw new IllegalStateException("Schemadatei fehlt: " + kind.file);
            }
            Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaUrl);
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (SAXException e) {
            throw new AssertionError("XML entspricht nicht dem DATEV-Schema " + kind.file + ": "
                    + e.getMessage() + System.lineSeparator() + xml, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
