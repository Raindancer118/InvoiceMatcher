/**
 * Reicht das Belegbild aus der API durch.
 *
 * <p>Der Browser darf die API nicht selbst ansprechen - er haette das Zugriffstoken nicht und
 * soll es auch nie bekommen. Deshalb laeuft das Original ueber diesen Weg, mit dem Medientyp
 * und dem Dateinamen, die die API mitgibt.
 */
import type { APIRoute } from 'astro';
import { api, ApiFehler } from '../../../lib/api';

export const GET: APIRoute = async ({ params, locals }) => {
  try {
    const antwort = await api.beleg(locals.sitzung.accessToken, params.id!);
    return new Response(antwort.body, {
      status: 200,
      headers: {
        'Content-Type': antwort.headers.get('Content-Type') ?? 'application/octet-stream',
        'Content-Disposition': antwort.headers.get('Content-Disposition') ?? 'inline',
        // Aufbewahrungspflichtige Belege gehoeren nicht in einen geteilten Zwischenspeicher.
        'Cache-Control': 'private, max-age=300',
      },
    });
  } catch (fehler) {
    if (fehler instanceof ApiFehler) {
      return new Response(fehler.message, { status: fehler.status });
    }
    throw fehler;
  }
};
