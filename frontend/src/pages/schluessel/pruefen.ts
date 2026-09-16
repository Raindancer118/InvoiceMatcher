/**
 * Loest die Lebendpruefung aller Schluessel aus - als POST, weil sie beim Anbieter Kontingent
 * kostet und deshalb nie durch blosses Aufrufen einer Adresse passieren darf.
 *
 * <p>Antwortet mit JSON statt einer Weiterleitung: der Knopf auf der Schluesselseite laeuft per
 * {@code fetch} und schreibt die Ergebnisse in die bestehende Tabelle (gleiches Muster wie
 * {@code einstellungen/ki-test.ts}).
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ locals }) => {
  try {
    const ergebnis = await api.kiSchluesselPruefen(locals.sitzung.accessToken);
    return new Response(JSON.stringify(ergebnis), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (fehler) {
    const meldung = fehler instanceof ApiFehler ? fehler.message : 'Schlüsselprüfung fehlgeschlagen.';
    return new Response(JSON.stringify({ fehler: meldung }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    });
  }
};
