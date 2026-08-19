# syntax=docker/dockerfile:1

# --- Build -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Abhaengigkeiten zuerst aufloesen, damit der Layer bei reinen Codeaenderungen haelt.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Laufzeit ----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl wird fuer den HEALTHCHECK gebraucht; das JRE-Image bringt keinen HTTP-Client mit.
RUN apt-get update \
 && apt-get install --no-install-recommends -y curl \
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
