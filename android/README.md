# NMRFRemote (Android)

Android-App zur Steuerung des NM-RF-HAT (Bruce-Firmware) plus eigenständige Lab-Tools.
Erstes Modul: **WLAN-Analyzer** (läuft ohne HAT/Firmware).

- Kotlin + Jetpack Compose (Material 3), kein GMS/Google-Dienst.
- `minSdk 26`, `targetSdk 34`, Kotlin 2.0 (Compose-Compiler-Plugin).
- Zielgerät: Huawei EMUI 12 (Android 12).

## Öffnen & Bauen

Der Gradle-Wrapper ist enthalten; Build + Unit-Tests sind verifiziert
(Gradle 8.9, AGP 8.7.0, Kotlin 2.0.21, Compose BOM 2024.09.03).

**Wichtig:** CLI-Builds mit **JDK 21** (JBR) — Java 25 ist für Gradle 8.9 zu neu.

1. **Android Studio** → *Open* → Ordner `android/` wählen, Gradle-Sync abwarten
   (Studio installiert das `android-34`-Platform bei Bedarf automatisch).
2. Gerät per USB (USB-Debugging an) → **Run 'app'**.

CLI:
```
cd android
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :app:testDebugUnitTest   # Unit-Tests (RadioMath + ViewModel)
./gradlew :app:assembleDebug       # Debug-APK -> app/build/outputs/apk/debug/
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
