/**
 * Nimmt einen Beleg aus der Ablageflaeche entgegen und reicht ihn an die API weiter.
 *
 * <p>Nach der Uebernahme geht es direkt in den Vorgang: wer einen Beleg einreicht, will als
 * Naechstes sehen, was daraus geworden ist.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';

export const POST: APIRoute = async ({ request, locals, redirect }) => {
  const formular = await request.formData();
  const datei = formular.get('datei');

  if (!(datei instanceof File) || datei.size === 0) {
    return redirect('/?meldung=' + encodeURIComponent('Es wurde keine Datei ausgewählt.'));
  }

  try {
    const vorgang = await api.einreichen(locals.sitzung.accessToken, datei);
    return redirect(
      `/vorgang/${encodeURIComponent(vorgang.id)}?meldung=` +
        encodeURIComponent(
          vorgang.status === 'OFFEN'
            ? 'Beleg angenommen, er braucht eine Prüfung.'
            : 'Beleg angenommen und automatisch freigegeben.',
        ),
    );
  } catch (fehler) {
    const text = fehler instanceof ApiFehler ? fehler.message : 'Beleg konnte nicht gelesen werden.';
    return redirect('/?meldung=' + encodeURIComponent(text));
  }
};
