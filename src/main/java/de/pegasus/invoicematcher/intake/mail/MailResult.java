package de.pegasus.invoicematcher.intake.mail;

/** Was aus einer Nachricht geworden ist - und damit, wohin sie im Postfach wandert. */
public enum MailResult {

    /** Mindestens ein Beleg ist in den Pruefablauf gegangen. */
    VERARBEITET,

    /** Kein Anhang, der ein Beleg sein koennte - z.B. eine Rueckfrage an die Buchhaltung. */
    OHNE_BELEG,

    /** Der Absender steht nicht auf der Liste der Einlieferungsberechtigten. */
    ABGEWIESEN,

    /** Der Beleg war nicht lesbar oder zu gross. */
    FEHLGESCHLAGEN;

    public boolean isProcessed() {
        return this == VERARBEITET;
    }
}
