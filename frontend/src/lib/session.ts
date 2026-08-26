/**
 * Die Sitzung als verschluesseltes Cookie.
 *
 * <p>Kein Sitzungsspeicher im Arbeitsspeicher: der ueberlebt keinen Neustart und keine zweite
 * Instanz. Verschluesselt statt nur signiert, weil das Zugriffstoken darin liegt - signiert
 * waere es fuer jeden lesbar, der das Cookie in die Hand bekommt.
 *
 * <p>Verschluesselt wird mit `jose` (JWE, A256GCM). Eigene Krypto waere hier der falsche Ort
 * zum Sparen.
 */
import { CompactEncrypt, compactDecrypt } from 'jose';
import { createHash } from 'node:crypto';
import type { AstroCookies } from 'astro';
import { config } from './config';

export const SITZUNGS_COOKIE = 'im_sitzung';

/** Kurzlebiges Cookie fuer den Zustand waehrend der Anmeldung (state, nonce, PKCE). */
export const ANMELDE_COOKIE = 'im_anmeldung';

export interface Sitzung {
  accessToken: string;
  refreshToken?: string;
  idToken: string;
  /** Sekunden seit Epoche, ab wann das Zugriffstoken erneuert werden muss. */
  laeuftAb: number;
  benutzer: {
    name: string;
    email?: string;
    gruppen: string[];
  };
}

export interface Anmeldezustand {
  state: string;
  nonce: string;
  codeVerifier: string;
  /** Wohin nach erfolgreicher Anmeldung zurueckgesprungen wird. */
  ziel: string;
}

/** Der Schluessel muss 32 Byte haben; das Geheimnis darf beliebig lang sein. */
const schluessel = createHash('sha256').update(config.sessionSecret).digest();

async function verschluesseln(inhalt: unknown): Promise<string> {
  return new CompactEncrypt(new TextEncoder().encode(JSON.stringify(inhalt)))
    .setProtectedHeader({ alg: 'dir', enc: 'A256GCM' })
    .encrypt(schluessel);
}

async function entschluesseln<T>(text: string): Promise<T | null> {
  try {
    const { plaintext } = await compactDecrypt(text, schluessel);
    return JSON.parse(new TextDecoder().decode(plaintext)) as T;
  } catch {
    // Abgelaufen, manipuliert oder mit einem alten Schluessel erzeugt: als abgemeldet behandeln.
    return null;
  }
}

const COOKIE_VORGABEN = {
  httpOnly: true,
  sameSite: 'lax',
  path: '/',
  secure: process.env.NODE_ENV === 'production',
} as const;

export async function sitzungSchreiben(cookies: AstroCookies, sitzung: Sitzung): Promise<void> {
  cookies.set(SITZUNGS_COOKIE, await verschluesseln(sitzung), {
    ...COOKIE_VORGABEN,
    maxAge: 60 * 60 * 12,
  });
}

export async function sitzungLesen(cookies: AstroCookies): Promise<Sitzung | null> {
  const roh = cookies.get(SITZUNGS_COOKIE)?.value;
  return roh ? entschluesseln<Sitzung>(roh) : null;
}

export function sitzungLoeschen(cookies: AstroCookies): void {
  cookies.delete(SITZUNGS_COOKIE, { path: '/' });
}

export async function anmeldungSchreiben(cookies: AstroCookies, zustand: Anmeldezustand): Promise<void> {
  cookies.set(ANMELDE_COOKIE, await verschluesseln(zustand), {
    ...COOKIE_VORGABEN,
    maxAge: 10 * 60,
  });
}

export async function anmeldungLesen(cookies: AstroCookies): Promise<Anmeldezustand | null> {
  const roh = cookies.get(ANMELDE_COOKIE)?.value;
  return roh ? entschluesseln<Anmeldezustand>(roh) : null;
}

export function anmeldungLoeschen(cookies: AstroCookies): void {
  cookies.delete(ANMELDE_COOKIE, { path: '/' });
}
