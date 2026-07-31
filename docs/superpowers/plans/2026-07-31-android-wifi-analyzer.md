# WLAN-Analyzer (Android) — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Erstes lauffähiges Modul der Android-App `NMRFRemote`: ein WLAN-Analyzer, der ohne HAT/Firmware 2.4- und 5-GHz-Netze mit Kanal, Band, RSSI und Kanal-Graph anzeigt.

**Architecture:** `WifiAnalyzerScreen` (Compose) beobachtet `WifiAnalyzerViewModel`; das ViewModel zieht Scans von `WifiScanner` (Wrapper um `WifiManager`, liefert `Flow<List<AccessPoint>>`) und nutzt die pure Funktion `RadioMath` für Frequenz→Kanal/Band. Nur AOSP-APIs, kein GMS.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), AndroidX Lifecycle/ViewModel, Coroutines/Flow, JUnit4. Gradle Kotlin-DSL + Version-Catalog.

## Global Constraints

- `minSdk 26`, `targetSdk 34`, `compileSdk 34`.
- Kotlin 2.0 mit Compose-Compiler-Plugin (`org.jetbrains.kotlin.plugin.compose`).
- Kein Google-Play-Dienst, kein FusedLocation, keine externe Netzwerk-Lib. Nur `android.net.wifi.WifiManager`.
- Package: `com.nmrf.remote`. Android-Projektwurzel: `android/`.
- Runtime-Permission `ACCESS_FINE_LOCATION` (+ Standort an) ist Pflicht für Scan-Ergebnisse. Manifest zusätzlich: `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`.
- Vordergrund-Scan-Throttle (~4/2 min) respektieren: bei Ablehnung `wifiManager.scanResults` (letzte Ergebnisse) zeigen, Restzeit anzeigen.
- Zielgerät Huawei EMUI 12 (Android 12) ohne GMS.

---

### Task 1: Android-Projektgerüst (Gradle, Manifest, Theme, leere MainActivity)

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`
- Create: `android/gradle/libs.versions.toml`, `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`, `.../values/themes.xml`
- Create: `android/app/src/main/java/com/nmrf/remote/MainActivity.kt`
- Create: `android/app/src/main/java/com/nmrf/remote/ui/theme/{Color.kt,Type.kt,Theme.kt}`
- Create: `android/.gitignore`, `android/README.md`

**Interfaces:**
- Produces: baubares Compose-Projekt; `MainActivity` rendert vorerst einen Platzhalter. `NmrfTheme { }` Composable für spätere Screens.

- [ ] **Step 1:** Gradle-Dateien + Version-Catalog anlegen (AGP 8.7, Kotlin 2.0.21, Compose BOM, Gradle 8.9 wrapper-props).
- [ ] **Step 2:** Manifest mit den drei Permissions + `MainActivity` (exported, LAUNCHER).
- [ ] **Step 3:** Theme (`Color/Type/Theme.kt`) — dunkles Material-3-Schema, ohne dynamic color (EMUI-sicher).
- [ ] **Step 4:** `MainActivity` mit `setContent { NmrfTheme { Text("NMRF Remote") } }`.
- [ ] **Step 5:** In Android Studio öffnen → Gradle-Sync + Run (manuell durch Leon). Erwartet: App startet, zeigt Platzhalter.
- [ ] **Step 6:** Commit + push.

### Task 2: RadioMath (pure Kernlogik, TDD)

**Files:**
- Create: `android/app/src/main/java/com/nmrf/remote/wifi/RadioMath.kt`
- Test: `android/app/src/test/java/com/nmrf/remote/wifi/RadioMathTest.kt`

**Interfaces:**
- Produces: `enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }`; `object RadioMath { fun bandOf(mhz: Int): Band; fun channelOf(mhz: Int): Int }`. `channelOf` gibt `0` für unbekannt/UNKNOWN.

- [ ] **Step 1: Failing test** — bekannte Frequenzen:

```kotlin
class RadioMathTest {
    @Test fun band_classification() {
        assertEquals(Band.GHZ_2_4, RadioMath.bandOf(2412))
        assertEquals(Band.GHZ_2_4, RadioMath.bandOf(2484))
        assertEquals(Band.GHZ_5,   RadioMath.bandOf(5180))
        assertEquals(Band.GHZ_6,   RadioMath.bandOf(5955))
        assertEquals(Band.UNKNOWN, RadioMath.bandOf(1000))
    }
    @Test fun channel_mapping() {
        assertEquals(1,   RadioMath.channelOf(2412))
        assertEquals(6,   RadioMath.channelOf(2437))
        assertEquals(13,  RadioMath.channelOf(2472))
        assertEquals(14,  RadioMath.channelOf(2484))   // Sonderfall
        assertEquals(36,  RadioMath.channelOf(5180))
        assertEquals(149, RadioMath.channelOf(5745))
        assertEquals(1,   RadioMath.channelOf(5955))   // 6 GHz ch1
        assertEquals(0,   RadioMath.channelOf(1000))   // unbekannt
    }
}
```

- [ ] **Step 2:** Test laufen lassen → FAIL (RadioMath fehlt). `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 3: Implementation:**

```kotlin
package com.nmrf.remote.wifi

enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

object RadioMath {
    fun bandOf(mhz: Int): Band = when (mhz) {
        in 2400..2499 -> Band.GHZ_2_4
        in 4900..5899 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.UNKNOWN
    }

    fun channelOf(mhz: Int): Int = when (bandOf(mhz)) {
        Band.GHZ_2_4 -> if (mhz == 2484) 14 else (mhz - 2407) / 5
        Band.GHZ_5   -> (mhz - 5000) / 5
        Band.GHZ_6   -> (mhz - 5950) / 5
        Band.UNKNOWN -> 0
    }
}
```

- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5:** Commit + push.

### Task 3: AccessPoint-Datentyp

**Files:**
- Create: `android/app/src/main/java/com/nmrf/remote/wifi/AccessPoint.kt`

**Interfaces:**
- Produces: `data class AccessPoint(ssid, bssid, freqMhz, rssi, channel, band, widthMhz)` + Factory `fun fromScan(...)`.

- [ ] **Step 1:** Datentyp + Mapper aus `android.net.wifi.ScanResult`:

```kotlin
package com.nmrf.remote.wifi

import android.net.wifi.ScanResult
import android.os.Build

data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val freqMhz: Int,
    val rssi: Int,
    val channel: Int,
    val band: Band,
    val widthMhz: Int,
) {
    companion object {
        fun fromScan(r: ScanResult): AccessPoint {
            val name = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.trim('"') ?: "" else @Suppress("DEPRECATION") (r.SSID ?: "")
            val width = when (r.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                else -> 20
            }
            return AccessPoint(
                ssid = name,
                bssid = r.BSSID ?: "",
                freqMhz = r.frequency,
                rssi = r.level,
                channel = RadioMath.channelOf(r.frequency),
                band = RadioMath.bandOf(r.frequency),
                widthMhz = width,
            )
        }
    }
}
```

- [ ] **Step 2:** Kompiliert (keine eigene Testlogik — reines Mapping, wird in Task 5 über ViewModel indirekt geprüft).
- [ ] **Step 3:** Commit + push.

### Task 4: WifiScanner (WifiManager-Wrapper, Flow)

**Files:**
- Create: `android/app/src/main/java/com/nmrf/remote/wifi/WifiScanner.kt`

**Interfaces:**
- Consumes: `AccessPoint.fromScan`.
- Produces: `interface WifiSource { val results: Flow<List<AccessPoint>>; fun requestScan(): Boolean; fun latest(): List<AccessPoint> }` und `class WifiScanner(context) : WifiSource`. `requestScan()` gibt `false` zurück, wenn `startScan()` gedrosselt/abgelehnt wurde. Das Interface erlaubt einen Fake im ViewModel-Test.

- [ ] **Step 1:** Interface + Impl mit `BroadcastReceiver` auf `SCAN_RESULTS_AVAILABLE_ACTION`, `callbackFlow`:

```kotlin
package com.nmrf.remote.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface WifiSource {
    val results: Flow<List<AccessPoint>>
    fun requestScan(): Boolean
    fun latest(): List<AccessPoint>
}

class WifiScanner(context: Context) : WifiSource {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    override fun latest(): List<AccessPoint> =
        runCatching { wifi.scanResults.map(AccessPoint::fromScan) }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    override fun requestScan(): Boolean = runCatching { wifi.startScan() }.getOrDefault(false)

    override val results: Flow<List<AccessPoint>> = callbackFlow {
        trySend(latest())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(latest()) }
        }
        app.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        awaitClose { app.unregisterReceiver(receiver) }
    }
}
```

- [ ] **Step 2:** Kompiliert (Instrumentierung braucht Gerät — deshalb kein Unit-Test hier; Logik testet Task 5 über den Fake).
- [ ] **Step 3:** Commit + push.

### Task 5: WifiAnalyzerViewModel (Sortierung/Band-Filter, TDD)

**Files:**
- Create: `android/app/src/main/java/com/nmrf/remote/wifi/WifiAnalyzerViewModel.kt`
- Test: `android/app/src/test/java/com/nmrf/remote/wifi/WifiAnalyzerViewModelTest.kt`

**Interfaces:**
- Consumes: `WifiSource`, `AccessPoint`, `Band`.
- Produces: `class WifiAnalyzerViewModel(source: WifiSource)`; `data class UiState(val visible: List<AccessPoint>, val selectedBand: Band, val scanning: Boolean)`; `val state: StateFlow<UiState>`; `fun selectBand(b: Band)`; `fun rescan()`. `visible` = nach `selectedBand` gefiltert und nach RSSI absteigend sortiert.

- [ ] **Step 1: Failing test** mit Fake-Source:

```kotlin
class WifiAnalyzerViewModelTest {
    private fun ap(rssi: Int, band: Band) =
        AccessPoint("n$rssi", "00:00", if (band==Band.GHZ_5) 5180 else 2412, rssi, 1, band, 20)

    private class Fake(val list: List<AccessPoint>) : WifiSource {
        override val results = kotlinx.coroutines.flow.flowOf(list)
        override fun requestScan() = true
        override fun latest() = list
    }

    @Test fun sorts_by_rssi_and_filters_band() = runTest {
        val src = Fake(listOf(ap(-70, Band.GHZ_2_4), ap(-40, Band.GHZ_2_4), ap(-50, Band.GHZ_5)))
        val vm = WifiAnalyzerViewModel(src)
        vm.selectBand(Band.GHZ_2_4)
        val s = vm.state.value
        assertEquals(listOf(-40, -70), s.visible.map { it.rssi })  // sortiert, 5 GHz raus
    }
}
```

- [ ] **Step 2:** Test → FAIL. `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 3: Implementation** — sammelt `results` in `viewModelScope`, hält Rohliste, projiziert `UiState`:

```kotlin
package com.nmrf.remote.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WifiAnalyzerViewModel(private val source: WifiSource) : ViewModel() {
    private val raw = MutableStateFlow(source.latest())
    private val band = MutableStateFlow(Band.GHZ_2_4)
    private val scanning = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(raw, band, scanning) { aps, b, sc ->
        UiState(aps.filter { it.band == b }.sortedByDescending { it.rssi }, b, sc)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(emptyList(), Band.GHZ_2_4, false))

    init { viewModelScope.launch { source.results.collect { raw.value = it } } }

    fun selectBand(b: Band) { band.value = b }
    fun rescan() { scanning.value = source.requestScan() }
}

data class UiState(val visible: List<AccessPoint>, val selectedBand: Band, val scanning: Boolean)
```

- [ ] **Step 4:** Test → PASS (mit `kotlinx-coroutines-test` `runTest`).
- [ ] **Step 5:** Commit + push.

### Task 6: WifiAnalyzerScreen (Compose UI) + MainActivity-Verdrahtung

**Files:**
- Create: `android/app/src/main/java/com/nmrf/remote/wifi/WifiAnalyzerScreen.kt`
- Modify: `android/app/src/main/java/com/nmrf/remote/MainActivity.kt`

**Interfaces:**
- Consumes: `WifiAnalyzerViewModel`, `UiState`, `AccessPoint`, `Band`, `WifiScanner`.
- Produces: `@Composable fun WifiAnalyzerScreen(vm: WifiAnalyzerViewModel, hasPermission: Boolean, onRequestPermission: () -> Unit)`.

- [ ] **Step 1:** Permission-Flow in `MainActivity` (`rememberLauncherForActivityResult` / `ActivityResultContracts.RequestPermission` für `ACCESS_FINE_LOCATION`), VM via `viewModels { factory }` mit `WifiScanner(this)`.
- [ ] **Step 2:** Screen: Band-`SegmentedButton` (2.4/5 GHz), `ChannelGraph`-Canvas (X=Kanäle des Bandes, je AP ein Dreieck an seinem Kanal, Höhe ∝ RSSI −90…−30, Farbe pro Band), Netz-`LazyColumn` (SSID/`<hidden>`, Kanal·Band·Breite, RSSI + Balken), Kopfzeile mit Rescan-Button. Fehlt Permission → Hinweis + Button.
- [ ] **Step 3:** `@Preview` mit Fake-`UiState`.
- [ ] **Step 4:** Auf Gerät starten (Leon): Permission erteilen, Netze erscheinen, Band-Umschalten filtert, Rescan aktualisiert.
- [ ] **Step 5:** Commit + push.

## Self-Review

- **Spec-Abdeckung:** Netz-Liste (T6), Kanal-Graph (T6), Band-Umschalter (T6), Auto/Rescan+Throttle (T5 `rescan()`/`scanning`, T6 Button), Permission-Flow (T6/MainActivity), RadioMath 2.4/5/6 GHz (T2), Datentyp (T3), WifiManager-Wrapper (T4). Out-of-Scope (echtes Spektrum/Deauth) bleibt draußen. ✔
- **Platzhalter:** keine — jeder Code-Step hat echten Code. ✔
- **Typ-Konsistenz:** `Band`, `AccessPoint`, `WifiSource`, `UiState` durchgehend gleich benannt; `RadioMath.channelOf/bandOf` einheitlich. `WifiAnalyzerViewModel(source: WifiSource)` erlaubt Fake im Test. ✔
