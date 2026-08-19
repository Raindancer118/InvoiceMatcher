package de.pegasus.invoicematcher.extraction.einvoice;

/** Das XML ist weder UBL noch CII - oder ueberhaupt kein wohlgeformtes XML. */
public class UnsupportedEInvoiceException extends RuntimeException {

    public UnsupportedEInvoiceException(String message) {
        super(message);
    }

    public UnsupportedEInvoiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
