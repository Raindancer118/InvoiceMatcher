package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.config.DatevExportProperties;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static de.pegasus.invoicematcher.export.datev.XmlWriting.attribute;
import static de.pegasus.invoicematcher.export.datev.XmlWriting.optionalElement;
import static de.pegasus.invoicematcher.export.datev.XmlWriting.unchecked;

/**
 * Schreibt die Dokumentenverwaltungsdatei {@code document.xml} des DATEV Document-Packages.
 *
 * <p>Sie klammert je Beleg das Belegbild und - sofern vorhanden - die Belegsatzdatendatei.
 * Genau daran entscheidet DATEV, ob ein Buchungssatz entsteht oder der Beleg nur abgelegt wird.
 */
@Component
public class DocumentXmlWriter {

    private static final String NAMESPACE = "http://xml.datev.de/bedi/tps/document/v06.0";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION = NAMESPACE + " Document_v060.xsd";
    private static final String VERSION = "6.0";

    /** Das Schema verlangt Datum und Uhrzeit ohne Zeitzone. */
    private static final DateTimeFormatter ARCHIVE_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    /** Der Buchungszeitraum wird als Monat angegeben, z.B. "2026-01". */
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("uuuu-MM");

    private final DatevExportProperties properties;

    public DocumentXmlWriter(DatevExportProperties properties) {
        this.properties = properties;
    }

    public String write(List<DocumentEntry> entries) {
        return write(entries, LocalDateTime.now());
    }

    public String write(List<DocumentEntry> entries, LocalDateTime createdAt) {
        return XmlWriting.document(NAMESPACE, writer -> unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "archive");
            writer.writeDefaultNamespace(NAMESPACE);
            writer.writeNamespace("xsi", XSI);
            writer.writeAttribute(XSI, "schemaLocation", SCHEMA_LOCATION);
            attribute(writer, "version", VERSION);
            attribute(writer, "guid", UUID.randomUUID().toString());
            attribute(writer, "generatingSystem", properties.generatingSystem());

            writeHeader(writer, createdAt);

            writer.writeStartElement(NAMESPACE, "content");
            for (DocumentEntry entry : entries) {
                writeDocument(writer, entry);
            }
            writer.writeEndElement();

            writer.writeEndElement();
        }));
    }

    private void writeHeader(XMLStreamWriter writer, LocalDateTime createdAt) {
        unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "header");
            optionalElement(writer, NAMESPACE, "date", createdAt.format(ARCHIVE_DATE));
            optionalElement(writer, NAMESPACE, "consultantNumber", properties.consultantNumber());
            optionalElement(writer, NAMESPACE, "clientNumber", properties.clientNumber());
            optionalElement(writer, NAMESPACE, "clientName", properties.clientName());
            writer.writeEndElement();
        });
    }

    private void writeDocument(XMLStreamWriter writer, DocumentEntry entry) {
        unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "document");
            if (entry.hasLedger()) {
                writeLedgerExtension(writer, entry);
            }
            writer.writeStartElement(NAMESPACE, "extension");
            writer.writeAttribute(XSI, "type", "File");
            writer.writeAttribute("name", entry.imageFile());
            writer.writeEndElement();
            writer.writeEndElement();
        });
    }

    private void writeLedgerExtension(XMLStreamWriter writer, DocumentEntry entry) {
        unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "extension");
            writer.writeAttribute(XSI, "type", entry.ledgerKind().extensionType());
            writer.writeAttribute("datafile", entry.ledgerFile());
            writeProperty(writer, entry.bookingDate().format(PERIOD), "1");
            writeProperty(writer, folderOf(entry), "3");
            writer.writeEndElement();
        });
    }

    private void writeProperty(XMLStreamWriter writer, String value, String key) {
        unchecked(() -> {
            writer.writeStartElement(NAMESPACE, "property");
            writer.writeAttribute("value", value);
            writer.writeAttribute("key", key);
            writer.writeEndElement();
        });
    }

    private String folderOf(DocumentEntry entry) {
        return entry.ledgerKind() == DocumentEntry.LedgerKind.AUSGANG
                ? properties.outgoingFolder()
                : properties.incomingFolder();
    }
}
