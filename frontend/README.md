# InvoiceMatcher — Prüfoberfläche

Astro mit serverseitigem Rendern. Die Anmeldung läuft über Authentik (`portal.tstieh.de`), das
Zugriffstoken bleibt auf dem Server und erreicht den Browser nie.

## Aufbau

```
src/
  lib/config.ts        Umgebung; fehlt etwas Sicherheitsrelevantes, bricht der Start ab
  lib/session.ts       Sitzung als verschlüsseltes Cookie (JWE, A256GCM)
  lib/oidc.ts          Authorization Code Flow mit PKCE gegen Authentik
  lib/api.ts           Zugriff auf /api/v1, ausschließlich serverseitig
  lib/darstellung.ts   Beträge, Daten, Zustände
  middleware.ts        Wächter: ohne Sitzung zur Anmeldung, ohne Gruppe auf /kein-zugriff
  layouts/             Grundgerüst mit Kopfleiste
  components/          Vorgangstabelle, Ablagefläche
  pages/               Übersicht, Prüfliste, Vorgang, Export, Anmeldewege
  styles/tokens.css    aus dem freigegebenen Prototyp; Änderungen zuerst in ../Design.md
```

## Warum serverseitig

Ein statisch ausgeliefertes Frontend müsste das Zugriffstoken im Browser halten. Damit läge es
in Reichweite jedes Skripts auf der Seite, und Belegbilder sind aufbewahrungspflichtige
Geschäftsdaten. Also hält der Server die Sitzung, und der Browser bekommt nur fertiges HTML.

Das Belegbild läuft aus demselben Grund über `/vorgang/{id}/beleg` durch den Frontend-Server,
statt dass der Browser die API direkt anspräche.

## Entwicklung

```bash
cp .env.example .env      # Werte aus dem Authentik-Provider eintragen
npm install
npm run dev               # http://localhost:4321
```

Die API muss dabei erreichbar sein (`INVOICEMATCHER_API`, Vorgabe `http://localhost:8080`).

`npm run build` baut, `npx astro check` prüft Typen und Astro-Diagnose. Beides läuft in der CI.

## Anmeldung einrichten

Im Authentik-Provider der Anwendung müssen eingetragen sein:

| Eintrag | Typ | Wert |
|---|---|---|
| Redirect-URI | `Authorization` | `…/auth/callback` |
| Redirect-URI | `Logout` | die Adresse aus `OIDC_POST_LOGOUT_REDIRECT_URI` |

Scopes: `openid profile email groups`. Ohne `groups` kommt niemand durch — die Middleware und
die API verlangen beide die Gruppe aus `REQUIRED_GROUP`.
