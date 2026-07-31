# NM-RF-HAT App — Android-Pivot & Projekt-Überblick

**Datum:** 2026-07-31
**Status:** Richtungsentscheidung — ersetzt die App-Hälfte der iOS-Foundation-Spec

## Pivot

Von iOS auf **Android** gewechselt. Zielgerät: **Huawei mit EMUI 12 = Android 12** (API 31),
ohne Google-Dienste (GMS). Grund: kein bezahlter Apple-Developer-Account und das Apple-Limit
für frei signierte Apps erreicht.

- **Firmware-Plan bleibt 1:1 gültig** — die BLE-UART-Bridge (`docs/.../2026-07-31-nmrf-hat-ble-remote-foundation.md`, Tasks 1–5) ist plattform-unabhängig.
- **App-Hälfte** (iOS-Spec Tasks 6–11, SwiftUI) wird durch **Kotlin + Jetpack Compose** ersetzt.
  Die Architektur überträgt sich direkt: `BruceLink`-Interface, `LineAssembler`, `Chunker`,
  Terminal-, Nav-Pad-, Verbindungs-Screen.

## Toolchain (alles gratis)

- **Android Studio** — IDE, bringt Android SDK, Gradle, Emulator, Geräte-Deployment. Sprache **Kotlin**, UI **Jetpack Compose**.
- **USB-Debugging** am Huawei (Über das Telefon → 7× Build-Nummer → Entwickleroptionen → USB-Debugging). Self-Install ohne Account, **kein App-Limit**.
- **PlatformIO/VS Code** (oder Arduino IDE) zum Flashen des Bruce-Forks — unverändert.
- App nutzt nur **AOSP-APIs** (`android.bluetooth`, `WifiManager`) → **kein GMS nötig**.
- Projekt-Vorgaben: `minSdk 26`, `targetSdk 34`, Kotlin, Compose, kein Google-Play-Dienst.

## Plattform-Grenzen (Android)

- **WLAN-Scan geht** (`WifiManager.scanResults`): SSID, BSSID, **Frequenz (2.4 + 5 GHz)**, **RSSI**,
  Kanalbreite, Capabilities → echter Netz-/Kanal-Analyzer machbar.
  - Braucht Runtime-Permission **`ACCESS_FINE_LOCATION`** + Standort eingeschaltet (Android koppelt WLAN-Scan an Location).
  - Android drosselt Vordergrund-Scans (~4 / 2 min); Analyzer pollt entsprechend + zeigt „letzter Scan vor X s".
- **BLE-Scan/Connect** via `android.bluetooth` (BluetoothLeScanner) — offener als iOS; braucht
  `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (Android 12) Runtime-Permissions.
- **Nicht möglich** auf stock/ungerootet: echtes RF-Spektrum/Wasserfall, WLAN-Deauth/Monitor-Sniffing,
  raw 802.11. Das braucht Root+passenden Chipsatz/externen Adapter oder einen SDR — bzw. läuft über den HAT.

## Revidierte Dekomposition

| Teil | Sub-Projekt | Braucht | Plattform |
|------|-------------|---------|-----------|
| A | **Firmware BLE-UART-Bridge** (NUS → `parseSerialCommand`) | — | ESP32/Bruce (unverändert) |
| B | **Android App-Core / BLE-Remote**: BLE, `BruceLink`, CLI-Layer, Terminal, Nav-Pad | A | Kotlin/Compose |
| C | **Bruce-Remote-Feature-Screens**: SubGHz, IR, NFC, System/GPIO/LED, WiFi-Settings … | B | Kotlin/Compose |
| D | **Standalone Lab-Tools**: **WLAN-Analyzer (2.4/5 GHz)**, BLE-Scanner, Audio-Spektrum/Wasserfall/Spektrogramm | — | Kotlin/Compose |
| E | **Sub-GHz-RSSI-Wasserfall**: Firmware-Sweep-Stream + App-Renderer | A, B | Bruce + Kotlin |

## Empfohlene Build-Reihenfolge (Leon: „alle")

1. **WLAN-Analyzer** (Teil D, standalone) — kein HAT, keine Firmware, sofort sichtbar; nutzt den Android-Vorteil. Eigenes Spec + Plan.
2. **Firmware BLE-Bridge** (Teil A) — bestehender Plan, Tasks 1–5.
3. **Android App-Core / BLE-Remote** (Teil B) — Kotlin-Portierung der iOS-Tasks 6–11.
4. **Feature-Screens** (Teil C) und **restliche Lab-Tools + Sub-GHz-Wasserfall** (D-Rest, E).

## Nächste Dokumente

- `specs/2026-07-31-android-wifi-analyzer-design.md` (+ zugehöriger Plan) — als Nächstes.
- Firmware-Plan unverändert weiterverwenden; iOS-App-Tasks 6–11 werden beim Erreichen von Teil B als Kotlin-Plan neu geschrieben.
