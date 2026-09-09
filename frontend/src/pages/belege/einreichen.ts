/** Jede Datei wird einzeln über die bestehende Beleg-API verarbeitet. */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../lib/api';
import type { UploadErgebnis } from '../../lib/upload';

export const POST: APIRoute = async ({ request, locals, session, redirect }) => {
  const formular = await request.formData();
  const dateien = formular.getAll('datei').filter((eintrag): eintrag is File => eintrag instanceof File && eintrag.name !== '');
  if (dateien.length === 0) {
    return redirect('/?meldung=' + encodeURIComponent('Es wurde keine Datei ausgewählt.'));
  }
  if (dateien.length > 50) {
    return redirect('/?meldung=' + encodeURIComponent('Bitte höchstens 50 Belege auf einmal auswählen. Es wurde nichts eingereicht.'));
  }
  if (!session) throw new Error('Upload-Ergebnis benötigt eine Sitzung.');
  const ergebnisse: UploadErgebnis[] = [];
  for (const datei of dateien) {
    if (datei.size === 0 || !/\.(pdf|xml)$/i.test(datei.name)) {
      ergebnisse.push({ dateiname: datei.name, meldung: datei.size === 0
        ? 'Die Datei ist leer.' : 'Bitte eine PDF- oder XML-Datei einreichen.' });
      continue;
    }
    try {
      const vorgang = await api.einreichen(locals.sitzung.accessToken, datei);
      ergebnisse.push({ dateiname: datei.name, id: vorgang.id,
        meldung: vorgang.status === 'OFFEN' ? 'Angenommen, Prüfung erforderlich.' : 'Angenommen und automatisch freigegeben.' });
    } catch (fehler) {
      const unklar = !(fehler instanceof ApiFehler) || fehler.status >= 500;
      const meldung = !unklar && fehler instanceof ApiFehler ? fehler.message
        : 'Verarbeitung fehlgeschlagen. Bitte prüfen, ob der Beleg bereits in der Prüfliste liegt, bevor du ihn erneut einreichst.';
      ergebnisse.push({ dateiname: datei.name, meldung, unklar });
    }
  }
  if (ergebnisse.length === 1 && ergebnisse[0]!.id) {
    return redirect('/vorgang/' + encodeURIComponent(ergebnisse[0]!.id!) + '?meldung=' + encodeURIComponent(ergebnisse[0]!.meldung));
  }
  const bericht = crypto.randomUUID();
  session.set('upload:' + bericht, ergebnisse, { ttl: 3600 });
  return redirect('/belege/ergebnis?bericht=' + bericht);
};
