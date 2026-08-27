package de.pegasus.invoicematcher.intake.mail;

import java.util.function.Function;

/**
 * Das Postfach, aus dem Belege kommen.
 *
 * <p>Der Port umfasst bewusst den ganzen Durchlauf statt getrennter Methoden zum Abholen und
 * Ablegen: nur so bleibt die Verbindung waehrend einer Runde in einer Hand, und der Aufrufer
 * kann eine Nachricht nicht abholen, ohne ueber ihren Verbleib zu entscheiden.
 */
public interface Mailbox {

    /**
     * Reicht jede unbearbeitete Nachricht an den Bearbeiter und legt sie nach dessen Ergebnis ab.
     *
     * @return Anzahl der bearbeiteten Nachrichten
     */
    int forEachUnread(Function<IncomingMail, MailResult> handler);
}
