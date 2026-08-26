/**
 * Freigabe und Ablehnung eines Vorgangs.
 *
 * <p>Als Formular-POST mit anschliessender Weiterleitung, nicht als Aufruf aus dem Browser:
 * so bleibt der Ablauf ohne JavaScript vollstaendig, und ein Neuladen nach der Entscheidung
 * schickt sie nicht ein zweites Mal ab.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../../lib/api';

export const POST: APIRoute = async ({ params, request, locals, redirect }) => {
  const id = params.id!;
  const formular = await request.formData();
  const entscheidung = formular.get('entscheidung');
  const benutzer = locals.sitzung.benutzer.name;

  try {
    if (entscheidung === 'freigeben') {
      await api.freigeben(locals.sitzung.accessToken, id, benutzer);
      return redirect(ziel(id, 'Vorgang freigegeben.'));
    }
    if (entscheidung === 'ablehnen') {
      const grund = String(formular.get('grund') ?? '').trim();
      await api.ablehnen(
        locals.sitzung.accessToken,
        id,
        benutzer,
        // Die API verlangt eine Begruendung; ohne Eingabe steht wenigstens fest, wer entschied.
        grund !== '' ? grund : 'Ohne Angabe eines Grundes abgelehnt',
      );
      return redirect(ziel(id, 'Vorgang abgelehnt.'));
    }
    return redirect(ziel(id, 'Unbekannte Entscheidung, nichts geändert.'));
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return redirect(ziel(id, fehler.message));
    }
    throw fehler;
  }
};

const ziel = (id: string, meldung: string) =>
  `/vorgang/${encodeURIComponent(id)}?meldung=${encodeURIComponent(meldung)}`;
