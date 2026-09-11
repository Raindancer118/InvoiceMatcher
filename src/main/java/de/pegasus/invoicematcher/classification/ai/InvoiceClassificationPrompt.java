package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import io.github.raindancer118.aigateway.ChatContent;
import io.github.raindancer118.aigateway.ChatMessage;
import io.github.raindancer118.aigateway.ChatRequest;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Baut den Klassifikations-Prompt: die Ablauflogik aus der Ursprungsaufgabe ({@code
 * Für Toms/Tom.docx}, die selbst wortwoertlich als Prompt geschrieben ist), der aktuelle
 * Kontenrahmen und die Geschaeftspartnerliste (aus den Einstellungen, jederzeit austauschbar)
 * als JSON, sowie die extrahierten Belegdaten. Bei gescannten Belegen kommen die gerenderten
 * Seiten als Bild dazu, damit ein vision-faehiges Modell auch handschriftliche Titel direkt
 * lesen kann, statt sich auf die (bei Handschrift oft fehlerhafte) Texterkennung zu verlassen.
 */
public final class InvoiceClassificationPrompt {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String ABLAUFLOGIK = """
            Du bist die Kontierungs-KI einer Buchhaltungssoftware. Klassifiziere den folgenden Beleg
            und antworte AUSSCHLIESSLICH mit einem einzigen JSON-Objekt (kein Markdown, kein Text
            davor oder danach) in genau dieser Form:

            {
              "document_type": "Rechnungseingang" | "Rechnungsausgang" | "Rechnungseingang zur Prüfung" | "Rechnungsausgang zur Prüfung" | "Sonstige",
              "belegdatum": "TTMMJJJJ" | null,
              "belegfeld_1": "<Rechnungsnummer>" | null,
              "personenkonto": <Kontonummer aus der Partnerliste> | null,
              "personenkonto_unbekannt": true | false,
              "personenkonto_name": "<erkannter Name, falls personenkonto_unbekannt>" | null,
              "buchungszeilen": [
                {"sachkonto": <int>, "soll_haben_kennzeichen": "S"|"H", "steuerschluessel": 8|9|null,
                 "umsatz": <Nettobetrag der Zeile>, "buchungstext": "<Positionstext>"}
              ],
              "buchungstext_kopf": null | "<Text>",
              "konfidenz_score": <0.0 bis 1.0>,
              "begruendung": "<ein bis zwei Saetze, warum du so entschieden hast>"
            }

            Ablauflogik (in dieser Reihenfolge pruefen):
            1. Enthaelt der Beleg seinem Inhalt nach eine Rechnung oder Gutschrift (auch wenn der
               Titel handschriftlich, unklar oder nicht woertlich "Rechnung" ist, aber der Inhalt
               eindeutig eine Rechnung/Gutschrift ist)? Nein, erkennbar etwas anderes (Lieferschein,
               Vertrag, Angebot, private Notiz ohne Rechnungscharakter) -> document_type = "Sonstige",
               alle uebrigen Felder null/leer.
            2. Sind Rechnungsnummer, Rechnungsdatum, Steuernummer/USt-IdNr. des Ausstellers, sowie
               Netto-, Steuer- und Bruttobetrag vorhanden? Fehlt etwas -> document_type = "...zur
               Prüfung" (je nach Richtung), buchungszeilen leer, aber personenkonto/belegdatum/
               belegfeld_1 trotzdem befuellen, soweit bekannt (Kontext fuer die menschliche Pruefung).
            3. Gilt Netto + Steuer = Brutto (Toleranz 0,01 EUR)? Nein -> wie Punkt 2 behandeln.
            4. Enthaelt eine Position einer Eingangsrechnung, die Anlagevermoegen ist (siehe
               fixedAssetKeywords im Kontenrahmen), einen Nettoeinzelpreis ueber der GWG-Grenze
               (gwgLimit)? Ja -> wie Punkt 2 behandeln (kein Konto raten, das ist eine
               Anlagevermoegen-Entscheidung).
            5. Ist der Aussteller (Eingangsrechnung) bzw. Empfaenger (Ausgangsrechnung) in der
               Partnerliste enthalten? Ja -> dessen Konto uebernehmen. Nein -> personenkonto_unbekannt
               = true und personenkonto_name = der erkannte Name setzen; das Sammelkonto wird
               programmatisch bestimmt, nicht von dir. Das ist kein Abbruch.
            6. Bestehen alle Pruefungen (1-4) -> document_type bleibt "Rechnungseingang"/
               "Rechnungsausgang", weiter mit Sachkonto-Zuordnung (Punkt 7).
            7. Ausgangsrechnung: keine Positionsaufteilung, nur Steuersatz pruefen (19% -> das
               automatische Erloeskonto, 7% -> das automatische Erloeskonto mit reduziertem Satz,
               siehe revenue im Kontenrahmen). Eingangsrechnung: jede Position einem Sachkonto aus
               dem Kontenrahmen zuordnen (categories nach Positionstext, sonst defaultExpense nach
               Steuersatz), danach Positionen mit demselben Sachkonto UND demselben Steuersatz zu
               einer Buchungszeile zusammenfassen (Nettobetraege addieren).

            steuerschluessel: null bei automatischen Konten (automatic=true im Kontenrahmen), sonst
            8 fuer 7 Prozent und 9 fuer 19 Prozent. soll_haben_kennzeichen: "S" bei Eingangsrechnungen (Aufwand),
            "H" bei Ausgangsrechnungen (Erloes). Eigenes Unternehmen (siehe company-JSON unten):
            ist der Aussteller das eigene Unternehmen, ist es eine Ausgangsrechnung, sonst eine
            Eingangsrechnung.
            """;

    private InvoiceClassificationPrompt() {
    }

    public static ChatRequest build(ExtractedInvoice invoice, CompanyProperties company,
                                    AccountingProperties accounting, PartnerProperties partners,
                                    List<BufferedImage> pages) {
        StringBuilder text = new StringBuilder(ABLAUFLOGIK);
        text.append("\n\neigenes Unternehmen (company):\n").append(JSON.writeValueAsString(company));
        text.append("\n\nKontenrahmen (accounting):\n").append(JSON.writeValueAsString(accounting));
        text.append("\n\nGeschaeftspartner (partners):\n").append(JSON.writeValueAsString(partners));
        text.append("\n\nBeleg (Format ").append(invoice.sourceFormat()).append("):\n");
        text.append("Titel: ").append(invoice.documentTitle()).append('\n');
        text.append("Rechnungsnummer: ").append(invoice.invoiceNumber()).append('\n');
        text.append("Rechnungsdatum: ").append(invoice.invoiceDate()).append('\n');
        text.append("Aussteller: ").append(JSON.writeValueAsString(invoice.seller())).append('\n');
        text.append("Empfaenger: ").append(JSON.writeValueAsString(invoice.buyer())).append('\n');
        text.append("Summen: ").append(JSON.writeValueAsString(invoice.totals())).append('\n');
        text.append("Positionen: ").append(JSON.writeValueAsString(invoice.lines())).append('\n');
        text.append("Volltext:\n").append(invoice.rawText());

        List<ChatContent> parts = new ArrayList<>();
        parts.add(new ChatContent.Text(text.toString()));
        for (BufferedImage page : pages) {
            parts.add(new ChatContent.Image("image/png", encode(page)));
        }
        return ChatRequest.builder().message(ChatMessage.user(parts)).build();
    }

    private static byte[] encode(BufferedImage page) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(page, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
