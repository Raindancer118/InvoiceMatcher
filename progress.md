# Progress

## Umgesetzt
- [x] **KI-Klassifikation ersetzt das deterministische Regelwerk vollstaendig** (0.7.0): das alte
      `IRule`/`RuleEngine`-Regelwerk und das Keyword-/Fuzzy-Matching in `masterdata/` sind
      entfernt. `ClassificationService` schickt Beleg, Kontenrahmen und Geschaeftspartnerliste
      als Prompt an eine KI (`classification/ai`: `InvoiceClassificationPrompt`,
      `AiClassificationResponseParser`, `TieredInvoiceClassifier`) und uebernimmt deren
      Entscheidung direkt - document_type, Personenkonto, Sachkonto/Buchungszeilen. Modell-
      Tiering ueber `ai-gateway`: Groq zuerst (schnell, kein Bildeingang), bei gescannten
      Belegen oder Fehlschlag Gemini (vision-faehig, liest auch handschriftliche Titel direkt).
      Schlaegt die KI komplett fehl, geht der Beleg zur manuellen Pruefung statt verworfen zu
      werden. Personenkonto-Fallback fuer unbekannte Partner bleibt deterministisch
      (`DiverseAccountRange`, 26 Sammelkonten nach Anfangsbuchstabe).
      **Live gegen die echten Vault-Keys verifiziert** (`LiveAiClassificationTest`, opt-in via
      `INVOICEMATCHER_LIVE_AI_TEST=true`): document_type, Belegdatum, Rechnungsnummer,
      Personenkonto und beide Buchungszeilen (Sachkonto/Steuerschluessel/Betrag) eines echten
      Testbelegs stimmen exakt. Vorgabemodelle `openai/gpt-oss-120b` (Groq) und
      `gemini-flash-latest` (Gemini) - die urspruenglich gewaehlten `llama-3.3-70b-versatile`
      und `gemini-2.5-pro` waren fuer den Schluessel-Pool nicht mehr erreichbar (404).
- [x] Kontenrahmen, Geschaeftspartnerliste und KI-Modellwahl sind jederzeit ueber die
      Einstellungen-Oberflaeche editierbar (Roh-JSON bzw. Modell+Schluessel), ohne Neustart -
      `AccountingSettingsService`/`PartnerSettingsService`/`AiSettingsService`, gleiches
      Seed-dann-DB-Prinzip wie Firma/Mail-Eingang. "Verbindung testen"-Knopf prueft Modell/
      Schluessel live (`AiConnectionTester`) vor dem Speichern.
- [x] Belegimport PDF, ZUGFeRD/Factur-X und XRechnung (UBL + CII)
- [x] Layouterhaltende PDF-Textextraktion mit eigenen Feldextraktoren
- [x] Human-in-the-Loop: Prüfliste, Freigabe mit Korrektur, Ablehnung
- [x] REST-API für das Frontend (`/api/v1`) inkl. CORS und Problem-Details
- [x] Mailbenachrichtigung für offene Prüfvorgänge (abschaltbar)
- [x] Dockerisierung (Multi-Stage-Image, Compose, Healthcheck)
- [x] GitHub-Actions-Pipeline: Test, Build, Image nach GHCR
- [x] Abnahmetest über alle 31 Testbelege + Startup-Test des Spring-Kontexts
- [x] Übersetzung in die DATEV-XML-Schnittstelle online (Document-Package: `document.xml`,
      Belegsatzdaten, Belegbild), validiert gegen die offiziellen DATEV-Schemata
- [x] Exportvermerk je Vorgang gegen doppelte Buchung
- [x] Persistenz in SQLite: Pruefvorgaenge und Belegbilder ueberstehen einen Neustart
- [x] OCR-Stufe fuer gescannte Belege (Tesseract), layouterhaltend ueber die Wortrahmen
- [x] Gemini-Ausweichweg fuer die Zeichenerkennung: wird nur angefragt, wenn Tesseract keinen
      bekannten Belegtitel findet (typischerweise Handschrift) - immer aktiv, nutzt denselben
      Schluessel-Pool wie die KI-Klassifikation (`invoicematcher.ai.smart`); ohne hinterlegten
      Schluessel meldet sich der Ausweichweg einfach als nicht verfuegbar

- [x] Belegbild zum Pruefvorgang ueber die API abrufbar (`GET /api/v1/pruefungen/{id}/beleg`)
- [x] API gegen Authentik abgesichert (JWT, Gruppenzwang, Healthcheck bleibt frei)
- [x] Belegannahme per Mail: Postfach ueber IMAP abholen, PDF-/XML-Anhaenge in den Pruefablauf,
      Nachrichten umlegen statt loeschen, Absenderliste als Zugangsschutz
- [x] Authentik eingerichtet: Provider/Application `invoicematcher` und Gruppe `InvoiceMatcher`
      auf portal.tstieh.de, eigenes Scope-Mapping fuer die Gruppen
- [x] Postfach `belege@volantic.de` auf der eigenen Mailcow angelegt und angebunden
- [x] Live auf dorn unter https://belege.tstieh.de (NPMplus-Host 83 auf saphira, Auto-Deploy
      per GitHub Actions nach gruenem Build)

## Offen
- [x] Astro-Frontend gegen `/api/v1`: Uebersicht, Pruefliste, Vorgangsdetail mit Belegbild,
      Freigabe/Ablehnung, DATEV-Export - angemeldet ueber Authentik (portal.tstieh.de)
- [x] Einstellungen-Oberflaeche um Kontenrahmen, Geschaeftspartner und KI-Modellwahl erweitert
      (Roh-JSON-Editor bzw. Modell/Schluessel-Formular, "Verbindung testen"-Knopf)
- [ ] Wechsel auf eine Server-Datenbank samt Migrationswerkzeug, sobald mehrere Mandanten
      oder parallele Schreiber dazukommen (Ports `ReviewRepository` und `DocumentStore`
      bleiben unverändert)
- [ ] DTVF-CSV-Import fuer Geschaeftspartner bewusst nicht auf die neue Settings-Ablage
      uebertragen (war an `MasterDataConfiguration` gebunden, die mit dem Regelwerk entfallen
      ist) - Partner werden aktuell nur ueber das JSON-Feld gepflegt
- [ ] Dedizierte Frontend-Tests fuer die drei neuen Einstellungen-Formulare (Kontenrahmen,
      Geschaeftspartner, KI) fehlen noch - nur ueber den bestehenden Seiten-Rendertest
      (`GET /einstellungen`) indirekt abgedeckt; Backend-Seite ist vollstaendig getestet
      (Repository, Service, REST-API)
- [ ] Direkte Übertragung an DATEV Unternehmen online (aktuell wird das Paket zum Download
      erzeugt; für den Upload sind Zugangsdaten und der DATEV-Client nötig)
