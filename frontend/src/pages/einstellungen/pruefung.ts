/**
 * Speichert die Freigabeschwelle.
 *
 * <p>Die Oberflaeche fuehrt sie in Prozent - das ist die Sprache, in der ueber Sicherheit
 * gesprochen wird; die API rechnet in Konfidenz zwischen 0 und 1.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const prozent = Number(String(formular.get('freigabeschwelle') ?? '').replace(',', '.'));

  if (!Number.isFinite(prozent) || prozent < 0 || prozent > 100) {
    return redirect(ziel('Die Freigabeschwelle muss zwischen 0 und 100 Prozent liegen.'));
  }

  try {
    await api.pruefungSpeichern(locals.sitzung.accessToken, Math.round(prozent) / 100);
    return redirect(ziel('Freigabeschwelle gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}#pruefung`;
