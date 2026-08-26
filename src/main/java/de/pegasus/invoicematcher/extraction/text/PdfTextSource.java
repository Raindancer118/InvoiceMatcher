package de.pegasus.invoicematcher.extraction.text;

/**
 * Liefert den Text eines PDF-Belegs.
 *
 * <p>Eigene Abstraktion, damit die Textheuristik ohne PDF-Bibliothek getestet werden kann und
 * eine OCR-Stufe fuer gescannte Belege daneben treten kann.
 */
@FunctionalInterface
public interface PdfTextSource {

    PdfText extractText(byte[] pdf);
}
