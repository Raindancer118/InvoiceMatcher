/**
 * Speichert die Firmendaten.
 *
 * <p>Als Formular-POST mit anschliessender Weiterleitung, wie schon die Entscheidung ueber
 * einen Vorgang: der Ablauf bleibt ohne JavaScript vollstaendig.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const firma = {
    name: String(formular.get('name') ?? '').trim(),
    ustIdNr: leerAlsNull(formular.get('ustIdNr')),
    steuernummer: leerAlsNull(formular.get('steuernummer')),
  };

  try {
    await api.firmaSpeichern(locals.sitzung.accessToken, firma);
    return redirect(ziel('Firmendaten gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

function leerAlsNull(wert: FormDataEntryValue | null): string | null {
  const text = String(wert ?? '').trim();
  return text === '' ? null : text;
}

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}`;
