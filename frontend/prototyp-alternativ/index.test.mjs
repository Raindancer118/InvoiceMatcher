import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const previewUrl = new URL('./index.html', import.meta.url);

async function preview() {
  return readFile(previewUrl, 'utf8');
}

test('die Preview bildet den kompletten Pruefarbeitsplatz ab', async () => {
  const html = await preview();

  assert.match(html, /<title>Prüftisch · InvoiceMatcher<\/title>/);
  assert.match(html, /<main\b/);
  assert.match(html, /aria-label="Prüfwarteschlange"/);
  assert.match(html, /aria-label="Belegvorschau"/);
  assert.match(html, /aria-label="Kontierung prüfen"/);
  assert.match(html, />Freigeben</);
  assert.match(html, />Ablehnen</);
});

test('die Preview ist eigenstaendig und ohne externe Laufzeitabhaengigkeiten', async () => {
  const html = await preview();

  assert.doesNotMatch(html, /(?:src|href)=["']https?:\/\//i);
  assert.doesNotMatch(html, /\b(?:bootstrap|tailwind|inter)\b/i);
  assert.match(html, /<style>/);
  assert.match(html, /<script>/);
});

test('Tastatur, kleine Displays und reduzierte Bewegung sind beruecksichtigt', async () => {
  const html = await preview();

  assert.match(html, /:focus-visible/);
  assert.match(html, /prefers-reduced-motion:\s*reduce/);
  assert.match(html, /@media\s*\(max-width:\s*760px\)/);
  assert.match(html, /aria-live="polite"/);
});

test('die Warteschlangenfilter veraendern die sichtbaren Belege', async () => {
  const html = await preview();

  for (const status of ['offen', 'freigegeben', 'uebergeben', 'abgelehnt']) {
    assert.match(html, new RegExp(`data-state="${status}"`));
  }
  assert.match(html, /entry\.hidden\s*=\s*!show/);
});

test('alle vorhandenen Hauptbereiche sind im Prototyp navigierbar', async () => {
  const html = await preview();

  for (const view of ['uebersicht', 'pruefliste', 'export', 'einstellungen']) {
    assert.match(html, new RegExp(`data-view-target="${view}"`));
    assert.match(html, new RegExp(`data-view="${view}"`));
  }
  assert.match(html, /view\.hidden\s*=\s*view\.dataset\.view\s*!==\s*target/);
  assert.match(html, />Abmelden</);
});

test('Upload, Detailpruefung und Originalbeleg sind vollstaendig abgebildet', async () => {
  const html = await preview();

  assert.match(html, /accept="\.pdf,\.xml,application\/pdf,application\/xml"/);
  assert.doesNotMatch(html, /<input[^>]+type="file"[^>]+multiple/);
  for (const label of [
    'Belegart', 'Belegdatum', 'Belegnummer', 'Personenkonto', 'Aussteller',
    'Empfänger', 'USt-IdNr.', 'Netto / Steuer', 'Brutto',
    'Sachkonto', 'S/H', 'Steuerschl.', 'Umsatz',
  ]) {
    assert.match(html, new RegExp(label));
  }
  assert.match(html, />Original öffnen</);
  assert.match(html, />Freigeben</);
  assert.match(html, />Ablehnen</);
});

test('DATEV-Export und alle aktuellen Einstellungen sind enthalten', async () => {
  const html = await preview();

  assert.match(html, />Paket erzeugen und herunterladen</);
  for (const name of [
    'name', 'ustIdNr', 'steuernummer', 'aktiv', 'protokoll', 'host', 'port',
    'benutzername', 'passwort', 'ordner', 'verarbeitetOrdner',
    'fehlgeschlagenOrdner', 'taktSekunden', 'maxNachrichtenProDurchlauf',
    'maxAnhangGroesseKb', 'erlaubteAbsender',
  ]) {
    assert.match(html, new RegExp(`name="${name}"`));
  }
});

test('Anmeldung und beide vorhandenen Zugriffsfehler sind als Preview-Zustaende erreichbar', async () => {
  const html = await preview();

  assert.match(html, />Mit Authentik anmelden</);
  assert.match(html, />Anmeldung fehlgeschlagen</);
  assert.match(html, />Kein Zugriff</);
  assert.match(html, /data-auth-state="failed"/);
  assert.match(html, /data-auth-state="denied"/);
});
