/**
 * Erzeugt das DATEV-Paket und reicht es als Download durch.
 *
 * <p>Der Aufruf vermerkt jeden enthaltenen Vorgang als uebergeben - er darf deshalb nur
 * absichtlich ausgeloest werden, nie durch das blosse Aufrufen einer Seite. Daher POST.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ locals, redirect }) => {
  try {
    const antwort = await api.datevPaket(locals.sitzung.accessToken);
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
