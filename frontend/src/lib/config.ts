/**
 * Einstellungen aus der Umgebung.
 *
 * <p>Fehlt etwas Sicherheitsrelevantes, bricht der Start ab statt still weiterzulaufen: eine
 * Oberflaeche, die Belege ohne Anmeldung zeigt, faellt sonst erst im Betrieb auf.
 */

function pflicht(name: string): string {
  const wert = process.env[name];
  if (!wert || wert.trim() === '') {
    throw new Error(`Umgebungsvariable ${name} fehlt. Siehe .env.example.`);
  }
  return wert.trim();
}

function optional(name: string, vorgabe: string): string {
  const wert = process.env[name];
  return wert && wert.trim() !== '' ? wert.trim() : vorgabe;
}

const redirectUri = pflicht('OIDC_REDIRECT_URI');

export const config = {
  apiBasis: optional('INVOICEMATCHER_API', 'http://localhost:8080'),
  oidc: {
    issuer: pflicht('OIDC_ISSUER'),
    clientId: pflicht('OIDC_CLIENT_ID'),
    clientSecret: pflicht('OIDC_CLIENT_SECRET'),
    redirectUri,
    postLogoutRedirectUri: optional('OIDC_POST_LOGOUT_REDIRECT_URI', '/'),
  },
  sessionSecret: pflicht('SESSION_SECRET'),
  requiredGroup: optional('REQUIRED_GROUP', 'InvoiceMatcher'),
  // Fuer die eigene CSRF-Pruefung in middleware.ts: der oeffentliche Ursprung der Oberflaeche,
  // aus der ohnehin gepflegten Redirect-URI abgeleitet statt einer weiteren Umgebungsvariable.
  origin: new URL(redirectUri).origin,
} as const;
