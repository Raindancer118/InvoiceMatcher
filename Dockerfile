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

# Kein Root im Container.
RUN groupadd --system invoicematcher \
 && useradd --system --gid invoicematcher --home /app invoicematcher
USER invoicematcher

COPY --from=build --chown=invoicematcher:invoicematcher /build/target/InvoiceMatcher-*.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["java", "-version"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
