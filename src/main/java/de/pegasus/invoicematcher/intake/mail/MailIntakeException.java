package de.pegasus.invoicematcher.intake.mail;

/** Eine Nachricht liess sich nicht verwerten; sie bleibt zur Ansicht im Postfach liegen. */
public class MailIntakeException extends RuntimeException {

    public MailIntakeException(String message) {
        super(message);
    }

    public MailIntakeException(String message, Throwable cause) {
        super(message, cause);
    }
}
