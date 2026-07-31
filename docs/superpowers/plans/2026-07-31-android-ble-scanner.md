# Modul 1 (Shell + Disclaimer + BLE-Scanner) — Implementierungsplan

**Goal:** App-Shell (Bottom-Nav) + Lab-Disclaimer + standalone BLE-Recon-Tool.
**Architecture:** Tabs schalten Screens; jeder Screen hält VM + Permission. BLE: `BleScanner`(`BleSource`)→`BleScannerViewModel`→`BleScannerScreen`(+Detail/GATT).
**Tech:** Kotlin/Compose Material3, Coroutines/Flow, JUnit4. Package `com.nmrf.remote`.

## Global Constraints
- minSdk 26 / targetSdk 34 / compileSdk 34, kein GMS.
- BLE-Permissions nach `Build.VERSION` verzweigen (siehe Spec). Testgerät API 29.
- CLI-Build mit JBR 21 (`JAVA_HOME=.../jbr-21.0.11/...`).

## Tasks
1. **AppPrefs + Permissions-Helper** (`core/AppPrefs.kt`, `core/Permissions.kt`): SharedPreferences-Flag; `rememberPermissions(perms)` → `PermissionState(allGranted, request)` via `RequestMultiplePermissions`.
2. **DisclaimerScreen** (`core/DisclaimerScreen.kt`): scrollbarer Labor-Hinweis + Button `onAccept`.
3. **CompanyIds (TDD)** (`ble/CompanyIds.kt` + Test): Company-ID→Name; null/unbekannt→null.
4. **BleDevice + BleScanner** (`ble/BleDevice.kt`, `ble/BleScanner.kt`): `BleSource`-Interface; `callbackFlow` mit `ScanCallback`, MAC-Aggregation, RSSI-Historie.
5. **BleScannerViewModel (TDD)** (`ble/BleScannerViewModel.kt` + Test): `flatMapLatest(enabled)`, Filter/Sortierung, `BleUiState`.
6. **GattProbe** (`ble/GattProbe.kt`): `suspend enumerate(address): Result<List<GattService>>` via `connectGatt`+`discoverServices`, resume-once.
7. **BleScannerScreen** (`ble/BleScannerScreen.kt`): Liste + Detail (Sparkline, Hex, GATT mit 8 s Timeout).
8. **AppRoot + MainActivity + Manifest**: Bottom-Nav, WLAN/BLE-Tabs verdrahten, BT-Permissions ins Manifest.
9. Build + Tests (JBR 21) grün, commit, push.
