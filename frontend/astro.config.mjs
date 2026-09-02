// @ts-check
import { defineConfig, sessionDrivers } from 'astro/config';
import node from '@astrojs/node';

// Serverseitig gerendert, nicht statisch: die Sitzung und das Zugriffstoken duerfen den
// Browser nie erreichen, und die Pruefliste ist bei jedem Aufruf eine andere.
export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  server: { port: 4321, host: true },
  devToolbar: { enabled: false },
  // Der Node-Adapter leitet den Standard-Speicherpfad sonst vom Arbeitsverzeichnis beim
  // 'astro build' ab (Dockerfile-Bau-Stufe: /build) - im Laufzeit-Image (/app) existiert
  // dieser Pfad nicht und jedes session.set() schlaegt mit ENOENT fehl, weil der Pfad zur
  // Bauzeit fest in das Server-Bundle einkompiliert wird. SESSION_DIR wird deshalb waehrend
  // 'npm run build' im Dockerfile gesetzt (siehe dort); ausserhalb von Docker (lokal
  // 'astro dev') greift der projektrelative Vorgabewert.
  session: {
    driver: sessionDrivers.fsLite({ base: process.env.SESSION_DIR ?? '.astro-sessions' }),
  },
});
