package de.pegasus.invoicematcher.extraction.ocr;

import java.awt.image.BufferedImage;

/**
 * Erkennt den Text eines Seitenbildes.
 *
 * <p>Die Rueckgabe ist layouterhaltend aufgebaut wie die der PDF-Textextraktion: nebeneinander
 * stehende Bloecke bleiben durch breite Luecken trennbar, weil {@code DocumentText} genau
 * daraus die Spalten eines Belegs rekonstruiert.
 */
public interface OcrTextRecognizer {

    /** Ob die Erkennung im aktuellen Umfeld ueberhaupt ausgefuehrt werden kann. */
    boolean isAvailable();

    String recognize(BufferedImage page);
}
