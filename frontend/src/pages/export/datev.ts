/**
 * Erzeugt das DATEV-Paket und reicht es als Download durch.
 *
 * <p>Der Aufruf vermerkt jeden enthaltenen Vorgang als uebergeben - er darf deshalb nur
 * absichtlich ausgeloest werden, nie durch das blosse Aufrufen einer Seite. Daher POST.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const erneut = formular.get('erneut') === 'on';
  const vorgaenge = formular.getAll('vorgaenge').filter((v): v is string => typeof v === 'string' && v.trim() !== '');
  if (erneut && vorgaenge.length === 0) {
    return redirect('/export?meldung=' + encodeURIComponent('Zum erneuten Export bitte mindestens einen Beleg auswählen.'));
  }
  try {
    const antwort = await api.datevPaket(locals.sitzung.accessToken, vorgaenge, erneut);
    return new Response(antwort.body, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': antwort.headers.get('Content-Disposition') ?? 'attachment; filename="datev.zip"',
      },
    });
  } catch (fehler) {
    const text = fehler instanceof ApiFehler
      ? fehler.message
      : 'Das Paket konnte nicht erzeugt werden.';
    return redirect('/export?meldung=' + encodeURIComponent(text));
  }
};
