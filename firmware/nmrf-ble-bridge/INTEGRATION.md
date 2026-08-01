# Integration in den Bruce-1.16-Fork (rein additiv — keine Funktion geht verloren)

Verifiziert gegen Tag `1.16` (github.com/pr3y/Bruce). Schnellstart: `./setup_fork.sh` klont
Bruce 1.16 komplett und legt das Overlay ein. Danach die 3 Patches unten anwenden (nur
Hinzufügen, nichts löschen → voller Bruce-Funktionsumfang bleibt erhalten).

## Dateien
`ble_remote.h` + `ble_remote.cpp` → `src/modules/ble/` (PlatformIO globbt `src/**`).

## Patch 1 — Config-Toggle (persistent, `src/core/config.h` + `config.cpp`)
Am einfachsten die bestehenden `wifiAtStartup`-Zeilen kopieren und in `bleRemote` umbenennen.

`config.h` (bei den anderen int-Feldern + Settern):
```cpp
int  bleRemote = 0;                 // + neu
void setBleRemote(int value);       // + neu
```
`config.cpp`:
```cpp
void BruceConfig::setBleRemote(int value) { bleRemote = value; saveFile(); }   // + neu
// in toJson():   setting["bleRemote"] = bleRemote;                            // + neu (Stil der Nachbarzeilen übernehmen)
// in fromFile():  if (!setting["bleRemote"].isNull()) bleRemote = setting["bleRemote"];  // + neu
```

## Patch 2 — Menüpunkt (`src/core/settings.cpp`)
Im Settings-Menü einen Toggle ergänzen (Stil der bestehenden Optionen übernehmen):
```cpp
#include "modules/ble/ble_remote.h"
// ... im options-Vector des Settings-Menüs:
{ "BLE Remote", [=]() {
      bruceConfig.setBleRemote(!bruceConfig.bleRemote);
      if (bruceConfig.bleRemote) bleRemoteStart(); else bleRemoteStop();
  } },
```

## Patch 3 — Autostart (`src/main.cpp`, in setup() nach L449)
```cpp
#include "modules/ble/ble_remote.h"
// ... direkt nach: startSerialCommandsHandlerTask(true);
if (bruceConfig.bleRemote) bleRemoteStart();
```

## Bauen & Flashen
```
pio run -e CYD-2432S028 -t upload      # ESP32-2432S028; dual-USB-Board: CYD-2USB
```

## Verbinden (App)
BLE-/HAT-Tab → Gerät **NMRF-HAT** (NUS `6E400001-…`). App schreibt Befehle auf RX,
Firmware führt sie über die vorhandene CLI aus.

## Hinweise (aus dem Quellcheck)
- **Ein NimBLE-Stack:** Bruce-BLE-Angriffe init/deinit denselben Stack → Toggle mit
  BLE-Angriffen gegenseitig ausschließen (der `getInitialized()`-Guard verhindert Kollision).
- Bruce nutzt eine eigene Service-UUID (nicht NUS) → keine Kollision.
- **v2 (Ausgabe-Echo HAT→App):** SerialDevice-Unterklasse → `txChar->notify()`, `serialDevice`
  bei Connect umbiegen. Bis dahin: Befehle senden + Rückmeldung am HAT-Display.
- **Sicherheit:** volle CLI (inkl. gpio/badusb) → nur Labor. Passkey-Bonding via die
  auskommentierten Zeilen in `bleRemoteStart()` aktivierbar.

## Toolchain-Gotcha (pioarduino + esp32-classic) — WICHTIG fürs Flashen
Bruce weakt `ieee80211_raw_frame_sanity_check` in `libnet80211.a` (für WLAN-Deauth) via
`patch.py`. Dessen objcopy-Aufruf nutzt aber den ALTEN Toolchain-Namen
(`xtensa-esp32-elf-objcopy` / `-p toolchain-xtensa-esp32`) — aktuell heißt sie
`xtensa-esp-elf-objcopy` (`toolchain-xtensa-esp-elf`). Das Weakening schlägt still fehl,
`libnet80211.a` verschwindet, das `.patched`-Flag wird trotzdem gesetzt → Link-Fehler
`cannot find -lnet80211` bzw. `multiple definition of ieee80211_raw_frame_sanity_check`.

Fix (einmalig, nach dem ersten Fehlversuch, dann neu bauen):
```
L=~/.platformio/packages/framework-arduinoespressif32-libs/esp32/lib
BIN=~/.platformio/packages/toolchain-xtensa-esp-elf/bin
[ -f "$L/libnet80211.a" ] || cp "$L/libnet80211.a.old" "$L/libnet80211.a"
"$BIN/xtensa-esp-elf-objcopy" --weaken-symbol=ieee80211_raw_frame_sanity_check "$L/libnet80211.a"
# .patched-Flag bleibt -> patch.py überspringt und zerstört die Lib nicht mehr
```
Verifiziert geflasht: ESP32-D0WD-V3, 4 MB, `pio run -e CYD-2432S028 -t upload`.

## v2b — nRF24-Spektrum (Wasserfall) aktivieren + an die App streamen
Bruce 1.16 hat ein fertiges, aber geparktes `src/modules/NRF24/nrf_spectrum.cpp.bak`
(farbiger Wasserfall + Balken + Peak-Hold + 4 Modi, 126 Kanäle). Aktivieren + Stream:
1. `nrf_spectrum.cpp.bak` -> `nrf_spectrum.cpp` (überschreibt die alte 80-Kanal-Version;
   `scanChannels(bool)` bleibt erhalten, keine externen Referenzen brechen).
2. In `nrf_spectrum.cpp` nach den Includes ergänzen:
   `#include "modules/ble/ble_remote.h"` und `#define NRF_SPECTRUM_CHANNELS 126`.
3. Direkt nach `digitalWrite(bruceConfigPins.NRF24_bus.io0, HIGH);` (Ende des Sweep-Loops):
   `bleRemoteStreamSpectrum(channel, NRF_SPECTRUM_CHANNELS);`
4. `bleRemoteStreamSpectrum(const uint8_t*, int)` ist in `ble_remote.{h,cpp}` enthalten:
   sendet `SPEC:v0,..,vN` (Werte 0..100) per NUS-Notify, wenn ein App-Client verbunden ist.
   In der App zeichnet der SPEKTRUM-Tab daraus einen Live-Wasserfall.

Verifiziert: kompiliert sauber für env CYD-2432S028 (Bruce 1.16, NimBLE 2.5).

## v2c — CYD-Screen-Mirror (Draw-Command-Streaming an die App)
Bruce hat einen `tft_logger`, der jeden Zeichenbefehl als kompaktes Binärpaket serialisiert
(Header 0xAA, Längenbyte, Funktionscode, BE-uint16-Args, RGB565). Unsere Bridge routet
`serialDevice` schon auf NUS-TX. Wire-up in `ble_remote.cpp` (bereits enthalten):
- RxCb-Sonderbefehle: `mirror on` -> `tft.startAsyncSerial();`  ·  `mirror off` -> `tft.stopAsyncSerial();`
- `SrvCb::onDisconnect` ruft `tft.stopAsyncSerial();`
Die App (MIRROR-Tab) sendet `mirror on` beim Öffnen und demuxt den gemischten Stream
(0xAA = Binär-Draw-Op, sonst Text/Echo/SPEC; ASCII enthält nie 0xAA), spielt die Ops per
`TftReplay` auf ein Bitmap = Live-CYD-Screen. `tft` ist über `globals.h` verfügbar.
Verifiziert: kompiliert für env CYD-2432S028.

## v2d — Tap-to-Touch + einstellbares Jammer-PA
- Bridge (`ble_remote.cpp`): `touch x y` -> setzt `touchPoint` + `AnyKeyPress` und ruft `touchHeatMap()`
  (Menü-Region-Nav wie ein Fingertipp; Tastatur pixelgenau). Braucht `#include "core/utils.h"`.
  `jampa 0..3` -> `nrfSetJamPA()`.
- `src/modules/NRF24/nrf_jammer.cpp`: `static rf24_pa_dbm_e jamPA = RF24_PA_MAX;` + `void nrfSetJamPA(int)`
  (nach den Includes); `setPALevel(RF24_PA_MAX)`/`startConstCarrier(RF24_PA_MAX,50)` -> `jamPA`.
  Deklaration `void nrfSetJamPA(int level);` in `nrf_jammer.h`. Wirkt beim (Neu-)Start des Jammers.
- App: MIRROR-Tab antippen -> `touch <x> <y>` (Bild-Koordinate auf 240x320 gemappt); HAT-BEFEHLE-Tab
  hat eine nRF24-Power-Karte (MIN/LOW/HIGH/MAX -> `jampa`).

## v3 (Phase 2) — WLAN-Mirror übers CYD-eigene AP
Bridge-Befehle in ble_remote.cpp: `webon` -> `setWifiApCreds("NMRF-HAT","nmrflab1")` +
`wifiConnectMenu(WIFI_AP)` + `AsyncWebServer(80)`+`configureWebServer()` + `tft.setLogging(true)`
(BLE bleibt aktiv — koexistiert, FORCE_RADIO_TEARDOWN=false). `weboff` -> `stopWebUi()` + `wifiDisconnect()`.
Includes: core/wifi/webInterface.h, core/wifi/wifi_common.h, <WiFi.h>, <new>.
App (WifiMirror): tritt dem AP bei (WifiNetworkSpecifier), `POST /login` (admin/bruce) -> BRUCESESSION-
Cookie, pollt `GET http://172.0.0.1/getscreen` (dieselben 0xAA-Draw-Ops -> TftReplay). Steuerung bleibt
über BLE (touch/nav/commands). Hinweis: WLAN+BLE ist RAM-eng auf non-PSRAM-CYD; scheitert webon,
fällt die App auf BLE-Mirror zurück.

## Basis-Update — Bruce main (dev/Beta), Board CYD-2USB
Ab jetzt basiert der Fork auf **Bruce `main`** (dev/Beta, `BRUCE_VERSION="dev"`) statt Tag 1.16 —
alle Integrationspunkte sind strukturgleich (main.cpp Serial-Task-Zeile, src/modules/ble/,
nrf_spectrum.cpp(.bak), nrf_jammer.cpp PA-Zeilen, patch.py net80211, /getscreen, softAP), das
Overlay + alle Patches greifen 1:1. Gebaut/verifiziert für **env `CYD-2USB`** → `Bruce-CYD-2USB.bin`
(flash-fertig, offset 0x0). Board-Variante bei Bedarf auf `CYD-2432S028` umstellbar.
