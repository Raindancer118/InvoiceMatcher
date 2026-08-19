# Testbeleg-Auswahl (30 Belege) – Übersicht

20 echte, unveränderte Belege aus dem DATEV-Musterpaket + 10 konstruierte
Grenzfälle (das ZIP enthält nur korrekte Belege, Regelverstöße müssen daher
selbst angelegt werden).

## Format-Verteilung (Eingangsrechnungen)
- **6 × PDF**, **6 × ZUGFeRD**, **3 × XRechnung** – gleiche Anzahl PDF/ZUGFeRD,
  XRechnung bewusst seltener, jeweils über dieselben vier Wareneingangs-
  Kategorien verteilt.

| # | Datei | Format | Lieferant | Kategorie |
|---|---|---|---|---|
| 01 | 01_PDF_Klaus_Furniere.pdf | PDF | Friedrich Klausmuster | Furniere/Kanten (3401) |
| 02 | 02_PDF_Saege_Platte.pdf | PDF | Sägewerk Kumpfmühle Test | Plattenwerkstoffe (3403) – Positionstext trotz „Sägewerk" im Namen |
| 03 | 03_PDF_Redloff_Plattenwerkstoff.pdf | PDF | Jochen Redloffbeispiel | Plattenwerkstoffe (3403) |
| 04 | 04_PDF_Farben_Lack.pdf | PDF | Farben-Fritzebeispiel | Lacke/Beschichtung (3404) |
| 05 | 05_PDF_Buerobedarf_Kopierer.pdf | PDF | Bürobedarf Test | Bürobedarf (4930) – Grenzfall „Digitalkopiergerät" |
| 06 | 06_PDF_Tankstelle_Diesel.pdf | PDF | Mustertankstelle Schmidt | Kfz-Betriebsstoffe (4530) |
| 07 | 07_ZUGFeRD_Buerobedarf.pdf | ZUGFeRD | Bürobedarf Test | Bürobedarf (4930) |
| 08 | 08_ZUGFeRD_Saegewerk_Testtanne.pdf | ZUGFeRD | Sägewerk Testtanne | Plattenwerkstoffe (3403) – Position „Beschichtete Platte Eiche" |
| 09 | 09_ZUGFeRD_Unkastester_Furniere.pdf | ZUGFeRD | Unkastester & Hübnerbeispiel | Furniere/Kanten (3401) |
| 10 | 10_ZUGFeRD_Farben_Lack.pdf | ZUGFeRD | Farben-Fritzebeispiel | Lacke/Beschichtung (3404) |
| 11 | 11_ZUGFeRD_Redloff_Platte.pdf | ZUGFeRD | Jochen Redloffbeispiel | Plattenwerkstoffe (3403) |
| 12 | 12_ZUGFeRD_Tankstelle.pdf | ZUGFeRD | Mustertankstelle Schmidt | Kfz-Betriebsstoffe (4530) |
| 13 | 13_XRechnung_Buerobedarf.xml | XRechnung (UBL) | Bürobedarf Test | Bürobedarf (4930) |
| 14 | 14_XRechnung_Saegewerk_Testtanne.xml | XRechnung (UBL) | Sägewerk Testtanne | Plattenwerkstoffe (3403) – Position „Beschichtete Platte Eiche" |
| 15 | 15_XRechnung_Farben_Lack.xml | XRechnung (UBL) | Farben-Fritzebeispiel | Lacke/Beschichtung (3404) |

*Hinweis: Bei #02, #08, #14 (Sägewerke) zeigt sich real, dass die Position
über Plattenware geht ("beschichtete Platte"), obwohl der Lieferantenname
"Sägewerk" nahelegt, es handle sich um Massivholz – ein gutes Beispiel dafür,
dass die Kategorisierung über den Positionstext erfolgen muss, nicht über
den Lieferantennamen.*

## Ausgangsrechnungen und Sonstige (unverändert)

| # | Datei | Testet |
|---|---|---|
| 16 | 16_AR_Laub.pdf | AR, summarische Buchung nach Steuersatz |
| 17 | 17_AR_Beispiel.pdf | AR, zweiter Normalfall |
| 18 | 18_Sonstige_Mietvertrag.pdf | Kein Rechnungstitel, keine Beträge → „Sonstige" |
| 19 | 19_Sonstige_Grundabgabenbescheid.pdf | Enthält „Rechnungsdatum"/„Rechnungsnummer", Titel aber „Grundabgabenbescheid" → „Sonstige" |
| 20 | 20_PDF_Ton_unbekannterPartner.pdf | Echter Beleg, bewusst NICHT in die Partnerliste aufnehmen → testet Diverse-Fallback (Regel 2.3) |

## Konstruierte Grenzfälle (21–30)

Diese 10 Dateien liegen jetzt als eigene PDFs im Ordner vor. Jeweils Basis =
eine echte Rechnung aus 01–06 (Inhalt/Beträge original übernommen), gezielt
verändert. Alle anderen Angaben bleiben unverändert korrekt, damit die Fälle
jeweils genau eine Regel testen.

| # | Konstruiert aus | Änderung | Testet |
|---|---|---|---|
| 21 | 04 (Farben) | Titel „Rechnung" → „Auftragsbestätigung Nr. 1104" | Regel 1.1 – kein Rechnungstitel (Variante 1) |
| 22 | 02 (Säge) | Titel → „Lieferschein Nr. 16849", Netto-/Steuer-/Bruttozeile entfernt | Regel 1.1 – kein Rechnungstitel (Variante 2, ohne Steuerausweis) |
| 23 | 03 (Redloff) | Titel → „Angebot Nr. 411" | Regel 1.1 – kein Rechnungstitel (Variante 3) |
| 24 | 01 (Klaus) | Zeile „Rechnungs-Nr.: 5007" entfernt | Regel 1.2 – fehlende Rechnungsnummer |
| 25 | 06 (Tankstelle) | Zeile „Rechnungsdatum: 13.01.2026" entfernt | Regel 1.2 – fehlendes Rechnungsdatum |
| 26 | 03 (Redloff) | Zeile „USt-IdNr.: DE 433176775" entfernt | Regel 1.2 – fehlende USt-IdNr./Steuernummer |
| 27 | 05 (Bürobedarf) | Nettobetrag-Spalte entfernt, nur Bruttosumme steht noch da | Regel 1.2 – fehlender Nettobetrag |
| 28 | 04 (Farben) | Steuersatz-/Steuerbetrag-Angabe entfernt (nur Netto- und Bruttosumme sichtbar) | Regel 1.2 – fehlender Steuerbetrag/-satz |
| 29 | 01 (Klaus) | Gesamtsumme von 480,76 € auf 500,00 € geändert (Rest unverändert) | Regel 1.3 – Rechenfehler (Netto + Steuer ≠ Brutto) |
| 30 | 04 (Farben) | Titel „Rechnung" → „Gutschrift" (Beträge/Angaben sonst vollständig korrekt) | Positiver Testfall Regel 1.1 – „Gutschrift" muss genauso akzeptiert werden wie „Rechnung" |

## Zusammenfassung
- 15 normale Eingangsrechnungen über 3 Formate (6 PDF / 6 ZUGFeRD / 3 XRechnung), gleichmäßig über die vier Wareneingangs-Kategorien plus Büro/Kfz verteilt
- 2 Ausgangsrechnungen
- 2 echte „Sonstige"-Belege
- 1 echter „unbekannter Partner"-Fall
- 10 konstruierte Regelverstöße (3× fehlender Titel, 5× unterschiedliche fehlende Pflichtangaben, 1× Rechenfehler, 1× positiver Gutschrift-Testfall)

= 30 Testfälle, die jede Regel des Baseline-Regelwerks mindestens einmal
abdecken.
