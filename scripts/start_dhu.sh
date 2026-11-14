#!/usr/bin/env bash
set -euo pipefail

ADB="${HOME}/Library/Android/sdk/platform-tools/adb"
DHU="${HOME}/Library/Android/sdk/extras/google/auto/desktop-head-unit"

if [[ ! -x "$ADB" ]]; then
  echo "❌ adb nicht gefunden unter: $ADB"
  exit 1
fi

if [[ ! -x "$DHU" ]]; then
  echo "❌ Desktop Head Unit nicht gefunden unter: $DHU"
  exit 1
fi

echo "🔌 Prüfe verbundene Geräte..."
"$ADB" devices

echo "🔁 Richte Port-Forwarding tcp:5277 ein..."
"$ADB" forward tcp:5277 tcp:5277

echo "🚗 Starte Android Auto Desktop Head Unit..."
echo "   (Tipp: Auf dem Handy muss in den Android Auto Entwicklereinstellungen 'Head-Unit-Server starten' aktiv sein)"
exec "$DHU" "$@"
