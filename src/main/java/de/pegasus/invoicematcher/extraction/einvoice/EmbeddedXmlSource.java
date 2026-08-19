package de.pegasus.invoicematcher.extraction.einvoice;

import java.util.Optional;

/**
 * Holt das eingebettete Rechnungs-XML aus einem PDF/A-3.
 *
 * <p>Als eigener Port formuliert, weil die Einbettungsregeln (Dateiname, AFRelationship,
 * XMP-Metadaten) sich zwischen ZUGFeRD-Versionen unterscheiden und von einer Fachbibliothek
 * gepflegt werden sollen.
 */
@FunctionalInterface
public interface EmbeddedXmlSource {

    Optional<byte[]> extractXml(byte[] pdf);
}
