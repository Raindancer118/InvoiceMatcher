package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import io.github.raindancer118.aigateway.ChatRequest;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Baut die zweite Anfrage: den Beleg und die bereits getroffene Kontierung vorlegen und nach dem
 * Gedankengang fragen - in der Ich-Form, so wie ein Buchhalter den Beleg durchgeht ("Oben steht
 * 'Rechnung' ... die drei Furnierposten fasse ich auf 3401 zusammen").
 *
 * <p>Bewusst ohne die Ablauflogik im Wortlaut - das Modell soll erklaeren, was entschieden wurde,
 * nicht neu entscheiden. Bekaeme es die Regeln erneut, wuerde es die Entscheidung nachrechnen und
 * im Zweifel eine andere begruenden als die, die im Buchungssatz steht.
 */
public final class RationalePrompt {

    private static final String AUFTRAG = """
            Du bist die Kontierungs-KI einer Buchhaltungssoftware. Fuer den unten stehenden Beleg
            ist die Kontierung bereits getroffene Entscheidung - sie steht fest und wird nicht mehr
            geaendert. Gib jetzt deinen Gedankengang dazu wieder: das, was du beim Lesen des Belegs
            gedacht hast, bis diese Kontierung dastand.

            Schreibe in der Ich-Form und im Praesens, als denkst du gerade laut - so, wie ein
            Buchhalter vor sich hin denkt, waehrend er den Beleg durchgeht. Also etwa: "Oben steht
            'Rechnung' und darunter eine Rechnungsnummer - das ist ein Beleg, den ich buchen kann."
            oder "Die drei Furnierposten sind alle Material zum selben Steuersatz, die fasse ich zu
            einer Zeile auf 3401 zusammen." Keine Berichtssprache, keine Passivkonstruktionen, kein
            "es wurde festgestellt". Nenne dabei immer die konkreten Zahlen, Namen und Konten, die
            du im Beleg siehst.

            Antworte AUSSCHLIESSLICH mit einem einzigen JSON-Objekt (kein Markdown, kein Text davor
            oder danach) in genau dieser Form:

            {
              "kurzfassung": "<ein bis zwei Saetze in der Ich-Form: worauf es bei diesem Beleg hinauslief>",
              "schritte": [
                {"titel": "<kurze Station, z.B. Belegart, Pflichtangaben, Rechenprobe, Personenkonto, Sachkonten, Konfidenz>",
                 "text": "<dein Gedanke an dieser Station, Ich-Form, mit Betraegen, Namen und Kontonummern>"}
              ],
              "unsicherheiten": ["<woran du haengengeblieben bist, ebenfalls Ich-Form: 'Ich bin mir nicht sicher, ob ...'>"],
              "gegenprobe": "<was du sonst noch erwogen und warum du es verworfen hast, Ich-Form>" | null
            }

            Denke die Stationen in der Reihenfolge durch, in der du den Beleg liest: erst was fuer
            ein Papier das ueberhaupt ist, dann ob alle Pflichtangaben da sind, dann die Rechenprobe,
            dann wer der Partner ist und auf welches Personenkonto er gehoert, dann jede Position
            und ihr Sachkonto, zuletzt wie sicher du dir insgesamt bist.

            Erfinde nichts, was nicht im Beleg steht - findest du fuer einen Schritt nichts im
            Dokument, dann denke genau das ("Eine Steuernummer finde ich nirgends."). Weicht die
            Kontierung von dem ab, was du am Beleg siehst, sag das in "unsicherheiten", statt es
            glattzubuegeln.
            """;

    private RationalePrompt() {
    }

    public static ChatRequest build(ExtractedInvoice invoice, ClassificationResult result,
                                    List<BufferedImage> pages) {
        String text = AUFTRAG
                + "\n\ngetroffene Kontierung:\n" + BelegContext.json(result)
                + "\n\n" + BelegContext.describe(invoice);
        return BelegContext.request(text, pages);
    }
}
