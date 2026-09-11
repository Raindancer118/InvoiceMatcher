package de.pegasus.invoicematcher.classification.ai;

/** Die Modellantwort liess sich nicht in ein gueltiges Klassifikationsergebnis uebersetzen. */
public class AiClassificationParseException extends RuntimeException {

    public AiClassificationParseException(String message) {
        super(message);
    }

    public AiClassificationParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
