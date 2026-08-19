package de.pegasus.invoicematcher.export;

/** Es gibt keinen freigegebenen, noch nicht exportierten Beleg. */
public class NothingToExportException extends RuntimeException {

    public NothingToExportException(String message) {
        super(message);
    }
}
