# InvoiceMatcher

Klassifiziert Eingangs- und Ausgangsbelege und erzeugt daraus eine DATEV-kompatible
Vorkontierung. Belege, die das Regelwerk bemängelt oder die zu unsicher sind, gehen in eine
menschliche Prüfung statt direkt in die Buchhaltung.

## Was der Dienst kann

- **Vier Belegformate** — PDF (Textheuristik), gescanntes PDF (Zeichenerkennung),
  ZUGFeRD/Factur-X (eingebettetes CII-XML) und XRechnung (UBL). Strukturierte Daten haben
  immer Vorrang vor der Textauswertung.
- **Gescannte Belege** — hat ein PDF keine brauchbare Textebene, liest Tesseract die
  Seitenbilder. Die Spaltenstruktur des Belegs bleibt dabei erhalten, damit Anschriftenfeld,
  Kopfdaten und Positionstabelle weiter getrennt werden können. Ein so gelesener Beleg wird als
  `SCAN` gekennzeichnet und geht wegen der geringeren Konfidenz zur Prüfung.
- **Belegtyp nach Regelwerk** — `Rechnungseingang`, `Rechnungsausgang`, die beiden
  Prüfvarianten und `Sonstige`.
- **Vorkontierung** — Personenkonto aus den Stammdaten (Sammelkonto nach Anfangsbuchstabe für
  unbekannte Partner), Sachkonten aus dem Positionstext, Zusammenfassung gleichartiger
  Positionen, Steuerschlüssel je nach Automatikkonto.
- **Pflichtprüfungen** — §14-UStG-Angaben, Rechenprobe Netto + Steuer = Brutto, GWG-Grenze.
- **Human-in-the-Loop** — Prüfliste mit Freigabe, Korrektur und Ablehnung; optional
  Mailbenachrichtigung.
- **Persistenz** — Prüfvorgänge und Belegbilder liegen in einer SQLite-Datei und überstehen
  einen Neustart. Kein Datenbankserver nötig, die Ablage ist eine Datei zum Sichern.
- **DATEV-Übergabe** — erzeugt ein DATEV Document-Package (XML-Schnittstelle online, Version
  6.0): Belegbild, Belegsatzdaten und `document.xml` in einem ZIP. Ob ein Buchungssatz entsteht
  oder der Beleg nur abgelegt wird, steuert genau wie vorgesehen das Beiliegen der
  Belegsatzdatendatei. Übergebene Vorgänge werden vermerkt und nicht ein zweites Mal gebucht.

## Schnellstart

Für gescannte Belege wird Tesseract samt deutschen Sprachdaten gebraucht
(`apt install tesseract-ocr tesseract-ocr-deu`; das Container-Image bringt beides mit). Fehlt
es, laufen alle anderen Formate unverändert weiter — gescannte Belege gehen dann mit leeren
Feldern zur Prüfung, und der Start meldet das als Warnung.

```bash
mvn verify                # Testsuite (inkl. aller 31 Testbelege)
mvn spring-boot:run       # Dienst auf http://localhost:8080
docker compose up --build # als Container
```

## API

| Methode | Pfad | Zweck |
|---|---|---|
| `POST` | `/api/v1/belege/klassifikation` | Beleg klassifizieren, ohne ihn abzulegen (Vorschau) |
| `POST` | `/api/v1/belege` | Beleg annehmen und in den Prüfablauf geben |
| `POST` | `/api/v1/belege/stapel` | mehrere Belege auf einmal |
| `GET`  | `/api/v1/pruefungen` | offene Prüfvorgänge (`?alle=true` für alle) |
| `GET`  | `/api/v1/pruefungen/{id}` | einzelner Vorgang |
| `GET`  | `/api/v1/pruefungen/{id}/beleg` | Originalbeleg zur Anzeige (`inline`) |
| `POST` | `/api/v1/pruefungen/{id}/freigabe` | freigeben, optional mit Korrektur |
| `POST` | `/api/v1/pruefungen/{id}/ablehnung` | ablehnen, mit Begründung |
| `GET`  | `/api/v1/export/offen` | freigegebene, noch nicht übergebene Belege |
| `POST` | `/api/v1/export/datev` | DATEV Document-Package als ZIP (ohne Body: alle offenen) |
| `GET`  | `/api/v1/stammdaten/partner` | Geschäftspartner |
| `GET`  | `/api/v1/stammdaten/kategorien` | Warengruppen und Sachkonten |
| `GET`  | `/api/v1/stammdaten/regeln` | Regelwerk in Anwendungsreihenfolge |
| `GET`  | `/actuator/health` | Zustand |

Beispiel:

```bash
curl -F "datei=@rechnung.pdf" http://localhost:8080/api/v1/belege/klassifikation
```

```json
{
  "dateiname": "rechnung.pdf",
  "quelle": { "format": "PDF", "aussteller": "Friedrich Klausmuster", "netto": 404.00 },
  "ergebnis": {
    "document_type": "Rechnungseingang",
    "belegdatum": "04012026",
    "belegfeld_1": "5007",
    "personenkonto": 70101,
    "buchungszeilen": [
      { "sachkonto": 3401, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
        "umsatz": 399.00, "buchungstext": "Echtholzfurnierkanten Ahorn 24 mm, …" }
    ],
    "buchungstext_kopf": null,
    "konfidenz_score": 0.85
  },
  "hinweise": []
}
```

`ergebnis` ist das interne DATEV-Zwischenformat und bleibt unverändert; `quelle` und `hinweise`
sind Kontext für die Prüfoberfläche.

## Anmeldung

Die API hängt hinter Authentik (`portal.tstieh.de`). Jeder Aufruf braucht ein Zugriffstoken,
und das Konto muss in der Gruppe `InvoiceMatcher` sein.

| Variable | Vorgabe | Zweck |
|---|---|---|
| `INVOICEMATCHER_OIDC_ISSUER` | — | Herausgeber, z. B. `https://portal.tstieh.de/application/o/invoicematcher/` |
| `INVOICEMATCHER_OIDC_GROUP` | `InvoiceMatcher` | verlangte Gruppe; leer lässt jedes gültige Token zu |

Ohne gesetzten Herausgeber läuft die Anwendung ohne Anmeldung und sagt das beim Start als
Warnung. Für die lokale Entwicklung ist das gewollt, für den Betrieb nicht.

## Ablage

Prüfvorgänge und die Original-Belegbilder liegen in einer SQLite-Datei, standardmäßig unter
`./data/invoicematcher.db`. Der Pfad lässt sich über `INVOICEMATCHER_DB` setzen; im Container
liegt die Datei im Volume `/data`.

```bash
INVOICEMATCHER_DB=/var/lib/invoicematcher/db.sqlite mvn spring-boot:run
```

Das Verzeichnis legt die Anwendung bei Bedarf selbst an. Für den Wechsel auf eine
Server-Datenbank sind `ReviewRepository` und `DocumentStore` die einzigen zu ersetzenden
Bausteine.

## Konfiguration

Der gesamte Kontierungsrahmen steht in `src/main/resources/application.yml` unter
`invoicematcher` — eigenes Unternehmen, Warengruppen mit Stichwörtern, GWG-Grenze,
Partnerstammdaten, Review-Schwelle. Eine neue Warengruppe braucht kein Deployment, nur einen
Eintrag.

Die Zeichenerkennung steht unter `invoicematcher.ocr` und lässt sich vollständig über
Umgebungsvariablen steuern:

| Variable | Vorgabe | Zweck |
|---|---|---|
| `INVOICEMATCHER_OCR_ENABLED` | `true` | abschalten, wenn kein Tesseract vorhanden ist |
| `INVOICEMATCHER_OCR_COMMAND` | `tesseract` | Pfad, falls es nicht im `PATH` liegt |
| `INVOICEMATCHER_OCR_LANGUAGE` | `deu+eng` | Sprachdaten |
| `INVOICEMATCHER_OCR_TESSDATA_DIR` | — | abweichendes Verzeichnis der Sprachdaten |

## DATEV-Übergabe

```bash
curl -X POST http://localhost:8080/api/v1/export/datev -o paket.zip
unzip -l paket.zip
#   document.xml
#   01_PDF_Klaus_Furniere.pdf
#   Rechnungsdaten_5007.xml
```

Die erzeugten Dateien werden in den Tests gegen die offiziellen DATEV-Schemata validiert
(`src/test/resources/datev-xsd`). Beträge werden dabei brutto ausgewiesen, weil DATEV in
`amount` den Rechnungsbetrag erwartet; die interne Vorkontierung rechnet netto.

## Lizenz und Daten

Die Belege unter `src/test/resources/belege` stammen aus dem DATEV-Musterpaket und sind
Testdaten ohne Personenbezug.
