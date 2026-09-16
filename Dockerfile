# syntax=docker/dockerfile:1

# --- Build -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Die eigenen Bausteine (ai-gateway, revolver) kommen aus der privaten Registry
# packages.tstieh.de. Die Zugangsdaten kommen als BuildKit-Secret herein - so landen sie in
# keiner Image-Schicht und in keinem Verlauf. Lokal:
#   docker build --secret id=maven_settings,src=$HOME/.m2/settings.xml .
# MAVEN_OPTS/TLSv1.2: sonst scheitert Maven dort im TLS-Handshake ("unrecognized_name").
ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2"

# Abhaengigkeiten zuerst aufloesen, damit der Layer bei reinen Codeaenderungen haelt.
COPY pom.xml ./
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    mvn -B -q package -DskipTests

# --- Laufzeit ----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl wird fuer den HEALTHCHECK gebraucht; das JRE-Image bringt keinen HTTP-Client mit.
# tesseract-ocr liest gescannte Belege ohne Textebene; ohne die deutschen Sprachdaten waere
# die Erkennung auf einem deutschen Beleg wertlos.
RUN apt-get update \
 && apt-get install --no-install-recommends -y curl tesseract-ocr tesseract-ocr-deu \
 && rm -rf /var/lib/apt/lists/*

# Kein Root im Container.
RUN groupadd --system invoicematcher \
 && useradd --system --gid invoicematcher --home /app invoicematcher
# Die Ablage liegt in einem eigenen Verzeichnis, damit sie als Volume ueberlebt.
RUN mkdir -p /data && chown invoicematcher:invoicematcher /data
USER invoicematcher

COPY --from=build --chown=invoicematcher:invoicematcher /build/target/InvoiceMatcher-*.jar /app/app.jar

VOLUME ["/data"]

EXPOSE 8080
# --enable-native-access: der SQLite-Treiber laedt eine native Bibliothek. Ohne diese Freigabe
# warnt das JDK und wird den Zugriff kuenftig blockieren.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED" \
    INVOICEMATCHER_DB="/data/invoicematcher.db"

# Prueft die Anwendung, nicht nur den Prozess: erst ein UP aus dem Health-Endpunkt zaehlt.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
