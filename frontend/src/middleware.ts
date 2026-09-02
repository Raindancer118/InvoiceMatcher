/**
 * Waechter vor jeder Seite.
 *
 * <p>Ohne Sitzung geht es zur Anmeldung, ohne die verlangte Gruppe auf eine eigene Seite. Das
 * ist die Bequemlichkeitsschicht: die eigentliche Absicherung sitzt in der API, die jedes
 * Zugriffstoken selbst prueft. Wer diese Oberflaeche umgeht, kommt trotzdem nicht an die Belege.
 */
import { defineMiddleware } from 'astro:middleware';
import { config } from './lib/config';
import { sitzungLesen, sitzungSchreiben, type Sitzung } from './lib/session';
import { tokenErneuern } from './lib/oidc';

/** Seiten, die ohne Anmeldung erreichbar sein muessen. */
const OFFEN = ['/auth/login', '/auth/callback', '/auth/logout', '/kein-zugriff', '/anmeldung-fehlgeschlagen'];

/** Erneuert schon kurz vor Ablauf, damit ein laufender Aufruf nicht mittendrin scheitert. */
const VORLAUF_SEKUNDEN = 60;

export const onRequest = defineMiddleware(async (context, next) => {
  const pfad = context.url.pathname;
  if (OFFEN.includes(pfad) || pfad.startsWith('/_')) {
    return next();
  }

  let sitzung = await sitzungLesen(context.cookies);
  if (sitzung && sitzung.laeuftAb - VORLAUF_SEKUNDEN < Math.floor(Date.now() / 1000)) {
    sitzung = await erneuern(sitzung, context.cookies);
  }

  if (!sitzung) {
    const ziel = context.url.pathname + context.url.search;
    return context.redirect('/auth/login?ziel=' + encodeURIComponent(ziel));
  }

  if (!sitzung.benutzer.gruppen.includes(config.requiredGroup)) {
    return context.redirect('/kein-zugriff');
  }

  context.locals.sitzung = sitzung;
  return next();
});

async function erneuern(sitzung: Sitzung, cookies: Parameters<typeof sitzungSchreiben>[0]) {
  if (!sitzung.refreshToken) {
    return null;
  }
  const erneuert = await tokenErneuern(sitzung.refreshToken);
  if (!erneuert) {
    return null;
  }
  const neu: Sitzung = {
    accessToken: erneuert.accessToken,
    refreshToken: erneuert.refreshToken,
    idToken: erneuert.idToken || sitzung.idToken,
    laeuftAb: erneuert.laeuftAb,
    benutzer: {
      name: erneuert.name !== 'Unbekannt' ? erneuert.name : sitzung.benutzer.name,
      email: erneuert.email ?? sitzung.benutzer.email,
      gruppen: erneuert.gruppen.length > 0 ? erneuert.gruppen : sitzung.benutzer.gruppen,
    },
  };
  await sitzungSchreiben(cookies, neu);
  return neu;
}
