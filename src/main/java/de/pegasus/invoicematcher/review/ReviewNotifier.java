package de.pegasus.invoicematcher.review;

/**
 * Benachrichtigt die Buchhaltung ueber einen neuen Pruefvorgang.
 *
 * <p>Bewusst ein eigener Port: ob per Mail, per Chat oder gar nicht benachrichtigt wird, ist
 * eine Betriebsentscheidung und keine Eigenschaft des Pruefablaufs.
 */
@FunctionalInterface
public interface ReviewNotifier {

    void notifyPending(ReviewCase reviewCase);
}
