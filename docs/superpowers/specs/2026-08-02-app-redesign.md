# NMRFRemote App-Redesign — Design-Spec

**Datum:** 2026-08-02 · **Status:** freigegeben ("alle machen")

## Struktur
Kachel-**Launcher (Home)** statt Bottom-Nav: WLAN · BLE · AUDIO · HAT · TOOLS · SETTINGS.
Tippen → Vollbild-Tool; System-Back → Home. Matrix-Theme bleibt, sauber & lesbar.

## HAT-Standardansicht
Mirror **groß oben** (antippen = Touch) · unten **Fadenkreuz ▲▼◀▶ + OK + ESC** ·
Befehle/Terminal/Spektrum über Unterleiste. Mirror-Transport: **CYD-eigenes AP + BLE gemischt**.

## Mirror-Transport (Phase 2)
CYD spannt festes AP auf: **SSID `NMRF-HAT`, Pass `nmrflab1`** + WebUI-Binlog. App: One-Tap-Beitritt
(WifiNetworkSpecifier) → Mirror/Commands über WLAN (flüssig); ohne WLAN BLE-Fallback (Auto).

## Settings
Changelog · Transport (Auto/WLAN/BLE) · Auto-Reconnect · CYD-AP-Zugang · Über/GitHub.

## Tools (Phase 3)
BLE-Beacon-Decoder · WLAN-Passiv-Detektor · Scans speichern/export · Offline-OUI/Company-DB ·
BLE-GATT-Konsole (lesen/schreiben/notify).

## Phasen
1. Launcher + Settings/Changelog + HAT-Relayout (App-only).
2. WLAN-Mirror (CYD-AP + WebUI-Binlog + Transport-Auto; Firmware+App).
3. Tools.
