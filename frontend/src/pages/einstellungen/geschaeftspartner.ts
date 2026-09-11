/**
 * Speichert die Geschaeftspartnerliste als Roh-JSON.
 *
 * <p>Formular-POST mit anschliessender Weiterleitung, wie die uebrigen Einstellungen-Formulare.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const geschaeftspartner = { json: String(formular.get('json') ?? '') };

  try {
    await api.geschaeftspartnerSpeichern(locals.sitzung.accessToken, geschaeftspartner);
    return redirect(ziel('Geschäftspartner gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}#geschaeftspartner`;
