#!/usr/bin/env bash
# Laeuft auf dorn: leert die SQLite-Ablage von InvoiceMatcher bis auf die Einstellungen.
# Wird von reset-daten.sh per scp dorthin kopiert und ausgefuehrt - nicht direkt aufrufen.

set -euo pipefail
trap 'rm -f "$0"' EXIT

VOLUME="invoicematcher_invoicematcher-data"
CONTAINER="invoicematcher"
DB_PATH="/data/invoicematcher.db"
BACKUP_DIR="/home/murthag/invoicematcher"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
SKIP_CONFIRM="${1:-0}"

sql() {
    docker run --rm -v "${VOLUME}:/data" alpine:3.20 sh -c \
        "apk add --no-cache sqlite >/dev/null 2>&1 && sqlite3 ${DB_PATH} \"$1\""
}

CASES=$(sql "SELECT COUNT(*) FROM review_case;")
IMAGES=$(sql "SELECT COUNT(*) FROM document_image;")

echo "Server: $(hostname), Volume: $VOLUME"
echo "Loescht $CASES Pruefvorgaenge und $IMAGES Belegbilder."
echo "Bleiben erhalten: Firmen-, Mail-, Kontenrahmen-, Partner-, KI- und Review-Einstellungen"
echo "sowie die verschluesselten KI-Schluessel-Zustandsdateien."
echo "Die Anwendung wird waehrend der Loeschung kurz gestoppt (Downtime)."

if [[ "$SKIP_CONFIRM" -ne 1 ]]; then
    read -r -p "Fortfahren? Tippe 'ja' zum Bestaetigen: " antwort
    if [[ "$antwort" != "ja" ]]; then
        echo "Abgebrochen."
        exit 1
    fi
fi

BACKUP_FILE="${BACKUP_DIR}/invoicematcher-vor-reset-${TIMESTAMP}.tar.gz"
echo "Sichere Volume nach ${BACKUP_FILE} ..."
docker run --rm -v "${VOLUME}:/data" -v "${BACKUP_DIR}:/sicherung" alpine \
    tar czf "/sicherung/invoicematcher-vor-reset-${TIMESTAMP}.tar.gz" -C /data .

echo "Stoppe Anwendungscontainer..."
docker stop "$CONTAINER"

echo "Loesche Pruefvorgaenge und Belegbilder..."
sql "DELETE FROM document_image; DELETE FROM review_case; VACUUM;"

echo "Starte Anwendungscontainer wieder..."
docker start "$CONTAINER"

echo "Fertig. Sicherung liegt unter ${BACKUP_FILE}."
echo "Verbliebene Pruefvorgaenge: $(sql "SELECT COUNT(*) FROM review_case;")"
