# NMRFRemote (Android)

Android-App zur Steuerung des NM-RF-HAT (Bruce-Firmware) plus eigenständige Lab-Tools.
Erstes Modul: **WLAN-Analyzer** (läuft ohne HAT/Firmware).

- Kotlin + Jetpack Compose (Material 3), kein GMS/Google-Dienst.
- `minSdk 26`, `targetSdk 34`, Kotlin 2.0 (Compose-Compiler-Plugin).
- Zielgerät: Huawei EMUI 12 (Android 12).

## Öffnen & Bauen

1. **Android Studio** → *Open* → Ordner `android/` wählen.
2. Gradle-Sync abwarten (lädt AGP 8.7 / Kotlin 2.0.21 / Compose BOM). Android Studio erzeugt
   dabei den Gradle-Wrapper (`gradlew`/`gradle-wrapper.jar`) automatisch — die Jar liegt
   absichtlich **nicht** im Repo.
3. Gerät per USB (USB-Debugging an) → **Run 'app'**.

CLI-Alternative (nachdem der Wrapper existiert):
```
cd android
./gradlew :app:testDebugUnitTest   # Unit-Tests (RadioMath + ViewModel)
./gradlew :app:assembleDebug       # APK bauen
```

## Berechtigungen

- `ACCESS_FINE_LOCATION` (Runtime) + **Standort eingeschaltet** — Android koppelt WLAN-Scans daran.
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` (Manifest).
- Android drosselt Vordergrund-Scans (~4/2 min); der Rescan-Button triggert `WifiManager.startScan()`,
  zwischendurch werden die letzten Ergebnisse angezeigt.

## Struktur

```
app/src/main/java/com/nmrf/remote/
  MainActivity.kt            Permission-Flow + Screen einhängen
  ui/theme/                  Dark-Neon Material-3-Theme
  wifi/
    RadioMath.kt             pure Frequenz -> Band/Kanal (getestet)
    AccessPoint.kt           Datentyp + ScanResult-Mapper
    WifiScanner.kt           WifiManager-Wrapper (WifiSource-Interface)
    WifiAnalyzerViewModel.kt Sortierung/Band-Filter (getestet)
    WifiAnalyzerScreen.kt    Compose-UI: Band-Umschalter, Kanal-Graph, Netz-Liste
app/src/test/.../wifi/       RadioMathTest, WifiAnalyzerViewModelTest
```

Design & Plan: `../docs/superpowers/specs/2026-07-31-android-wifi-analyzer-design.md`,
`../docs/superpowers/plans/2026-07-31-android-wifi-analyzer.md`.

## Nicht enthalten (Plattform-Grenze)

Echtes RF-Spektrum/Wasserfall, WLAN-Deauth/Monitor/Sniffing, Paket-Capture gehen auf
stock/ungerootet nicht — das läuft über den HAT (spätere Module) oder braucht Root+Adapter/SDR.
