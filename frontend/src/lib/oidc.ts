/**
 * Anmeldung gegen Authentik (portal.tstieh.de).
 *
 * <p>Authorization Code Flow mit PKCE. Die Konfiguration des Herausgebers wird einmal geholt
 * und behalten - sie aendert sich nicht zwischen zwei Anmeldungen, und ein Netzaufruf je
 * Anmeldeversuch waere eine unnoetige Abhaengigkeit im heissen Weg.
 */
import * as client from 'openid-client';
import { config } from './config';

let konfiguration: client.Configuration | null = null;

export async function herausgeber(): Promise<client.Configuration> {
  if (!konfiguration) {
    konfiguration = await client.discovery(
      new URL(config.oidc.issuer),
      config.oidc.clientId,
      config.oidc.clientSecret,
    );
  }
  return konfiguration;
}

export interface Anmeldebeginn {
  ziel: URL;
  state: string;
  nonce: string;
  codeVerifier: string;
}

export async function anmeldungBeginnen(): Promise<Anmeldebeginn> {
  const konfig = await herausgeber();
  const codeVerifier = client.randomPKCECodeVerifier();
  const codeChallenge = await client.calculatePKCECodeChallenge(codeVerifier);
  const state = client.randomState();
  const nonce = client.randomNonce();

  const ziel = client.buildAuthorizationUrl(konfig, {
    redirect_uri: config.oidc.redirectUri,
    // 'groups' liefert die Gruppenzugehoerigkeit; ohne sie koennte niemand zugeordnet werden.
    scope: 'openid profile email groups',
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    state,
    nonce,
  });

  return { ziel, state, nonce, codeVerifier };
}

export interface Anmeldeergebnis {
  accessToken: string;
  refreshToken?: string;
  idToken: string;
  laeuftAb: number;
  name: string;
  email?: string;
  gruppen: string[];
}

export async function anmeldungAbschliessen(
  aufrufUrl: URL,
  erwartet: { state: string; nonce: string; codeVerifier: string },
): Promise<Anmeldeergebnis> {
  const konfig = await herausgeber();
  // openid-client leitet die 'redirect_uri' fuer den Token-Request aus dem Schema/Host der
  // uebergebenen URL ab. Hinter dem Reverse Proxy sieht Astro das Original-Schema (https) nicht
  // zuverlaessig - ohne Korrektur wuerde ein 'http://...' gesendet, das nicht zur bei Authentik
  // registrierten Redirect-URI passt und der Token-Austausch schlaegt fehl.
  const korrigierteUrl = new URL(config.oidc.redirectUri);
  korrigierteUrl.search = aufrufUrl.search;
  const token = await client.authorizationCodeGrant(konfig, korrigierteUrl, {
    pkceCodeVerifier: erwartet.codeVerifier,
    expectedState: erwartet.state,
    expectedNonce: erwartet.nonce,
  });

  const angaben = token.claims();
  if (!angaben) {
    throw new Error('Authentik lieferte kein ID-Token mit Angaben zum Konto');
  }
  return {
    accessToken: token.access_token,
    refreshToken: token.refresh_token,
    idToken: token.id_token!,
    laeuftAb: Math.floor(Date.now() / 1000) + (token.expires_in ?? 3600),
    name: (angaben.name as string) ?? (angaben.preferred_username as string) ?? 'Unbekannt',
    email: angaben.email as string | undefined,
    gruppen: gruppenAus(angaben),
  };
}

/** Erneuert das Zugriffstoken, solange Authentik das Konto noch kennt. */
export async function tokenErneuern(refreshToken: string): Promise<Anmeldeergebnis | null> {
  try {
    const konfig = await herausgeber();
    const token = await client.refreshTokenGrant(konfig, refreshToken);
    const angaben = token.claims();
    return {
      accessToken: token.access_token,
      refreshToken: token.refresh_token ?? refreshToken,
      idToken: token.id_token ?? '',
      laeuftAb: Math.floor(Date.now() / 1000) + (token.expires_in ?? 3600),
      name: (angaben?.name as string) ?? 'Unbekannt',
      email: angaben?.email as string | undefined,
      gruppen: gruppenAus(angaben ?? {}),
    };
  } catch {
    // Zurueckgezogen oder abgelaufen: der Aufrufer meldet neu an.
    return null;
  }
}

export async function abmeldeZiel(idToken: string): Promise<URL> {
  const konfig = await herausgeber();
  return client.buildEndSessionUrl(konfig, {
    id_token_hint: idToken,
    post_logout_redirect_uri: config.oidc.postLogoutRedirectUri,
  });
}

function gruppenAus(angaben: Record<string, unknown>): string[] {
  const gruppen = angaben.groups;
  return Array.isArray(gruppen) ? gruppen.map(String) : [];
}
