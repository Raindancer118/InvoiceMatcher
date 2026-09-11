/**
 * Speichert den Kontenrahmen als Roh-JSON.
 *
 * <p>Formular-POST mit anschliessender Weiterleitung, wie die uebrigen Einstellungen-Formulare.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const kontenrahmen = { json: String(formular.get('json') ?? '') };

  try {
    await api.kontenrahmenSpeichern(locals.sitzung.accessToken, kontenrahmen);
    return redirect(ziel('Kontenrahmen gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}#kontenrahmen`;
