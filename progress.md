# Progress

## Umgesetzt
- [x] **Einreich-Ablage faehrt von oben herein** (0.10.0): der Knopf "+ Beleg einreichen" im Kopf
      oeffnet jetzt auf jeder Seite eine Lade, die von oben ins Bild faehrt und Belege per Klick
      auf die Flaeche oder per Ziehen annimmt - vorher sprang er auf die Startseite. Natives
      `<dialog>` (`Einreichlade.astro`), damit Fokusfalle, Escape und abgedunkelter Hintergrund
      vom Browser kommen; ohne Javascript bleibt der Knopf ein Verweis auf die Ablage der
      Startseite. `Ablage.astro` verdrahtet seine Flaechen jetzt einzeln statt ueber feste IDs,
      weil dieselbe Ablage zweimal auf einer Seite haengen kann.
- [x] **Schluesselansicht mit Kontingentstand** (0.9.0): eigene Seite `/schluessel` listet jeden
      hinterlegten KI-Schluessel beider Stufen - maskiert (`gsk_…a1b2`, der Klartext verlaesst
      den Server nie), mit Nutzungen, Fehlern, Kontingenttreffern und laufender Sperre aus dem
      Pool (kostenlos). Knopf "Alle Schluessel live pruefen" fragt jeden Schluessel einzeln beim
      Anbieter an und zeigt Restanfragen, Resttoken und den Zeitpunkt, ab dem das Kontingent
      wieder da ist. Neu dahinter: `AiKeyPool` (langlebiger Pool je Stufe, Zustand verschluesselt
      neben der Datenbank - vorher war jede Kontingentsperre nach einem Beleg vergessen),
      `AiKeyProbe` (rohes HTTP, weil nur die Antwort-Header die Kontingentzahlen tragen) und
      `AiKeyStatusService`; API: `GET /api/v1/einstellungen/ki/schluessel` und
      `POST .../schluessel/pruefen`.
      **Live verifiziert** (`LiveAiKeyProbeTest`): Groq meldete 999 Restanfragen, 7927 Resttoken
      und einen konkreten Reset-Zeitpunkt; Gemini ist erreichbar, gibt aber keine Restangaben
      zurueck - das steht so in der Oberflaeche.
- [x] **CI-Zugang zur privaten Maven-Registry repariert** (16.09.2026): der Build scheiterte seit
      dem 11.09. mit 401 beim Aufloesen von `ai-gateway` - die Actions hatten ueberhaupt keine
      Zugangsdaten fuer `packages.tstieh.de`, der Deploy lief deshalb nie an. Eigener,
      ausschliesslich lesender Registry-Token `invoicematcher-ci` (Route `/releases`, `r`) als
      GitHub-Secrets hinterlegt; Workflow reicht ihn ueber `setup-java` an Maven und als
      BuildKit-Secret in den Image-Build.
- [x] **Gedanken der KI je Beleg** (0.8.0): auf die Klassifikation folgt eine zweite, rein
      erklaerende Anfrage (`RationalePrompt`, `ClassificationRationaleService`). Sie legt dem
      Modell den Beleg und die bereits getroffene Kontierung vor und fragt nach dem Gedankengang -
      ausdruecklich in der Ich-Form und im Praesens ("Oben steht 'Rechnung' - das ist ein
      Rechnungseingang", "Den Klausmuster ordne ich dem Personenkonto 70101 zu"), gegliedert in
      Stationen (Belegart, Pflichtangaben, Rechenprobe, Personenkonto, Sachkonten, Konfidenz),
      dazu selbst benannte Unsicherheiten und was das Modell sonst noch erwogen hat.
      Das Ergebnis haengt als `gedanken` am `ClassificationReport`, wird mit dem Vorgang in
      SQLite abgelegt, kommt ueber `GET /api/v1/pruefungen/{id}` mit und liegt im Prueftisch in
      einer ausfahrbaren Lade am rechten Rand (`<details class="gedankenlade">`, reines CSS, kein
      Skript - der Buchungssatz bleibt sichtbar, die Gedanken kommen nur auf Anforderung davor). Bewusst getrennt vom Klassifikationsaufruf: eine
      Begruendung im selben Aufruf formt die Entscheidung mit. Faellt die Zweitanfrage aus, ist
      `gedanken` schlicht `null` - die Kontierung bleibt unberuehrt.
      **Live gegen die echten Vault-Keys verifiziert** (`LiveAiClassificationTest`): das Modell
      lieferte zum Testbeleg sieben Gedankenschritte in der Ich-Form mit Betraegen und
      Kontonummern und blieb von sich aus am fehlenden Steuerschluessel der Zuschnitt-Position
      haengen ("Ich bin mir nicht sicher, ob ... ein Steuerschluessel noetig ist") - genau der
      Punkt, an dem ein Pruefer ansetzt.
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
