# Progress

## Umgesetzt
- [x] Regelwerk als Ablauflogik (`IRule` + `RuleEngine`, eine Klasse je Regel)
- [x] Belegimport PDF, ZUGFeRD/Factur-X und XRechnung (UBL + CII)
- [x] Layouterhaltende PDF-Textextraktion mit eigenen Feldextraktoren
- [x] Datenvalidierung: Pflichtangaben (§14 UStG), Rechenprobe, GWG-Grenze
- [x] Stammdaten aus Konfiguration und aus DATEV-DTVF-Export (CSV)
- [x] Personenkontenfindung inkl. Diverse-Konten nach Anfangsbuchstabe
- [x] Sachkontenfindung über den Positionstext, Zusammenfassung der Buchungszeilen
- [x] Konfidenzwert als Steuergröße für die automatische Freigabe
- [x] Human-in-the-Loop: Prüfliste, Freigabe mit Korrektur, Ablehnung
- [x] REST-API für das Frontend (`/api/v1`) inkl. CORS und Problem-Details
- [x] Mailbenachrichtigung für offene Prüfvorgänge (abschaltbar)
- [x] Dockerisierung (Multi-Stage-Image, Compose, Healthcheck)
- [x] GitHub-Actions-Pipeline: Test, Build, Image nach GHCR
- [x] Abnahmetest über alle 31 Testbelege + Startup-Test des Spring-Kontexts

## Offen
- [ ] Übersetzung des internen JSON in die DATEV-XML-Schnittstelle online
- [ ] Belegannahme per Mail (Mailcow-Postfach abholen)
- [ ] OCR-Stufe für gescannte Belege (Port `PdfTextSource` steht bereit)
- [ ] Astro-Frontend gegen `/api/v1`
- [ ] Persistenz der Prüfvorgänge (aktuell im Speicher, Port `ReviewRepository` steht bereit)
