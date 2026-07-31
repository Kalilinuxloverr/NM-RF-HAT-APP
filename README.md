# NM-RF-HAT App

Steuerung eines **NM-RF-HAT** (RF-Board auf ESP32-2432S028 „CYD", Bruce-Firmware) per
**BLE** vom Handy — plus eigenständige Lab-Tools. Privater Laborgebrauch.

## Repo-Struktur

| Pfad | Inhalt |
|------|--------|
| `android/` | **Android-App `NMRFRemote`** (Kotlin/Compose). Erstes Modul: WLAN-Analyzer. |
| `docs/superpowers/specs/` | Design-Specs (Fundament BLE-Remote, Android-Pivot, WLAN-Analyzer). |
| `docs/superpowers/plans/` | Implementierungspläne (BLE-Fundament, WLAN-Analyzer). |

Firmware-Fork (Bruce BLE-UART-Bridge, NUS → serielle CLI) kommt als eigener Teil dazu
(Plan: `docs/superpowers/plans/2026-07-31-nmrf-hat-ble-remote-foundation.md`).

## Sub-Projekte (Build-Reihenfolge)

1. **WLAN-Analyzer** (Android, standalone) — *in Arbeit*, siehe `android/`.
2. Firmware BLE-UART-Bridge (Bruce-Fork).
3. Android BLE-Remote-Core (`BruceLink`, Terminal, Nav-Pad).
4. Feature-Screens (SubGHz/IR/NFC/System) + Sub-GHz-RSSI-Wasserfall.

## Plattform-Grenzen

Ein Stock-Handy kann kein echtes RF-Spektrum und kein WLAN-Deauth/Monitor-Sniffing
(kein SDR, kein Monitor-Mode ohne Root+Adapter). Solche „harten" Funktionen laufen auf
dem HAT; die App ist deren Fernsteuerung. WLAN-**Scan** (2.4/5 GHz, RSSI, Kanal) geht auf
Android nativ — Grundlage des WLAN-Analyzers.

Zielgerät: Huawei EMUI 12 (Android 12), ohne Google-Dienste.
