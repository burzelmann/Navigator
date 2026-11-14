#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if [[ ! -f "play-service-account.json" ]]; then
  echo "❌ Fehler: play-service-account.json fehlt im Projekt-Root ($ROOT_DIR)."
  echo "   Bitte den Google Cloud Service-Account-Schlüssel dort ablegen."
  exit 1
fi

if [[ ! -f "keystore.properties" ]]; then
  echo "❌ Fehler: keystore.properties fehlt im Projekt-Root ($ROOT_DIR)."
  exit 1
fi

GRADLE_FILE="app/build.gradle.kts"
NOTES_FILE="app/src/main/play/release-notes/de-DE/internal.txt"

if [[ $# -lt 1 || -z "${1// }" ]]; then
  echo "❌ Fehler: Release-Notes sind Pflicht!"
  echo "   Verwendung: ./scripts/publish_internal.sh \"Neu in dieser Version: ...\""
  exit 1
fi

NOTES="$1"
NOTES_LEN=${#NOTES}
if (( NOTES_LEN < 20 )); then
  echo "❌ Fehler: Release-Notes müssen aussagekräftig sein (mind. 20 Zeichen, aktuell: $NOTES_LEN)."
  exit 1
fi
if (( NOTES_LEN > 500 )); then
  echo "❌ Fehler: Release-Notes überschreiten das Google-Play-Limit von 500 Zeichen (aktuell: $NOTES_LEN)."
  exit 1
fi

printf '%s\n' "$NOTES" > "$NOTES_FILE"
echo "📝 Release-Notes aktualisiert ($NOTES_LEN Zeichen): $NOTES_FILE"

CURRENT_CODE=$(grep -E '^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*[0-9]+' "$GRADLE_FILE" | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')
if [[ -z "$CURRENT_CODE" ]]; then
  echo "❌ Konnte versionCode in $GRADLE_FILE nicht lesen."
  exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))
sed -i '' -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1${NEW_CODE}/" "$GRADLE_FILE"
echo "🔢 versionCode erhöht: $CURRENT_CODE -> $NEW_CODE"

echo "🧪 Führe Unit-Tests aus..."
./gradlew test

echo "🚀 Baue & veröffentliche Bundle in Google Play (Track: internal)..."
./gradlew publishReleaseBundle

echo "✅ Release (versionCode=$NEW_CODE) erfolgreich in den Internen Test-Track hochgeladen!"
