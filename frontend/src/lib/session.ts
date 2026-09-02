/**
 * Die Sitzung als serverseitige Astro-Session (Dateisystem-Storage im Container).
 *
 * <p>Fruehere Fassung: die komplette Sitzung (Access-, ID-Token, Kontodaten) verschluesselt in
 * einem Cookie. Authentik packt ins ID-Token ein Base64-Avatarbild und weitere Claims; das
 * verschluesselte Cookie wurde dadurch ueber 6 KB gross - Browser verwerfen ein Set-Cookie ab
 * 4 KB stillschweigend. Die Sitzung landete nie im Browser, jede Seite sah "nicht angemeldet"
 * und schickte sofort zurueck zur Anmeldung: eine stille Neustart-Schleife ohne jeden Fehler
 * im Log. Die Sitzungsdaten liegen deshalb serverseitig; der Browser haelt nur die kurze,
 * von Astro selbst verwaltete Session-ID.
 */
import type { AstroSession } from 'astro';

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

const SITZUNGS_SCHLUESSEL = 'sitzung';
const ANMELDE_SCHLUESSEL = 'anmeldung';

export async function sitzungSchreiben(session: AstroSession | undefined, sitzung: Sitzung): Promise<void> {
  session?.set(SITZUNGS_SCHLUESSEL, sitzung, { ttl: 60 * 60 * 12 });
}

export async function sitzungLesen(session: AstroSession | undefined): Promise<Sitzung | null> {
  return (await session?.get<Sitzung>(SITZUNGS_SCHLUESSEL)) ?? null;
}

export function sitzungLoeschen(session: AstroSession | undefined): void {
  session?.delete(SITZUNGS_SCHLUESSEL);
}

export async function anmeldungSchreiben(
  session: AstroSession | undefined,
  zustand: Anmeldezustand,
): Promise<void> {
  session?.set(ANMELDE_SCHLUESSEL, zustand, { ttl: 10 * 60 });
}

export async function anmeldungLesen(session: AstroSession | undefined): Promise<Anmeldezustand | null> {
  return (await session?.get<Anmeldezustand>(ANMELDE_SCHLUESSEL)) ?? null;
}

export function anmeldungLoeschen(session: AstroSession | undefined): void {
  session?.delete(ANMELDE_SCHLUESSEL);
}
