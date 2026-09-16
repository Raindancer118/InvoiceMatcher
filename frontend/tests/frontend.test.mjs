import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { createStorage } from 'unstorage';
import fsDriver from 'unstorage/drivers/fs-lite';
import { AstroSession, PERSIST_SYMBOL } from '../node_modules/astro/dist/core/session/runtime.js';

// Echter SSR-Server und echte Frontend-Routen, API-Grenze als isolierter Testserver.
test('Prüftisch: geschützte Seiten, Originale und vollständige Formularwege', async (t) => {
  const dir = await mkdtemp(join(tmpdir(), 'invoicematcher-frontend-'));
  const buildDir = fileURLToPath(new URL('../.astro/test-build-' + randomUUID(), import.meta.url));
  t.after(() => rm(dir, { recursive: true, force: true }));
  t.after(() => rm(buildDir, { recursive: true, force: true }));
  const vorgang = {
    id: 'test-beleg', status: 'OFFEN', dateiname: 'rechnung.xml',
    quelle: { format: 'XRECHNUNG', aussteller: 'Testlieferant', empfaenger: 'Testfirma',
      ausstellerUstId: 'DE123456789', rechnungsdatum: '2026-09-09', netto: 100, steuer: 19, brutto: 119 },
    ergebnis: { document_type: 'EINGANGSRECHNUNG', belegfeld_1: 'R-42', personenkonto: 70001,
      konfidenz_score: 0.8, buchungstext_kopf: 'Testauftrag',
      buchungszeilen: [{ sachkonto: 3400, soll_haben_kennzeichen: 'S', steuerschluessel: 9, umsatz: 100, buchungstext: 'Prüfbare Position' }] },
    hinweise: [{ ruleId: '1.3', severity: 'WARNUNG', message: 'Bitte Original prüfen.' }],
    gedanken: { modell: 'groq/openai-gpt-oss-120b',
      kurzfassung: 'Ich lese eine ganz normale Eingangsrechnung von einem Lieferanten, den ich kenne.',
      schritte: [{ titel: 'Belegart', text: 'Ich sehe oben groß „Rechnung“ und darunter die Nummer R-42 - das kann ich buchen.' },
        { titel: 'Personenkonto', text: 'Den Testlieferant führe ich unter 70001, also geht der Beleg auf dieses Konto.' }],
      unsicherheiten: ['Beim Zuschnitt bin ich mir nicht sicher, ob 3400 oder 3401 richtig ist.'],
      gegenprobe: 'Ich habe kurz überlegt, ob das ein Rechnungsausgang sein könnte - dafür müsste die eigene Firma der Aussteller sein.' },
    eingegangenAm: '2026-09-01T10:00:00Z', exportiertAm: null,
  };
  const firma = { name: 'Testfirma', ustIdNr: 'DE123456789', steuernummer: '123/456' };
  const mail = { aktiv: true, protokoll: 'imaps', host: 'mail.test', port: 993, benutzername: 'belege',
    passwortGesetzt: true, ordner: 'INBOX', verarbeitetOrdner: 'Verarbeitet', fehlgeschlagenOrdner: 'Fehler',
    taktSekunden: 60, maxNachrichtenProDurchlauf: 10, maxAnhangGroesseKb: 10000, erlaubteAbsender: [] };
  const schluessel = [
    { stufe: 'fast', anbieter: 'groq', position: 1, schluessel: 'gsk_…a1b2', verfuegbar: true,
      gesperrtBis: null, nutzungen: 12, erfolge: 11, fehler: 1, kontingenttreffer: 0,
      zuverlaessigkeit: 0.9, probe: null },
    { stufe: 'smart', anbieter: 'google-gemini', position: 1, schluessel: 'AIz…9876', verfuegbar: false,
      gesperrtBis: '2026-09-09T12:30:00Z', nutzungen: 4, erfolge: 3, fehler: 1, kontingenttreffer: 1,
      zuverlaessigkeit: 0.6, probe: null },
  ];
  const calls = [];
  const backend = createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = Buffer.concat(chunks).toString();
    calls.push({ path: req.url, method: req.method, body, token: req.headers.authorization });
    res.setHeader('Content-Type', 'application/json');
    const path = req.url.split('?')[0];
    if (path === '/api/v1/belege' && body.includes('defekt.xml')) {
      res.statusCode = 422;
      res.end(JSON.stringify({ detail: 'Ungültige XML-Struktur in Zeile 2, Spalte 5: End-Tag fehlt' }));
      return;
    }
    if (path === '/api/v1/einstellungen/ki/schluessel') { res.end(JSON.stringify(schluessel)); return; }
    if (path === '/api/v1/einstellungen/ki/schluessel/pruefen') {
      res.end(JSON.stringify(schluessel.map((eintrag) => ({ ...eintrag, probe: {
        ok: eintrag.stufe === 'fast', meldung: eintrag.stufe === 'fast' ? 'Erreichbar' : 'Kontingent erschoepft',
        dauerMs: 120, restAnfragen: eintrag.stufe === 'fast' ? 941 : null,
        restToken: eintrag.stufe === 'fast' ? 17532 : null,
        kontingentZurueckAb: eintrag.stufe === 'fast' ? null : '2026-09-09T12:30:00Z',
        geprueftAm: '2026-09-09T12:00:00Z' } }))));
      return;
    }
    if (path.endsWith('/beleg')) { res.setHeader('Content-Type', 'application/xml'); res.end('<Invoice><Note>&lt;script&gt;</Note></Invoice>'); }
    else if (path.endsWith('/freigabe')) { vorgang.status = 'FREIGEGEBEN'; res.end(JSON.stringify(vorgang)); }
    else if (path.endsWith('/ablehnung')) { vorgang.status = 'ABGELEHNT'; vorgang.entscheidungsnotiz = JSON.parse(body).grund; res.end(JSON.stringify(vorgang)); }
    else if (path.endsWith('/datev')) { vorgang.exportiertAm = '2026-09-09T12:00:00Z'; res.setHeader('Content-Type', 'application/zip'); res.end(Buffer.from('PK-test-package')); }
    else if (path.endsWith('/export/offen')) res.end(JSON.stringify(vorgang.status === 'FREIGEGEBEN' && !vorgang.exportiertAm ? [vorgang] : []));
    else if (path.endsWith('/firma')) res.end(JSON.stringify(firma));
    else if (path.endsWith('/mail-eingang')) res.end(JSON.stringify(mail));
    else if (path === '/api/v1/pruefungen') res.end(JSON.stringify([vorgang]));
    else res.end(JSON.stringify(vorgang));
  });
  backend.listen(0, '127.0.0.1');
  await once(backend, 'listening');
  t.after(() => { backend.closeAllConnections(); backend.close(); });
  const portProbe = createServer();
  portProbe.listen(0, '127.0.0.1');
  await once(portProbe, 'listening');
  const port = portProbe.address().port;
  await new Promise((resolve) => portProbe.close(resolve));
  const origin = `http://127.0.0.1:${port}`;
  const env = { ...process.env, SESSION_DIR: dir, HOST: '127.0.0.1', PORT: String(port),
    INVOICEMATCHER_API: `http://127.0.0.1:${backend.address().port}`,
    OIDC_ISSUER: 'https://identity.example.test', OIDC_CLIENT_ID: 'test', OIDC_CLIENT_SECRET: 'test-only',
    OIDC_REDIRECT_URI: origin + '/auth/callback', SESSION_SECRET: 'test-only-session-secret', REQUIRED_GROUP: 'InvoiceMatcher' };
  const build = spawn('npm', ['run', 'build', '--', '--outDir', buildDir], { env, cwd: new URL('..', import.meta.url), stdio: 'pipe' });
  let output = '';
  build.stdout.on('data', (data) => output += data);
  build.stderr.on('data', (data) => output += data);
  assert.equal((await once(build, 'exit'))[0], 0, output);
  const server = spawn(process.execPath, [join(buildDir, 'server/entry.mjs')], { env, cwd: new URL('..', import.meta.url), stdio: 'pipe' });
  server.stdout.on('data', (data) => output += data);
  server.stderr.on('data', (data) => output += data);
  t.after(async () => { if (server.exitCode === null) { server.kill(); await once(server, 'exit'); } });
  let ready = false;
  for (let i = 0; i < 100; i++) {
    try { if ((await fetch(origin + '/anmeldung-fehlgeschlagen')).ok) { ready = true; break; } } catch {}
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  assert.ok(ready, output);
  let cookie;
  const storage = createStorage({ driver: fsDriver({ base: dir }) });
  const session = new AstroSession({ cookies: { get: () => undefined, set: (name, value) => { cookie = `${name}=${value}`; } },
    config: { driver: 'test' }, runtimeMode: 'development', mockStorage: storage, logger: console });
  session.set('sitzung', { accessToken: 'test-access-token', idToken: 'test-id-token',
    laeuftAb: Math.floor(Date.now() / 1000) + 3600, benutzer: { name: 'Test Prüfer', gruppen: ['InvoiceMatcher'] } });
  await session[PERSIST_SYMBOL]();
  const get = (path, init = {}) => fetch(origin + path, { redirect: 'manual', ...init, headers: { cookie, ...init.headers } });
  const post = (path, data) => get(path, { method: 'POST', headers: { origin }, body: new URLSearchParams(data) });

  await t.test('Anmeldung bleibt erforderlich, Seiten rendern echte API-Daten ohne Token-Leak', async () => {
    const anonymous = await fetch(origin + '/', { redirect: 'manual' });
    assert.match(anonymous.headers.get('location'), /^\/auth\/login/);
    for (const path of ['/', '/pruefliste', '/export', '/schluessel', '/einstellungen', '/vorgang/test-beleg']) {
      const response = await get(path);
      assert.equal(response.status, 200, path);
      const html = await response.text();
      assert.match(html, /Test Prüfer/);
      assert.doesNotMatch(html, /test-access-token|test-id-token/);
    }
  });
  await t.test('Kopfdaten, Buchungstext, XML und Ablehnungsgrund erscheinen im Prüftisch', async () => {
    const html = await (await get('/vorgang/test-beleg')).text();
    for (const text of ['R-42', 'Testlieferant', 'Prüfbare Position', 'Testauftrag', 'name="grund"', 'Auf dem Tisch', '&lt;Invoice&gt;']) assert.ok(html.includes(text), text);
    const result = await post('/vorgang/test-beleg/entscheiden', { entscheidung: 'ablehnen', grund: 'Beleg unvollständig' });
    assert.equal(result.status, 302);
    assert.equal(JSON.parse(calls.findLast((c) => c.path.endsWith('/ablehnung')).body).grund, 'Beleg unvollständig');
    const decided = await (await get('/vorgang/test-beleg')).text();
    assert.match(decided, /Beleg unvollständig/);
    assert.doesNotMatch(decided, /value="freigeben"/);
  });
  await t.test('Die Gedanken der KI liegen als ausfahrbare Lade am Vorgang', async () => {
    const html = await (await get('/vorgang/test-beleg')).text();
    for (const text of ['class="gedankenlade"', 'Gedanken der KI', 'Was die KI beim Lesen gedacht hat',
      'Ich sehe oben groß „Rechnung“', 'Personenkonto', 'Testlieferant führe ich unter 70001',
      'Woran die KI hängen blieb', 'Beim Zuschnitt bin ich mir nicht sicher', 'Auch erwogen',
      'groq/openai-gpt-oss-120b']) {
      assert.ok(html.includes(text), text);
    }
    // Ohne Skript bedienbar: die Lade ist ein natives <details> mit Umschalter.
    assert.match(html, /<details class="gedankenlade">\s*<summary/);
  });

  await t.test('Ohne Erläuterung bleibt der Prüftisch vollständig, nur ohne die Karte', async () => {
    const gedanken = vorgang.gedanken;
    vorgang.gedanken = null;
    const html = await (await get('/vorgang/test-beleg')).text();
    vorgang.gedanken = gedanken;
    assert.ok(!html.includes('gedankenlade'));
    assert.match(html, /Prüfbare Position/);
  });

  await t.test('Die Einreich-Ablage hängt auf jeder Seite im Kopf und nimmt Dateien entgegen', async () => {
    for (const pfad of ['/', '/pruefliste', '/export', '/schluessel']) {
      const html = await (await get(pfad)).text();
      assert.ok(html.includes('id="einreichlade"'), 'Lade auf ' + pfad);
      assert.ok(html.includes('data-lade="einreichlade"'), 'Auslöser auf ' + pfad);
      assert.match(html, /<dialog[^>]*class="einreichlade"/, 'natives dialog auf ' + pfad);
      // Ohne Skript bleibt der Weg über die Startseite erhalten.
      assert.ok(html.includes('href="/#datei"'), 'Rückfallweg auf ' + pfad);
    }
    // Der Rueckfallweg muss auch ankommen: die Startseite traegt den Anker.
    assert.ok((await (await get('/')).text()).includes('id="datei"'), 'Anker auf der Startseite');
    const lade = await (await get('/pruefliste')).text();
    assert.ok(lade.includes('action="/belege/einreichen"'), 'Formularziel');
    assert.ok(lade.includes('type="file"'), 'Dateifeld');
    assert.ok(lade.includes('multiple'), 'Mehrfachauswahl');
  });

  await t.test('Die Ablage bringt ihr Erfolgsbild mit: Kreis, ausgestanzter Haken, Meldung', async () => {
    const html = await (await get('/pruefliste')).text();
    assert.ok(html.includes('class="lade-fertig"'), 'Erfolgsflaeche');
    assert.match(html, /<svg[^>]*class="haken-kreis"/, 'Zeichnung');
    assert.ok(html.includes('class="kreis-fuellung"'), 'fuellender Kreis');
    assert.ok(html.includes('class="haken"'), 'Haken');
    assert.ok(html.includes('Eingereicht'), 'Meldung');
    // Der Haken traegt keine eigene Farbe: er wird aus der Flaechenfarbe ausgestanzt.
    assert.match(html, /class="lade-fertig"[^>]*hidden/, 'erst nach dem Einreichen sichtbar');
  });

  await t.test('Schlüsselansicht zeigt jeden Schlüssel maskiert samt Zustand', async () => {
    const html = await (await get('/schluessel')).text();
    for (const text of ['KI-Schlüssel', 'gsk_…a1b2', 'AIz…9876', 'Bereit', 'Gesperrt bis',
      'Schnelle Stufe', 'Starke Stufe', 'Alle Schlüssel live prüfen']) {
      assert.ok(html.includes(text), text);
    }
  });

  await t.test('Ein gemerktes Prüfergebnis überlebt den Seitenaufruf', async () => {
    const ohne = await (await get('/schluessel')).text();
    assert.ok(ohne.includes('Noch nicht geprüft'), 'ohne Prüfung');

    schluessel[0].probe = { ok: true, meldung: 'Erreichbar', dauerMs: 120, restAnfragen: 941,
      restToken: 17532, kontingentZurueckAb: null, geprueftAm: '2026-09-09T12:00:00Z' };
    const html = await (await get('/schluessel')).text();
    schluessel[0].probe = null;

    assert.ok(html.includes('941 Anfragen frei'), 'Restanfragen');
    assert.ok(html.includes('17.532 Token frei'), 'Resttoken');
    assert.ok(html.includes('Zuletzt geprüft um'), 'Zeitpunkt');
    assert.ok(html.includes('Erreichbar · 120 ms'), 'Zustand aus der Prüfung');
  });

  await t.test('Die Live-Prüfung läuft nur per POST und meldet Restkontingent zurück', async () => {
    assert.equal((await get('/schluessel/pruefen')).status, 404);
    const antwort = await get('/schluessel/pruefen', { method: 'POST', headers: { origin } });
    assert.equal(antwort.status, 200);
    const ergebnis = await antwort.json();
    assert.equal(ergebnis[0].probe.restAnfragen, 941);
    assert.equal(ergebnis[1].probe.ok, false);
    assert.ok(calls.some((c) => c.path === '/api/v1/einstellungen/ki/schluessel/pruefen' && c.method === 'POST'));
  });

  await t.test('Prüfliste öffnet Auswahl, Original und Prüfung zusammen; Filter behalten die Auswahl', async () => {
    const html = await (await get('/pruefliste')).text();
    assert.match(html, /Auf dem Tisch/);
    assert.match(html, /&lt;Invoice&gt;/);
    assert.match(html, /Prüfbare Position/);
    assert.match(html, /aria-label="Warteschlange filtern"/);
    assert.doesNotMatch(html, /class="tabelle"/);
    const filtered = await (await get('/pruefliste?zustand=abgelehnt&id=test-beleg')).text();
    assert.match(filtered, /Beleg unvollständig/);
    assert.match(filtered, /zustand=abgelehnt(?:&amp;|&)id=test-beleg/);
    const empty = await (await get('/pruefliste?zustand=offen')).text();
    assert.match(empty, /In diesem Zustand liegt gerade nichts/);
    assert.doesNotMatch(empty, /&lt;Invoice&gt;/);
  });
  await t.test('Statusfilter und PDF-Original bleiben erreichbar', async () => {
    assert.doesNotMatch(await (await get('/pruefliste?zustand=offen')).text(), /R-42/);
    assert.match(await (await get('/pruefliste?zustand=abgelehnt')).text(), /R-42/);
    assert.match(await (await get('/pruefliste?zustand=alle')).text(), /R-42/);
    vorgang.quelle.format = 'PDF';
    const html = await (await get('/vorgang/test-beleg')).text();
    assert.match(html, /type="application\/pdf"/);
    assert.match(html, /Original öffnen/);
    assert.equal((await get('/vorgang/test-beleg/beleg')).status, 200);
    vorgang.quelle.format = 'XRECHNUNG';
  });
  await t.test('Upload-Fehler werden sichtbar und Dateien durchgereicht', async () => {
    const empty = await post('/belege/einreichen', {});
    const html = await (await get(empty.headers.get('location'))).text();
    assert.match(html, /Es wurde keine Datei ausgewählt/);
    const body = new FormData();
    body.append('datei', new File(['<Invoice/>'], 'rechnung.xml', { type: 'application/xml' }));
    const result = await get('/belege/einreichen', { method: 'POST', headers: { origin }, body });
    assert.match(result.headers.get('location'), /^\/vorgang\/test-beleg/);
    assert.match(calls.findLast((c) => c.path === '/api/v1/belege').body, /rechnung.xml/);
  });
  await t.test('Freigabe und DATEV-Download verändern den angezeigten Status', async () => {
    vorgang.status = 'OFFEN';
    await post('/vorgang/test-beleg/entscheiden', { entscheidung: 'freigeben' });
    assert.match(await (await get('/export')).text(), /R-42/);
    const result = await post('/export/datev', {});
    assert.equal(result.headers.get('content-type'), 'application/zip');
    assert.equal(await result.text(), 'PK-test-package');
    assert.match(await (await get('/export')).text(), /Nichts zur Übergabe bereit/);
    assert.match(await (await get('/vorgang/test-beleg')).text(), /An DATEV übergeben/);
  });
  await t.test('Mehrfach-Upload verarbeitet alle Dateien und erklärt einzelne Ablehnungen', async () => {
    const body = new FormData();
    for (const name of ['erste.xml', 'defekt.xml', 'letzte.xml']) {
      body.append('datei', new File(['<Invoice/>'], name, { type: 'application/xml' }));
    }
    const before = calls.filter((c) => c.path === '/api/v1/belege').length;
    const response = await get('/belege/einreichen', { method: 'POST', headers: { origin }, body });
    assert.equal(calls.filter((c) => c.path === '/api/v1/belege').length - before, 3);
    const result = await (await get(response.headers.get('location'))).text();
    for (const text of ['erste.xml', 'defekt.xml', 'letzte.xml', 'Zeile 2, Spalte 5', 'End-Tag fehlt']) assert.ok(result.includes(text), text);
    assert.match(result, /2 angenommen/);
    assert.match(result, /1 abgelehnt/);
  });
  await t.test('Bereits exportierte Belege können gezielt erneut heruntergeladen werden', async () => {
    const html = await (await get('/export')).text();
    assert.match(html, /Erneut exportieren/);
    const result = await post('/export/datev', { erneut: 'on', vorgaenge: 'test-beleg' });
    assert.equal(result.headers.get('content-type'), 'application/zip');
    assert.deepEqual(JSON.parse(calls.findLast((c) => c.path.endsWith('/datev')).body), { vorgaenge: ['test-beleg'], erneut: true });
    const before = calls.length;
    const empty = await post('/export/datev', { erneut: 'on' });
    assert.match(empty.headers.get('location'), /meldung=/);
    assert.equal(calls.length, before);
  });
  await t.test('Firmen- und Mailformular speichern über die bestehenden API-Routen', async () => {
    await post('/einstellungen/firma', firma);
    assert.deepEqual(JSON.parse(calls.findLast((c) => c.path.endsWith('/firma') && c.method === 'PUT').body), firma);
    await post('/einstellungen/mail-eingang', { ...mail, aktiv: 'on', passwort: '', erlaubteAbsender: 'rechnung@example.test' });
    const saved = JSON.parse(calls.findLast((c) => c.path.endsWith('/mail-eingang') && c.method === 'PUT').body);
    assert.equal(saved.passwort, '');
    assert.deepEqual(saved.erlaubteAbsender, ['rechnung@example.test']);
    assert.equal(saved.port, 993);
    assert.ok(calls.every((c) => c.token === 'Bearer test-access-token'));
  });
  await t.test('Fremde Formularursprünge werden weiterhin abgewiesen', async () => {
    const result = await get('/einstellungen/firma', { method: 'POST', headers: { origin: 'https://other.example.test' }, body: new URLSearchParams(firma) });
    assert.equal(result.status, 403);
  });
});
