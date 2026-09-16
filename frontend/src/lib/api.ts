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

/** Die Herleitung der KI zu einer Kontierung - aus der zweiten, rein erklaerenden Anfrage. */
export interface Gedanken {
  modell: string;
  kurzfassung: string | null;
  schritte: { titel: string | null; text: string }[];
  unsicherheiten: string[];
  gegenprobe: string | null;
}

export interface Vorgang {
  id: string;
  status: Zustand;
  dateiname: string;
  quelle: Quelle;
  ergebnis: Ergebnis;
  hinweise: Hinweis[];
  gedanken: Gedanken | null;
  eingegangenAm: string;
  entschiedenAm: string | null;
  entschiedenVon: string | null;
  entscheidungsnotiz: string | null;
  exportiertAm: string | null;
}

/** Zustand eines einzelnen KI-Schluessels; `schluessel` ist immer nur die Maske. */
export interface SchluesselStatus {
  stufe: 'fast' | 'smart';
  anbieter: string;
  position: number;
  schluessel: string;
  verfuegbar: boolean;
  gesperrtBis: string | null;
  nutzungen: number;
  erfolge: number;
  fehler: number;
  kontingenttreffer: number;
  zuverlaessigkeit: number;
  probe: {
    ok: boolean;
    meldung: string;
    dauerMs: number;
    restAnfragen: number | null;
    restToken: number | null;
    kontingentZurueckAb: string | null;
    /** Wann diese Pruefung lief - der Server merkt sie sich ueber den Seitenaufruf hinaus. */
    geprueftAm: string;
  } | null;
}

export interface Firma {
  name: string;
  ustIdNr: string | null;
  steuernummer: string | null;
}

export interface MailEingang {
  aktiv: boolean;
  protokoll: string;
  host: string;
  port: number;
  benutzername: string;
  passwortGesetzt: boolean;
  ordner: string;
  verarbeitetOrdner: string;
  fehlgeschlagenOrdner: string;
  taktSekunden: number;
  maxNachrichtenProDurchlauf: number;
  maxAnhangGroesseKb: number;
  erlaubteAbsender: string[];
}

/** Wie {@link MailEingang}, aber mit dem Passwort zum Speichern statt dem Gesetzt-Merker. */
export interface MailEingangEingabe extends Omit<MailEingang, 'passwortGesetzt'> {
  passwort: string;
}

/** Kontenrahmen bzw. Geschaeftspartnerliste als Roh-JSON - jederzeit austauschbar. */
export interface JsonEinstellung {
  json: string;
}

export interface Ki {
  fastModell: string;
  fastSchluesselGesetzt: boolean;
  fastSchluesselAnzahl: number;
  smartModell: string;
  smartSchluesselGesetzt: boolean;
  smartSchluesselAnzahl: number;
}

/** Wie {@link Ki}, aber mit den Schluesseln zum Speichern statt den Gesetzt-Merkern. */
export interface KiEingabe {
  fastModell: string;
  fastSchluessel: string[];
  smartModell: string;
  smartSchluessel: string[];
}

export interface KiTestErgebnis {
  fastOk: boolean;
  fastMeldung: string;
  smartOk: boolean;
  smartMeldung: string;
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

  datevPaket: (token: string, vorgaenge: string[] = [], erneut = false) =>
    ruf('/api/v1/export/datev', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vorgaenge, erneut }),
    }),

  firma: (token: string) => json<Firma>('/api/v1/einstellungen/firma', token),

  firmaSpeichern: (token: string, firma: Firma) =>
    json<Firma>('/api/v1/einstellungen/firma', token, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(firma),
    }),

  mailEingang: (token: string) => json<MailEingang>('/api/v1/einstellungen/mail-eingang', token),

  mailEingangSpeichern: (token: string, mail: MailEingangEingabe) =>
    json<MailEingang>('/api/v1/einstellungen/mail-eingang', token, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(mail),
    }),

  kontenrahmen: (token: string) => json<JsonEinstellung>('/api/v1/einstellungen/kontenrahmen', token),

  kontenrahmenSpeichern: (token: string, kontenrahmen: JsonEinstellung) =>
    json<JsonEinstellung>('/api/v1/einstellungen/kontenrahmen', token, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(kontenrahmen),
    }),

  geschaeftspartner: (token: string) => json<JsonEinstellung>('/api/v1/einstellungen/geschaeftspartner', token),

  geschaeftspartnerSpeichern: (token: string, geschaeftspartner: JsonEinstellung) =>
    json<JsonEinstellung>('/api/v1/einstellungen/geschaeftspartner', token, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(geschaeftspartner),
    }),

  kiEinstellungen: (token: string) => json<Ki>('/api/v1/einstellungen/ki', token),

  kiEinstellungenSpeichern: (token: string, ki: KiEingabe) =>
    json<Ki>('/api/v1/einstellungen/ki', token, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ki),
    }),

  /** Kostenlos: der Zustand, den der laufende Schluessel-Pool ohnehin kennt. */
  kiSchluessel: (token: string) => json<SchluesselStatus[]>('/api/v1/einstellungen/ki/schluessel', token),

  /** Kostet Kontingent: fragt jeden Schluessel einzeln beim Anbieter an. */
  kiSchluesselPruefen: (token: string) =>
    json<SchluesselStatus[]>('/api/v1/einstellungen/ki/schluessel/pruefen', token, { method: 'POST' }),

  kiVerbindungTesten: (token: string, ki: KiEingabe) =>
    json<KiTestErgebnis>('/api/v1/einstellungen/ki/test', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ki),
    }),
};
