package de.pegasus.invoicematcher.extraction;

/** Der Beleg konnte nicht gelesen werden - falsches Format, defekte Datei oder leerer Inhalt. */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
