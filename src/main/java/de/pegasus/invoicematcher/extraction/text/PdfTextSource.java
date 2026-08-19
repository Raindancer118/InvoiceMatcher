package de.pegasus.invoicematcher.extraction.text;

/**
 * Liefert den Text eines PDF-Belegs.
 *
 * <p>Eigene Abstraktion, damit die Textheuristik ohne PDF-Bibliothek getestet werden kann und
 * spaeter eine OCR-Stufe fuer gescannte Belege daneben treten kann.
 */
@FunctionalInterface
public interface PdfTextSource {

    String extractText(byte[] pdf);
}
