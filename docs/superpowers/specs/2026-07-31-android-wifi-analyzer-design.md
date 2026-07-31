# WLAN-Analyzer (Android) — Design-Spec

**Datum:** 2026-07-31
**Status:** Design, bereit für Implementierungsplan
**Teil:** D (Standalone Lab-Tools) — **erstes Modul der App `NMRFRemote` (Android)**
**Ziel:** Huawei EMUI 12 = Android 12, ohne GMS. Kotlin + Jetpack Compose. `minSdk 26`, `targetSdk 34`.

## Zweck & Scope

Ein „fescher" WLAN-Analyzer, der **ohne HAT und ohne Firmware** auf dem Huawei läuft:
sichtbare 2.4- und 5-GHz-Netze mit Kanal, Band, Signalstärke und einer Kanal-Belegungsgrafik.
Erstes lauffähiges Modul der späteren Gesamt-App (später kommen BLE-Remote, Terminal, Feature-Screens dazu).

**In Scope:** Netz-Liste (SSID/BSSID/Band/Kanal/RSSI), Kanal-Graph (2.4 & 5 GHz), Band-Umschalter,
Auto-Rescan mit Throttle-Anzeige, Runtime-Permission-Flow.
**Out of Scope (Deckel):** echtes RF-Spektrum/Wasserfall, Deauth/Monitor/Sniffing, Paket-Capture —
auf stock/ungerootet unmöglich (Root+Adapter/SDR nötig) bzw. läuft über den HAT.

## Architektur

```
WifiAnalyzerScreen (Compose)  ── beobachtet ──▶  WifiAnalyzerViewModel
                                                     │ nutzt ↓
                                                 WifiScanner  ──▶  Android WifiManager
                                                     │
                                          RadioMath (pure: freq→Kanal/Band)
```

- **WifiScanner** (kapselt `WifiManager`): startet Scans, hört auf `SCAN_RESULTS_AVAILABLE_ACTION`,
  liefert `Flow<List<AccessPoint>>`. Kennt Permission-Status und Throttle.
- **RadioMath** (pure, testbar): `frequencyToChannel(mhz): Int`, `bandOf(mhz): Band`.
- **WifiAnalyzerViewModel**: hält `accessPoints`, `selectedBand`, `lastScanElapsedMs`, `permissionState`; löst Rescan aus.
- **WifiAnalyzerScreen**: Compose-UI, hängt nur am ViewModel.

## Datentypen

```kotlin
enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

data class AccessPoint(
    val ssid: String,        // leer = hidden
    val bssid: String,
    val freqMhz: Int,
    val rssi: Int,           // dBm, negativ
    val channel: Int,
    val band: Band,
    val widthMhz: Int        // 20/40/80/160
)
```

## RadioMath (Kernlogik, TDD)

- 2.4 GHz: Kanal 1–13 = `(mhz - 2407) / 5`; Sonderfall Kanal 14 = `2484`.
- 5 GHz: Kanal = `(mhz - 5000) / 5`.
- 6 GHz (falls Gerät liefert): Kanal = `(mhz - 5950) / 5`.
- Band: 2400–2499→2.4; 4900–5899→5; 5925–7125→6; sonst UNKNOWN.

## UI (Compose, „fesch")

- **Band-Umschalter** (SegmentedButton: 2.4 / 5 GHz).
- **Kanal-Graph** (Canvas): X = Kanäle des Bandes, jeder AP als Bogen/Dreieck an seinem Kanal,
  Höhe ∝ RSSI (−90…−30 dBm gemappt), Farbe pro Band; überlappende Kanäle sichtbar.
- **Netz-Liste**: nach RSSI sortiert; pro Zeile SSID (oder „<hidden>"), Kanal · Band · Breite,
  RSSI-Wert + Signal-Balken.
- **Kopfzeile**: „Letzter Scan vor X s" + Rescan-Button (deaktiviert während Throttle-Fenster).

## Permissions & EMUI-12-Realität

- Runtime: **`ACCESS_FINE_LOCATION`** (+ Standortdienste an) — Pflicht für Scan-Ergebnisse.
- Manifest: `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION`.
- Throttling: Vordergrund-Scans ~4/2 min → ViewModel respektiert das, zeigt Restzeit; nutzt zwischenzeitlich `wifiManager.scanResults` (letzte Ergebnisse).
- Kein GMS: nur AOSP-`WifiManager`. Kein FusedLocationProvider nötig (Standort muss nur *an* sein, wird nicht abgefragt).

## Testing

- **RadioMath-Unit-Tests** (JUnit): bekannte Frequenzen → Kanal/Band (z.B. 2412→ch1/2.4, 2437→ch6, 5180→ch36/5, 5745→ch149/5, 2484→ch14).
- **ViewModel-Test**: gefälschter `WifiScanner` liefert AP-Liste → ViewModel sortiert nach RSSI, filtert nach Band korrekt.
- UI: Compose-Preview mit Fake-Daten.

## Datei-Struktur (im Android-Projekt `app/`)

- `wifi/RadioMath.kt` (pure) + Test `RadioMathTest.kt`
- `wifi/AccessPoint.kt` (Datentypen)
- `wifi/WifiScanner.kt` (WifiManager-Wrapper)
- `wifi/WifiAnalyzerViewModel.kt` + Test `WifiAnalyzerViewModelTest.kt`
- `wifi/WifiAnalyzerScreen.kt` (Compose)
- `MainActivity.kt` / Manifest — Permissions + Screen einhängen.
