#!/usr/bin/env bash
# Klont Bruce 1.16 (VOLLER Funktionsumfang) und legt die BLE-Remote-Bridge als
# rein ADDITIVES Overlay dazu. Es wird nichts an Bruce gelöscht -> keine Funktion geht verloren.
set -euo pipefail
DEST="${1:-Bruce-NMRF}"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo ">> Klone Bruce @1.16 nach $DEST"
git clone --depth 1 --branch 1.16 https://github.com/pr3y/Bruce "$DEST"

echo ">> Kopiere BLE-Remote-Overlay nach src/modules/ble/"
mkdir -p "$DEST/src/modules/ble"
cp "$HERE/ble_remote.cpp" "$HERE/ble_remote.h" "$DEST/src/modules/ble/"

cat <<'NOTE'

Overlay eingelegt. Jetzt die 3 ADDITIVEN Patches aus INTEGRATION.md anwenden:
  1) config.h / config.cpp : Feld bleRemote + Setter (wifiAtStartup duplizieren+umbenennen)
  2) settings.cpp          : Menüpunkt "BLE Remote" (Toggle)
  3) main.cpp              : Autostart nach startSerialCommandsHandlerTask(true)

Dann bauen/flashen (ESP32-2432S028 "CYD"):
  cd DEST && pio run -e CYD-2432S028 -t upload      # oder CYD-2USB
NOTE
