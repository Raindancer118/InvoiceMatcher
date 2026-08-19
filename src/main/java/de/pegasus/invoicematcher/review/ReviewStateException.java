package de.pegasus.invoicematcher.review;

/** Der Pruefvorgang laesst den gewuenschten Uebergang nicht zu. */
public class ReviewStateException extends RuntimeException {

    public ReviewStateException(String message) {
        super(message);
    }
}
