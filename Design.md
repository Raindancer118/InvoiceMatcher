# Design — InvoiceMatcher Prüfoberfläche

> Referenzdokument. Ändert sich eine Design-Entscheidung, wird zuerst diese Datei angepasst.
> Nicht committen (siehe `.gitignore`).

## Wofür das hier gestaltet wird

Ein Arbeitsplatz, keine Broschüre. Die Buchhaltung hat die Seite acht Stunden am Tag offen und
geht Belege durch, die das Regelwerk nicht allein entscheiden konnte. Jede Sekunde, die das
Interface für sich selbst beansprucht, fehlt beim Lesen des Belegs.

Zwei Fragen muss der Einstieg in unter drei Sekunden beantworten: *Wie viel liegt an?* und
*Womit fange ich an?*

## Herkunft der Gestaltung

Basis ist Toms eigenes System von `uniqua.de` — dieselbe Handschrift, auf ein Werkzeug
übertragen. Übernommen wurden Schriftpaar, Neutraltöne und die Blau-Rampe; ergänzt wurde, was
eine Buchhaltungsoberfläche zusätzlich braucht.

Aus der Recherche zu Werkzeug-Oberflächen (Stripe, Mercury, Ramp, Linear) sind drei Regeln
übernommen:

1. **Farbe bedeutet ausschließlich Zustand.** Kein dekorativer Akzent. Sieht der Prüfer Ocker,
   heißt das „offen" und nichts anderes.
2. **Typografie trägt die Hierarchie, nicht Icons und nicht Rahmen.** Tiefe entsteht über
   Flächen, nicht über Linien.
3. **Die größte Zahl auf der Seite beantwortet die wichtigste Frage.** Bei Mercury ist das der
   Kontostand, hier die Zahl der wartenden Vorgänge.

Bewusst nicht übernommen: Sparklines und Trendpfeile. Ein Prüfstapel hat keinen Trend, er hat
eine Länge.

## Farben

Warme Neutraltöne statt kaltem Blaugrau, wie auf `uniqua.de`. Papierton als Grundfläche.

| Rolle | Hell | Dunkel |
|---|---|---|
| Seitengrund | `#f6f4ee` | `#14171b` |
| Karte | `#fdfcf9` | `#1c2027` |
| Vertiefte Fläche | `#efece3` | `#101317` |
| Rahmen | `#e7e4dc` | `#2b313a` |
| Text | `#17202b` | `#eae7e0` |
| Text, zweite Ebene | `#647079` | `#98a1ab` |
| Akzent (Navigation, Fokus) | `#134a7d` | `#4f9bdb` |

**Zustandsfarben.** Die einzige Stelle, an der Farbe erscheint:

| Zustand | Farbe | Fläche |
|---|---|---|
| Offen | `#d98c1a` Ocker | `#fbedd4` |
| Freigegeben | `#2f8f5b` Oliv | `#e2f4ea` |
| Abgelehnt | `#c8372e` Rostrot | `#fbe6e4` |
| Übergeben | `#134a7d` Tinte | `#e5f2fa` |

Als Punkt vor der Zeile, nicht als bunte Kapsel. Ein Stapel mit dreißig Kapseln ist ein
Jahrmarkt. Zusätzlich trägt jede Zeile das Wort — Farbe allein reicht für Rot-Grün-Blindheit
nicht aus.

## Typografie

**Barlow Condensed** für Überschriften und Zahlen mit Gewicht, **Open Sans** für alles andere.
Toms Paar von `uniqua.de`. Die kondensierte Display-Schrift verträgt lange deutsche
Komposita („Rechnungseingang zur Prüfung") ohne umzubrechen.

- Display: `clamp(2rem, 1.4rem + 2vw, 3.2rem)`, `letter-spacing: -0.02em`
- Abschnitt: 1.5rem Barlow Condensed 600
- Fließtext: 0.95rem Open Sans, Zeilenhöhe 1.6
- Tabelle: 0.9rem, Zeilenhöhe 1.4

**Alle Zahlen laufen mit `font-variant-numeric: tabular-nums`.** Beträge sind rechtsbündig,
damit die Kommas untereinander stehen. Ohne das kann man eine Betragsspalte nicht überfliegen,
und Überfliegen ist hier die Hauptbewegung.

Kontonummern und Steuerschlüssel bekommen `letter-spacing: 0.02em` — vierstellige Zahlen ohne
Bedeutung als Wort brauchen etwas Luft, sonst verliest man sich.

## Aufbau der Einstiegsseite

```
┌──────────────────────────────────────────────────────────────┐
│  Wortmarke        Prüfliste  Belege  Export      Niklas K. ▾ │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Willkommen, Niklas Krause.                                  │
│  Dienstag, 26. August                                        │
│                                                              │
│  ACHT VORGÄNGE WARTEN            ┌────────────────────────┐  │
│  darunter zwei seit über         │  Beleg einreichen      │  │
│  einer Woche                     │  hierher ziehen        │  │
│                                  └────────────────────────┘  │
│  ─────────────────────────────────────────────────────────   │
│  8 offen · 2 zur Übergabe bereit · 47 diesen Monat gebucht    │
│                                                              │
│  Was zuerst                                     alle ansehen │
│  ● Offen   04.01.  Friedrich Klausmuster    5007    480,76   │
│  ● Offen   03.01.  Sägewerk Testtanne       4711  1.204,00   │
│  ● Offen   28.12.  Farben & Lack GmbH      44120  1.036,87   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

Die Begrüßung nennt den Namen aus dem SSO-Profil. Darunter steht die eine Zahl, die zählt, als
Display-Zeile ausgeschrieben statt als Kachel mit Ziffer — „Acht Vorgänge warten" liest sich
schneller als eine 8 in einem Kasten.

Rechts daneben liegt die Ablagefläche für neue Belege. Das ist die zweithäufigste Handlung und
gehört deshalb neben die erste, nicht auf eine eigene Seite.

Die Zahlenzeile darunter ersetzt vier KPI-Kacheln durch einen Satz. Drei Zahlen, mit Punkten
getrennt, keine Kästen.

Darunter die drei ältesten offenen Vorgänge als Abkürzung in die Prüfliste.

## Prüfliste

Dichte Tabelle, keine Karten. Karten kosten pro Zeile das Dreifache an Höhe und man sieht acht
statt fünfundzwanzig Vorgängen.

- Kopfzeile auf vertiefter Fläche, keine Rahmenlinien zwischen den Spalten
- Zeilentrenner nur 1px in Rahmenfarbe, kein Zebra
- Zustandspunkt ganz links, Wort daneben
- Beträge rechtsbündig, tabellarisch
- Konfidenz als schmaler Balken statt als Zahl mit zwei Nachkommastellen: 0,70 sagt einem
  Menschen nichts, ein zu zwei Dritteln gefüllter Balken sofort etwas
- Zeilenhöhe 44px, Klickfläche über die ganze Zeile

## Vorgangsdetail

Zweispaltig, links der Beleg, rechts die Vorkontierung. Der Beleg steht fix (`position: sticky`),
die Kontierung scrollt. Wer prüft, vergleicht — beides muss gleichzeitig sichtbar sein.

Die Hinweise des Regelwerks stehen über der Kontierung, nicht darunter. Sie sind der Grund,
warum der Vorgang überhaupt hier liegt.

Freigabe und Ablehnung sitzen am unteren Rand der rechten Spalte, in einer Leiste, die beim
Scrollen stehen bleibt. Freigabe ist die häufige Handlung und bekommt die gefüllte Fläche,
Ablehnung bleibt eine Umrandung.

## Bewegung

Sparsam. Zustandswechsel bekommen 150ms, Seitenwechsel eine View Transition. Kein
Scroll-Scrubbing, kein Parallax. Wer den ganzen Tag hier arbeitet, empfindet Animation als
Wartezeit.

Jede Bewegung hat einen `prefers-reduced-motion`-Ausstieg.

## Technik

- Astro mit SSR (Node-Adapter). SSR ist nötig, weil die Sitzung serverseitig gehalten wird und
  das Zugriffstoken den Browser nie erreichen soll.
- Kein CSS-Framework. Eigene Tokens in `src/styles/tokens.css`, Komponenten daneben.
- Kein JavaScript für etwas, das CSS kann: `@view-transition`, `animation-timeline`,
  `anchor-name` für Menüs, `<dialog>` für Rückfragen.
- Schriften selbst gehostet als WOFF2, `font-display: swap`. Kein Google-Fonts-Aufruf aus dem
  Browser — das wäre bei einer Anwendung mit Beschäftigtendaten eine Übermittlung ohne Grund.
- Grain über `<feTurbulence>` als SVG-Filter auf der Grundfläche, sehr schwach (`opacity: .025`).

## Was hier nicht hingehört

Keine Kacheln mit Icons in Dreiergruppen. Keine Fortschrittsringe. Keine Begrüßung mit
Tageszeit-Logik („Guten Abend"), weil sie beim ersten falschen Treffer albern wirkt. Kein
Glassmorphismus. Keine Kapselfarben außerhalb der vier Zustände.
