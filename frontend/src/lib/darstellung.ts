/** Einheitliche Darstellung von Zahlen, Daten und Zustaenden. */
import type { Vorgang, Zustand } from './api';

const BETRAG = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
const DATUM = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
const LANGES_DATUM = new Intl.DateTimeFormat('de-DE', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
});

export const betrag = (wert: number | null): string => (wert === null ? '—' : BETRAG.format(wert));

export const datum = (iso: string | null): string => (iso ? DATUM.format(new Date(iso)) : '—');

export const heute = (): string => LANGES_DATUM.format(new Date());

/**
 * Zustand fuer die Anzeige. Ein uebergebener Vorgang ist kein eigener Status in der API,
 * sondern ein freigegebener mit Exportvermerk - fuer den Pruefer ist es aber der Unterschied
 * zwischen "muss noch weg" und "ist gebucht".
 */
export type Anzeigezustand = 'inarbeit' | 'offen' | 'freigegeben' | 'abgelehnt' | 'uebergeben';

export function anzeigezustand(vorgang: Vorgang): Anzeigezustand {
  if (vorgang.status === 'IN_ARBEIT') return 'inarbeit';
  if (vorgang.exportiertAm) return 'uebergeben';
  if (vorgang.status === 'ABGELEHNT') return 'abgelehnt';
  if (vorgang.status === 'OFFEN') return 'offen';
  return 'freigegeben';
}

export const ZUSTAND_TEXT: Record<Anzeigezustand, string> = {
  inarbeit: 'In Arbeit',
  offen: 'Offen',
  freigegeben: 'Freigegeben',
  abgelehnt: 'Abgelehnt',
  uebergeben: 'Übergeben',
};

export const ZUSTAND_KLASSE: Record<Anzeigezustand, string> = {
  inarbeit: 'z-arbeit',
  offen: 'z-offen',
  freigegeben: 'z-frei',
  abgelehnt: 'z-abg',
  uebergeben: 'z-ueb',
};

export const FORMAT_TEXT: Record<Vorgang['quelle']['format'], string> = {
  PDF: 'PDF',
  SCAN: 'SCAN',
  ZUGFERD: 'ZUGFeRD',
  XRECHNUNG: 'XRechnung',
};

/** Der Balken der Prueflliste; die Farbe folgt der Freigabeschwelle. */
export function konfidenzKlasse(wert: number): string {
  if (wert >= 0.85) return 'gut';
  return wert >= 0.4 ? 'mittel' : '';
}

const ZAHLWORT = ['Kein', 'Ein', 'Zwei', 'Drei', 'Vier', 'Fünf', 'Sechs', 'Sieben', 'Acht',
  'Neun', 'Zehn', 'Elf', 'Zwölf'];

/** „Acht Vorgänge warten" liest sich schneller als eine 8 in einem Kasten. */
export function zahlwort(anzahl: number): string {
  return ZAHLWORT[anzahl] ?? String(anzahl);
}

export const status: Record<Zustand, string> = {
  IN_ARBEIT: 'In Arbeit',
  OFFEN: 'Offen',
  FREIGEGEBEN: 'Freigegeben',
  ABGELEHNT: 'Abgelehnt',
  AUTOMATISCH_FREIGEGEBEN: 'Automatisch freigegeben',
};
