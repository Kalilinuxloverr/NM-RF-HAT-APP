# App-Shell + Lab-Disclaimer + BLE-Scanner/Analyzer — Design-Spec

**Datum:** 2026-07-31 · **Status:** Freigegeben (Chat) · **Teil:** Modul 1 der Recon-Suite
**Ziel:** Android, Kotlin/Compose, kein GMS. Testgerät Huawei P20 Pro = **Android 10 (API 29)**.

## Zweck
Fundament der App (Bottom-Navigation + Lab-Disclaimer) plus ein standalone **BLE-Recon-Tool**:
Advertisements live scannen, Geräte analysieren, einzelne Geräte per GATT enumerieren. Ohne HAT.

## Cross-cutting Fundament
- **Bottom-Nav** Tabs: WLAN · BLE · Audio · HAT. WLAN-Analyzer wandert in den WLAN-Tab; Audio/HAT sind Platzhalter.
- **Lab-Disclaimer-Gate**: Erststart, muss bestätigt werden, persistent (SharedPreferences `AppPrefs`).

## BLE-Modul
- **Scanner** (`BluetoothLeScanner`, SCAN_MODE_LOW_LATENCY): aggregiert nach MAC, hält RSSI-Historie (40).
- **Datentyp** `BleDevice`: address, name?, rssi, connectable, txPower?, companyId?, manufacturer?, serviceUuids, rawBytes, lastSeen, rssiHistory.
- **CompanyIds** (pure, testbar): Bluetooth-SIG Company-ID → Hersteller.
- **ViewModel**: Filter (Text auf MAC/Name/Hersteller), connectable-only, RSSI-Sortierung; `setEnabled` koppelt Scan an Permission.
- **Screen**: Liste (Name/MAC/Hersteller/RSSI-Balken/connectable) + Detail (RSSI-Sparkline, Service-UUIDs, roher Adv-Hex, **GATT lesen** → Services/Characteristics/Properties, 8 s Timeout).

## Permissions (API-abhängig!)
- **≤ API 30** (P20 Pro): `BLUETOOTH`+`BLUETOOTH_ADMIN` (maxSdk 30) + `ACCESS_FINE_LOCATION` (Runtime, Scan-Pflicht).
- **≥ API 31**: `BLUETOOTH_SCAN` (`neverForLocation`) + `BLUETOOTH_CONNECT` (Runtime).
- Runtime-Flow verzweigt nach `Build.VERSION.SDK_INT`.

## Architektur
`core/{AppPrefs,Permissions,DisclaimerScreen,AppRoot}.kt` · `ble/{BleDevice,CompanyIds,BleScanner,GattProbe,BleScannerViewModel,BleScannerScreen}.kt`. Keine Nav-Lib (State-basierte Tabs/Detail).

## Tests
- `CompanyIdsTest` (bekannt/unbekannt/null).
- `BleScannerViewModelTest` (Fake-`BleSource`: Sortierung, connectable-Filter, Text-Filter).

## Grenze
Standalone-BLE = Recon/Analyse + GATT eigener Geräte. Aktives Spam/BadBLE/Jamming = HAT (Modul 4/5).
