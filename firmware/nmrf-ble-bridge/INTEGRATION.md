# Integration in den Bruce-1.16-Fork

Alles verifiziert gegen Tag `1.16` (github.com/pr3y/Bruce).

## 1. Dateien einlegen
`ble_remote.h` + `ble_remote.cpp` nach **`src/modules/ble/`** kopieren
(neben `ble_common.cpp`). PlatformIO globbt `src/**`, kein Build-Skript nötig.

## 2. Aktivieren
Minimal (Autostart beim Booten): in **`src/main.cpp`**, in `setup()` nach
`startSerialCommandsHandlerTask(true);` (dort L449):
```cpp
#include "modules/ble/ble_remote.h"
...
if (bruceConfig.bleRemote) bleRemoteStart();
```
Kein Loop-Hook nötig — RX ist event-getrieben (onWrite), Re-Advertising passiert in onDisconnect.

## 3. Settings-Toggle „BLE Remote" (persistent)
Bruce speichert Config als JSON `/bruce.conf` über `BruceConfig` (`src/core/config.{h,cpp}`).
- `config.h`: Feld `int bleRemote = 0;` + `void setBleRemote(int value);` (Muster wie `wifiAtStartup`).
- `config.cpp`: Feld in `toJson()` **und** `fromFile()` ergänzen; `setBleRemote()` setzt + `saveFile()`.
- Menüpunkt in `src/core/settings.cpp`: Toggle, der bei An `bleRemoteStart()` / bei Aus `bleRemoteStop()` ruft.
- (Optional CLI-Toggle in `src/core/serial_commands/settings_commands.cpp`.)

## 4. Bauen & Flashen (PlatformIO)
Env für ESP32-2432S028 „CYD":
- `CYD-2432S028`  (Standard, ein Micro-USB, resistiver Touch XPT2046)
- `CYD-2USB`      (Revision mit Micro-USB **und** USB-C)
```
pio run -e CYD-2432S028 -t upload      # ggf. CYD-2USB
```

## 5. Verbinden (App)
Im BLE-Tab der App das Gerät **NMRF-HAT** (NUS-UUID `6E400001-…`) verbinden.
App schreibt Befehle auf RX (`6E400002`); z.B. `info`, `ir`, `subghz`, `nav` … (Flipper-CLI-kompatibel).

## 6. Wichtige Hinweise (aus dem Quellcheck)
- **Ein NimBLE-Stack:** BLE-Angriffe (ble_spam/sniffer/BLE_Suite) init/deinit denselben Stack.
  Läuft ein Angriff, kollidiert die Bridge → Toggle mit BLE-Angriffen **gegenseitig ausschließen**
  (unser `getInitialized()`-Guard verhindert Kollision, aber der Angriff „gewinnt").
- Bruce nutzt eine **eigene** Service-UUID (nicht NUS) → keine UUID-Kollision.
- **Ausgabe über BLE (v2):** SerialDevice-Unterklasse → `txChar->notify()`, `serialDevice`
  bei Connect umbiegen. Bis dahin ist v1 „blind steuern" (Befehle senden, Rückmeldung am HAT-Display).

## 7. Sicherheit
Kanal fährt die **volle CLI** (inkl. `gpio`, `badusb`) → nur abgeschottetes Labor.
Für verschlüsseltes Pairing die Passkey-Zeilen in `bleRemoteStart()` aktivieren
(dann muss die App bonden).
