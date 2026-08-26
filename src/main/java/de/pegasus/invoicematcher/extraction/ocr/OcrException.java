package de.pegasus.invoicematcher.extraction.ocr;

/** Die Zeichenerkennung konnte nicht ausgefuehrt werden oder ist fehlgeschlagen. */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
