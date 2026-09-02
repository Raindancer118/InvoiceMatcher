/**
 * Speichert die Belegannahme-per-Mail-Einstellungen.
 *
 * <p>Ein leeres Passwortfeld wird unveraendert an die API gereicht - dort entscheidet
 * {@code MailIntakeSettingsService.update}, dass ein leerer Wert "unveraendert" statt
 * "loeschen" bedeutet. Die Oberflaeche zeigt das vorhandene Passwort nie an.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const mail = {
    aktiv: formular.get('aktiv') === 'on',
    protokoll: String(formular.get('protokoll') ?? 'imaps'),
    host: String(formular.get('host') ?? '').trim(),
    port: Number(formular.get('port')),
    benutzername: String(formular.get('benutzername') ?? '').trim(),
    passwort: String(formular.get('passwort') ?? ''),
    ordner: String(formular.get('ordner') ?? '').trim(),
    verarbeitetOrdner: String(formular.get('verarbeitetOrdner') ?? '').trim(),
    fehlgeschlagenOrdner: String(formular.get('fehlgeschlagenOrdner') ?? '').trim(),
    taktSekunden: Number(formular.get('taktSekunden')),
    maxNachrichtenProDurchlauf: Number(formular.get('maxNachrichtenProDurchlauf')),
    maxAnhangGroesseKb: Number(formular.get('maxAnhangGroesseKb')),
    erlaubteAbsender: String(formular.get('erlaubteAbsender') ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s !== ''),
  };

  try {
    await api.mailEingangSpeichern(locals.sitzung.accessToken, mail);
    return redirect(ziel('Mail-Eingang gespeichert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(fehler.message));
    }
    throw fehler;
  }
};

const ziel = (meldung: string) => `/einstellungen?meldung=${encodeURIComponent(meldung)}`;
