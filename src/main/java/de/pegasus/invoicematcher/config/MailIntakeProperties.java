package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Belegannahme aus einem Postfach.
 *
 * <p>Abgeholt wird ueber IMAP und nicht ueber die Mailcow-Verwaltungs-API: die API verwaltet
 * Postfaecher, sie liest sie nicht. Damit haengt die Stufe an keinem bestimmten Mailserver.
 *
 * @param allowedSenders Absender, die in die Buchhaltung einliefern duerfen - Vollanschrift
 *                       ({@code buchhaltung@partner.example}) oder ganze Domaene
 *                       ({@code @partner.example}). Leer heisst: jeder darf, was fuer ein
 *                       oeffentlich erreichbares Postfach eine bewusste Entscheidung sein muss.
 */
@ConfigurationProperties("invoicematcher.intake.mail")
public record MailIntakeProperties(boolean enabled, String protocol, String host, Integer port,
                                   String username, String password, String folder,
                                   String processedFolder, String failedFolder,
                                   Duration pollInterval, int maxMessagesPerPoll,
                                   DataSize maxAttachmentSize, List<String> allowedSenders) {

    public MailIntakeProperties {
        protocol = blank(protocol) ? "imaps" : protocol.toLowerCase(Locale.ROOT);
        folder = blank(folder) ? "INBOX" : folder;
        processedFolder = blank(processedFolder) ? "Verarbeitet" : processedFolder;
        failedFolder = blank(failedFolder) ? "Fehlgeschlagen" : failedFolder;
        pollInterval = pollInterval == null ? Duration.ofMinutes(5) : pollInterval;
        maxMessagesPerPoll = maxMessagesPerPoll <= 0 ? 50 : maxMessagesPerPoll;
        maxAttachmentSize = maxAttachmentSize == null ? DataSize.ofMegabytes(20) : maxAttachmentSize;
        allowedSenders = allowedSenders == null ? List.of() : List.copyOf(allowedSenders);
        port = port == null ? ("imaps".equals(protocol) ? 993 : 143) : port;
    }

    /** Ohne Liste ist jeder Absender zugelassen; mit Liste zaehlt nur, was darin steht. */
    public boolean accepts(String sender) {
        if (allowedSenders.isEmpty()) {
            return true;
        }
        String address = addressOf(sender);
        return allowedSenders.stream().map(entry -> entry.toLowerCase(Locale.ROOT).trim())
                .anyMatch(entry -> entry.startsWith("@") ? address.endsWith(entry) : address.equals(entry));
    }

    /** Aus {@code Rechnungen <buchhaltung@example.de>} wird {@code buchhaltung@example.de}. */
    private static String addressOf(String sender) {
        if (sender == null) {
            return "";
        }
        String value = sender.trim().toLowerCase(Locale.ROOT);
        int start = value.lastIndexOf('<');
        int end = value.lastIndexOf('>');
        return start >= 0 && end > start ? value.substring(start + 1, end).trim() : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
