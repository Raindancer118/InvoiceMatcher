package de.pegasus.invoicematcher.extraction.einvoice;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ein schlanker, namensraum-toleranter Lesezugriff auf E-Rechnungs-XML.
 *
 * <p>UBL und CII binden ihre Praefixe frei; deshalb wird ausschliesslich ueber lokale
 * Elementnamen navigiert. Das Parsen selbst ist gegen XXE gehaertet - Belege stammen
 * definitionsgemaess aus einer nicht vertrauenswuerdigen Quelle.
 */
public final class XmlReader {

    private final Element element;

    private XmlReader(Element element) {
        this.element = element;
    }

    public static XmlReader parse(byte[] xml) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(xml)) {
            Document document = secureFactory().newDocumentBuilder().parse(in);
            document.getDocumentElement().normalize();
            return new XmlReader(document.getDocumentElement());
        } catch (Exception e) {
            throw new UnsupportedEInvoiceException("Beleg-XML konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    /** Der lokale Name des aktuellen Elements, z.B. {@code Invoice} oder {@code CrossIndustryInvoice}. */
    public String name() {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    /** Folgt einem Pfad lokaler Elementnamen und liefert das erste Kind je Ebene. */
    public Optional<XmlReader> child(String... path) {
        XmlReader current = this;
        for (String step : path) {
            List<XmlReader> children = current.children(step);
            if (children.isEmpty()) {
                return Optional.empty();
            }
            current = children.getFirst();
        }
        return Optional.of(current);
    }

    /** Alle direkten Kinder mit dem gegebenen lokalen Namen. */
    public List<XmlReader> children(String localName) {
        List<XmlReader> result = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child && localName.equals(localNameOf(child))) {
                result.add(new XmlReader(child));
            }
        }
        return result;
    }

    /** Alle Nachfahren mit dem gegebenen lokalen Namen, unabhaengig von der Schachtelungstiefe. */
    public List<XmlReader> descendants(String localName) {
        List<XmlReader> result = new ArrayList<>();
        collectDescendants(element, localName, result);
        return result;
    }

    private static void collectDescendants(Element parent, String localName, List<XmlReader> sink) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child) {
                if (localName.equals(localNameOf(child))) {
                    sink.add(new XmlReader(child));
                }
                collectDescendants(child, localName, sink);
            }
        }
    }

    public Optional<String> text(String... path) {
        return child(path).map(XmlReader::value);
    }

    public Optional<BigDecimal> decimal(String... path) {
        return text(path).map(XmlReader::toDecimal);
    }

    public Optional<String> attribute(String name) {
        String value = element.getAttribute(name);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /** Der getrimmte Textinhalt des Elements. */
    public String value() {
        String text = element.getTextContent();
        return text == null ? "" : text.strip();
    }

    public Optional<BigDecimal> decimalValue() {
        String text = value();
        return text.isEmpty() ? Optional.empty() : Optional.of(toDecimal(text));
    }

    private static BigDecimal toDecimal(String text) {
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            throw new UnsupportedEInvoiceException("Kein gueltiger Betrag im Beleg-XML: " + text, e);
        }
    }

    private static String localNameOf(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }
}
