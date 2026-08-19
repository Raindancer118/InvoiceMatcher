package de.pegasus.invoicematcher.review;

/** Der angefragte Pruefvorgang existiert nicht. */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(String id) {
        super("Kein Pruefvorgang mit der Kennung '" + id + "'");
    }
}
