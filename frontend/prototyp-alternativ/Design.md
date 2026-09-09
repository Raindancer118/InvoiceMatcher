# Alternative Frontend-Preview: Prüftisch

Diese Richtung wurde bewusst ohne Sichtung des bestehenden Frontends und des bestehenden
`Design.md` entwickelt. Grundlage sind allein Aufgabe, Domäne und dokumentierter Arbeitsablauf
des InvoiceMatcher.

Für die zweite Ausbaustufe wurden die vorhandenen Astro-Seiten anschließend als reines
Funktionsinventar gelesen. Ihre Gestaltung wurde nicht übernommen.

## Geprüfte Richtungen

1. **Prüftisch** (gewählt): Eine dreigeteilte Arbeitsfläche aus Warteschlange, Beleg und
   Kontierung. Die Anmutung kommt aus physischen Postmappen, Prüfstempeln und markierten
   Buchhaltungsbelegen.
2. **Kontenband**: Eine horizontale, zeitachsenartige Oberfläche, auf der Belege von Eingang
   bis DATEV wandern. Stark für Durchsatz, schwächer für die konzentrierte Einzelprüfung.
3. **Revisionsraum**: Dunkle, sehr dichte Datenoberfläche mit Beleg im Leuchttisch. Prägnant,
   für lange Arbeitstage aber anstrengender und unnötig technisch.

## Designplan

### Farbe

- **Arbeitstisch** `#d8d7cd`: ruhiger, sachlicher Grund ohne klinisches Weiß.
- **Belegpapier** `#fbfaf5`: hebt das Dokument eindeutig von der Arbeitsfläche ab.
- **Druckfarbe** `#171713`: Haupttext und harte Konturen.
- **Archivgrün** `#173e35`: Warteschlange und Navigationsanker.
- **Prüfstempel** `#f05a38`: primäre Aktion und offene Abweichung.
- **Freigabemarke** `#b6df63`: bestätigte oder automatisch erkannte Werte.

### Typografie

- Verdichtete Grotesk (`Arial Narrow`, `Liberation Sans Narrow`) für Navigation,
  Vorgangsnamen und Zahlen. Sie erinnert an Ablageetiketten, bleibt dabei sehr lesbar.
- Humanistische Sans (`Aptos`, `Segoe UI`) für längere Erklärungen und Formwerte.
- Monospace nur dort, wo Zeichen exakt verglichen werden: Rechnungsnummer, Konten und Beträge.

### Layout

```text
┌ Kopf: Produkt / Bereich / neue Einreichung ────────────────┐
│ Warteschlange │              Beleg              │ Kontierung │
│ dunkel, dicht   │         ruhige Bühne          │ hell, aktiv │
│ offene Fälle   │      Original im Zentrum      │ Felder       │
│                 │                                 │ Entscheidung │
└──────────────────┴──────────────────────────────────┴─────────────┘
```

Links und rechts sind handlungsorientiert, die Mitte bleibt als Dokumentbühne ruhig. Auf
kleinen Displays wird daraus eine lineare Reihenfolge: Auswahl, Beleg, Entscheidung.

### Prinzipien

- Der erste Bildschirm ist bereits der Arbeitsplatz, keine Produktwerbung.
- Genau ein markanter Moment: Die orange Stempelform zieht den Blick zur Entscheidung.
- Farbe trägt Status; Form und Text wiederholen dieselbe Bedeutung für Barrierefreiheit.
- Unterschiedliche Flächenformen folgen ihrer Funktion: Mappe, Papier, Prüfblatt, Stempel.
- Bewegungen reagieren auf Handlungen; keine dauerhafte Animation.

## Abgebildeter Funktionsumfang

- Übersicht mit persönlicher Begrüßung, Anzahl offener, freigegebener, übergebener und
  abgelehnter Vorgänge, Altershinweis und direktem Einstieg in die wichtigsten Belege.
- Einreichen eines einzelnen PDF-, ZUGFeRD- oder XRechnungs-Belegs mit Dateiauswahl und
  anschließender Weiterleitung in den Prüfarbeitsplatz.
- Prüfliste mit allen vier Anzeigezuständen, Format, Konfidenz und Betrag sowie Filterung
  nach Zustand.
- Vorgangsdetail mit PDF- und XML-Darstellung, Originalbeleg, Regelhinweis, vollständigen
  Kopfdaten, Buchungszeilen, Freigabe, begründeter Ablehnung und Entscheidungsstatus.
- DATEV-Übergabe mit Anzahl, Bruttosumme, enthaltenen Belegen und einmaliger Paketerzeugung.
- Firmeneinstellungen und die vollständige Konfiguration der Belegannahme per Mail.
- Benutzermenü, Abmeldung, erneute Authentik-Anmeldung sowie Vorschau der Fehlerzustände
  `Anmeldung fehlgeschlagen` und `Kein Zugriff`.

## Selbstkritik vor der Umsetzung

Der erste Gedanke einer cremefarbenen Editorial-Oberfläche mit Serifenschrift wäre ein
beliebiges Design-Default gewesen. Deshalb nutzt der Entwurf eine verdichtete Grotesk, eine
archivartige Farbwelt und eine echte Arbeitsansicht. Identische Karten, dekorative Verläufe,
Marketing-Hero und austauschbare Kennzahlen wurden verworfen.
