package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.MailIntakeProperties;

import java.util.List;

/** Belegannahme-per-Mail-Einstellungen fuer die Oberflaeche - nie das Passwort selbst. */
public record MailEingangResponse(boolean aktiv, String protokoll, String host, Integer port, String benutzername,
                                  boolean passwortGesetzt, String ordner, String verarbeitetOrdner,
                                  String fehlgeschlagenOrdner, long taktSekunden, int maxNachrichtenProDurchlauf,
                                  long maxAnhangGroesseKb, List<String> erlaubteAbsender) {

    public static MailEingangResponse of(MailIntakeProperties mail) {
        return new MailEingangResponse(mail.enabled(), mail.protocol(), mail.host(), mail.port(), mail.username(),
                mail.password() != null && !mail.password().isBlank(), mail.folder(), mail.processedFolder(),
                mail.failedFolder(), mail.pollInterval().toSeconds(), mail.maxMessagesPerPoll(),
                mail.maxAttachmentSize().toKilobytes(), mail.allowedSenders());
    }
}
