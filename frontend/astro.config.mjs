// @ts-check
import { defineConfig } from 'astro/config';
import node from '@astrojs/node';

// Serverseitig gerendert, nicht statisch: die Sitzung und das Zugriffstoken duerfen den
// Browser nie erreichen, und die Pruefliste ist bei jedem Aufruf eine andere.
export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  server: { port: 4321, host: true },
  devToolbar: { enabled: false },
});
