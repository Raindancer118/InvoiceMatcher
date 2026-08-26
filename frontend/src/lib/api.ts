/**
 * Zugriff auf die Belegschnittstelle.
 *
 * <p>Laeuft ausschliesslich serverseitig: das Zugriffstoken darf den Browser nicht erreichen.
 * Das Frontend spricht die API also nie direkt aus dem Browser an, sondern immer ueber diese
 * Schicht.
 */
import { config } from './config';

export type Zustand = 'OFFEN' | 'FREIGEGEBEN' | 'ABGELEHNT' | 'AUTOMATISCH_FREIGEGEBEN';

export interface Buchungszeile {
  sachkonto: number;
  soll_haben_kennzeichen: string;
  steuerschluessel: number | null;
  umsatz: number;
  buchungstext: string | null;
}

export interface Hinweis {
  ruleId: string;
  severity: 'INFO' | 'WARNUNG' | 'FEHLER';
  message: string;
}

export interface Quelle {
  format: 'PDF' | 'SCAN' | 'ZUGFERD' | 'XRECHNUNG';
  titel: string | null;
  aussteller: string | null;
  ausstellerUstId: string | null;
  empfaenger: string | null;
  rechnungsdatum: string | null;
  netto: number | null;
  steuer: number | null;
  brutto: number | null;
  positionen: number;
}

export interface Ergebnis {
  document_type: string;
  belegdatum: string | null;
  belegfeld_1: string | null;
  personenkonto: number | null;
  buchungszeilen: Buchungszeile[];
  buchungstext_kopf: string | null;
  konfidenz_score: number;
}

export interface Vorgang {
  id: string;
  status: Zustand;
  dateiname: string;
  quelle: Quelle;
  ergebnis: Ergebnis;
  hinweise: Hinweis[];
  eingegangenAm: string;
  entschiedenAm: string | null;
  entschiedenVon: string | null;
  entscheidungsnotiz: string | null;
  exportiertAm: string | null;
}

export class ApiFehler extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function ruf(pfad: string, token: string, init: RequestInit = {}): Promise<Response> {
  const antwort = await fetch(config.apiBasis + pfad, {
    ...init,
    headers: {
      ...(init.headers ?? {}),
      Authorization: 'Bearer ' + token,
    },
  });
  if (!antwort.ok) {
    throw new ApiFehler(antwort.status, await fehlertext(antwort));
  }
  return antwort;
}

/** Die API antwortet mit Problem Details; deren 'detail' ist die brauchbare Meldung. */
async function fehlertext(antwort: Response): Promise<string> {
  try {
    const koerper = await antwort.json();
    return koerper.detail ?? koerper.title ?? antwort.statusText;
  } catch {
    return antwort.statusText;
  }
}

async function json<T>(pfad: string, token: string, init?: RequestInit): Promise<T> {
  return (await ruf(pfad, token, init)).json() as Promise<T>;
}

export const api = {
  vorgaenge: (token: string, alle = false) =>
    json<Vorgang[]>('/api/v1/pruefungen?alle=' + alle, token),

  vorgang: (token: string, id: string) =>
    json<Vorgang>('/api/v1/pruefungen/' + encodeURIComponent(id), token),

  /** Das Original als Datenstrom, damit die Oberflaeche es weiterreichen kann. */
  beleg: (token: string, id: string) =>
    ruf('/api/v1/pruefungen/' + encodeURIComponent(id) + '/beleg', token),

  exportierbare: (token: string) => json<Vorgang[]>('/api/v1/export/offen', token),

  einreichen: async (token: string, datei: File): Promise<Vorgang> => {
    const koerper = new FormData();
    koerper.append('datei', datei);
    return json<Vorgang>('/api/v1/belege', token, { method: 'POST', body: koerper });
  },

  freigeben: (token: string, id: string, benutzer: string) =>
    json<Vorgang>('/api/v1/pruefungen/' + encodeURIComponent(id) + '/freigabe', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ benutzer }),
    }),

  ablehnen: (token: string, id: string, benutzer: string, grund: string) =>
    json<Vorgang>('/api/v1/pruefungen/' + encodeURIComponent(id) + '/ablehnung', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ benutzer, grund }),
    }),

  datevPaket: (token: string) =>
    ruf('/api/v1/export/datev', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    }),
};
