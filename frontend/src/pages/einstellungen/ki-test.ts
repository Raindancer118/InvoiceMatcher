/**
 * Testet die KI-Verbindung, ohne zu speichern - fuer den "Verbindung testen"-Knopf.
 *
 * <p>Anders als die uebrigen Einstellungen-Routen keine Weiterleitung, sondern eine
 * JSON-Antwort: der Knopf laeuft per {@code fetch} ohne Seitenwechsel, siehe
 * {@code einstellungen.astro}.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals }) => {
  const formular = await request.formData();
  const ki = {
    fastModell: String(formular.get('fastModell') ?? '').trim(),
    fastSchluessel: schluesselListe(formular.get('fastSchluessel')),
    smartModell: String(formular.get('smartModell') ?? '').trim(),
    smartSchluessel: schluesselListe(formular.get('smartSchluessel')),
  };

  try {
    const ergebnis = await api.kiVerbindungTesten(locals.sitzung.accessToken, ki);
    return new Response(JSON.stringify(ergebnis), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (fehler) {
    const meldung = fehler instanceof ApiFehler ? fehler.message : 'Verbindungstest fehlgeschlagen.';
    return new Response(JSON.stringify({ fehler: meldung }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    });
  }
};

function schluesselListe(wert: FormDataEntryValue | null): string[] {
  return String(wert ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '');
}
