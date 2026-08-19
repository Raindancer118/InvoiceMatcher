# InvoiceMatcher

Klassifiziert Eingangs- und Ausgangsbelege und erzeugt daraus eine DATEV-kompatible
Vorkontierung. Belege, die das Regelwerk bemängelt oder die zu unsicher sind, gehen in eine
menschliche Prüfung statt direkt in die Buchhaltung.

## Was der Dienst kann

- **Drei Belegformate** — PDF (Textheuristik), ZUGFeRD/Factur-X (eingebettetes CII-XML) und
  XRechnung (UBL). Strukturierte Daten haben immer Vorrang vor der Textauswertung.
- **Belegtyp nach Regelwerk** — `Rechnungseingang`, `Rechnungsausgang`, die beiden
  Prüfvarianten und `Sonstige`.
- **Vorkontierung** — Personenkonto aus den Stammdaten (Sammelkonto nach Anfangsbuchstabe für
  unbekannte Partner), Sachkonten aus dem Positionstext, Zusammenfassung gleichartiger
  Positionen, Steuerschlüssel je nach Automatikkonto.
- **Pflichtprüfungen** — §14-UStG-Angaben, Rechenprobe Netto + Steuer = Brutto, GWG-Grenze.
- **Human-in-the-Loop** — Prüfliste mit Freigabe, Korrektur und Ablehnung; optional
  Mailbenachrichtigung.

## Schnellstart

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
| `POST` | `/api/v1/pruefungen/{id}/freigabe` | freigeben, optional mit Korrektur |
| `POST` | `/api/v1/pruefungen/{id}/ablehnung` | ablehnen, mit Begründung |
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

## Konfiguration

Der gesamte Kontierungsrahmen steht in `src/main/resources/application.yml` unter
`invoicematcher` — eigenes Unternehmen, Warengruppen mit Stichwörtern, GWG-Grenze,
Partnerstammdaten, Review-Schwelle. Eine neue Warengruppe braucht kein Deployment, nur einen
Eintrag.

## Lizenz und Daten

Die Belege unter `src/test/resources/belege` stammen aus dem DATEV-Musterpaket und sind
Testdaten ohne Personenbezug.
