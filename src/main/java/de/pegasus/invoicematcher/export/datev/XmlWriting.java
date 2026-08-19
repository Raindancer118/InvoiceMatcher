package de.pegasus.invoicematcher.export.datev;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

/**
 * Kleiner Rahmen um {@link XMLStreamWriter}.
 *
 * <p>StAX statt Stringbau: Sonderzeichen in Firmennamen und Buchungstexten - kaufmaennisches
 * Und, Anfuehrungszeichen, Umlaute - werden korrekt maskiert, ohne dass jede Schreibstelle
 * daran denken muss.
 */
final class XmlWriting {

    private XmlWriting() {
    }

    /** Namensraum der XML-Schema-Instanz; beide DATEV-Dateien nutzen ihn fuer xsi:type. */
    static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * Erzeugt ein Dokument mit dem angegebenen Standard-Namensraum.
     *
     * <p>Die Namensraeume muessen dem Wurzelelement vorausgehen, sonst kennt der Writer beim
     * ersten {@code writeStartElement} noch kein Praefix.
     */
    static String document(String defaultNamespace, Consumer<XMLStreamWriter> body) {
        StringWriter target = new StringWriter();
        try {
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(target);
            writer.setDefaultNamespace(defaultNamespace);
            writer.setPrefix("xsi", XSI);
            writer.writeStartDocument("UTF-8", "1.0");
            body.accept(writer);
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("DATEV-XML konnte nicht geschrieben werden", e);
        }
        return target.toString();
    }

    /** Schreibt ein Element mit Textinhalt; leere Werte werden ausgelassen. */
    static void optionalElement(XMLStreamWriter writer, String namespace, String name, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            writer.writeStartElement(namespace, name);
            writer.writeCharacters(value.toString());
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Element '" + name + "' konnte nicht geschrieben werden", e);
        }
    }

    static void attribute(XMLStreamWriter writer, String name, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            writer.writeAttribute(name, value.toString());
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Attribut '" + name + "' konnte nicht geschrieben werden", e);
        }
    }

    /** Erlaubt Lambdas, die {@link XMLStreamException} werfen duerfen. */
    static void unchecked(XmlAction action) {
        try {
            action.run();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("DATEV-XML konnte nicht geschrieben werden", e);
        }
    }

    @FunctionalInterface
    interface XmlAction {
        void run() throws XMLStreamException;
    }
}
