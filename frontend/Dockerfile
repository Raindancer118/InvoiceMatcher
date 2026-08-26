# syntax=docker/dockerfile:1

# --- Bau -----------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

# Abhaengigkeiten zuerst, damit der Layer bei reinen Codeaenderungen haelt.
COPY package.json package-lock.json ./
RUN npm ci

COPY astro.config.mjs tsconfig.json ./
COPY src ./src
RUN npm run build

# Nur noch, was zur Laufzeit gebraucht wird.
RUN npm ci --omit=dev

# --- Laufzeit ------------------------------------------------------------------
FROM node:22-alpine AS runtime
WORKDIR /app

# Kein Root im Container; node:alpine bringt den Benutzer 'node' bereits mit.
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=4321

COPY --from=build --chown=node:node /build/node_modules ./node_modules
COPY --from=build --chown=node:node /build/dist ./dist
COPY --from=build --chown=node:node /build/package.json ./package.json

USER node
EXPOSE 4321

# Prueft die Anwendung, nicht nur den Prozess: die Anmeldeweiche muss antworten.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:4321/kein-zugriff').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "./dist/server/entry.mjs"]
