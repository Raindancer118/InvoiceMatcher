#!/usr/bin/env bash
# Loescht auf dorn (Produktivserver, https://belege.tstieh.de) alle Pruefvorgaenge samt
# Belegbildern aus der SQLite-Ablage von InvoiceMatcher - per SSH, ohne dass hier lokal ein
# Datenbank-Client noetig ist. Einstellungen (company_settings, mail_intake_settings,
# accounting_settings, partner_settings, ai_settings, review_settings) und die verschluesselten
# KI-Schluessel-Zustandsdateien bleiben unberuehrt.
#
# Ablauf auf dorn (siehe reset-daten-remote.sh): Sicherung des Volumes als tar.gz nach
# /home/murthag/invoicematcher/ -> Anwendungscontainer kurz stoppen -> Loeschung per
# Wegwerf-Container mit sqlite3 auf dem Volume -> Anwendungscontainer wieder starten.
#
# Nutzung: ./reset-daten.sh [--yes]

set -euo pipefail

HOST="dorn"
REMOTE_SCRIPT_LOCAL="$(dirname "$0")/reset-daten-remote.sh"
REMOTE_SCRIPT_REMOTE="/tmp/invoicematcher-reset-remote.sh"

SKIP_CONFIRM=0
for arg in "$@"; do
    case "$arg" in
        --yes|-y) SKIP_CONFIRM=1 ;;
    esac
done

if [[ ! -f "$REMOTE_SCRIPT_LOCAL" ]]; then
    echo "reset-daten-remote.sh fehlt neben diesem Script." >&2
    exit 1
fi

echo "Kopiere Reset-Script nach $HOST..."
scp -q "$REMOTE_SCRIPT_LOCAL" "$HOST:$REMOTE_SCRIPT_REMOTE"

ssh -t "$HOST" bash "$REMOTE_SCRIPT_REMOTE" "$SKIP_CONFIRM"
