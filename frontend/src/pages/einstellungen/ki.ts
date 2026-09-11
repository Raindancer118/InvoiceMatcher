/**
 * Speichert die KI-Modelleinstellungen.
 *
 * <p>Ein leeres Schluesselfeld wird unveraendert an die API gereicht - dort entscheidet
 * {@code AiSettingsService.update}, dass eine leere Liste "unveraendert" statt "loeschen"
 * bedeutet. Die Oberflaeche zeigt gespeicherte Schluessel nie im Klartext an.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const ki = {
    fastModell: String(formular.get('fastModell') ?? '').trim(),
    fastSchluessel: schluesselListe(formular.get('fastSchluessel')),
    smartModell: String(formular.get('smartModell') ?? '').trim(),
    smartSchluessel: schluesselListe(formular.get('smartSchluessel')),
  };

  try {
    await api.kiEinstellungenSpeichern(locals.sitzung.accessToken, ki);
    return redirect(ziel('KI-Einstellungen gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

function schluesselListe(wert: FormDataEntryValue | null): string[] {
  return String(wert ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '');
}

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}#ki`;
