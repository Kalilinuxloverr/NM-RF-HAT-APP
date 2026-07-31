# NM-RF-HAT BLE-Remote — Fundament — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine native iOS-App steuert einen NM-RF-HAT (Bruce-Firmware, ESP32-2432S028/CYD) über einen selbst hinzugefügten BLE-Nordic-UART-Kanal, der Bruces bestehende Text-CLI transportiert; v1 liefert Verbindungs-, Terminal- und Nav-Pad-Screen.

**Architecture:** Bruce-Firmware wird geforkt und um einen NimBLE Nordic-UART-Service (NUS) erweitert; ein `BleSerialStream : Stream` schleust empfangene Zeilen in Bruces `parseSerialCommand()` und CLI-Ausgabe zurück per Notify. Die iOS-App spricht diesen Service über CoreBluetooth an, gekapselt hinter dem Protokoll `BruceLink`; alle Screens hängen nur an `BruceLink`, nie direkt an CoreBluetooth.

**Tech Stack:** Firmware: C++/Arduino, NimBLE-Arduino, PlatformIO. App: Swift 5.9+, SwiftUI, CoreBluetooth, XCTest. Ziel iOS 17+.

## Global Constraints

- Hardware: NM-RF-HAT auf ESP32-2432S028 („CYD"); Bruce-Firmware, PlatformIO-Build.
- BLE Nordic-UART-UUIDs (verbatim): Service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, RX (Write) `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`, TX (Notify) `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`.
- Zeilenprotokoll: Befehle app→gerät enden mit `\n`; Ausgabe gerät→app ist zeilenbasiert (`\n`, optional `\r` davor).
- NUS nur aktiv, wenn Bruce-Settings-Toggle „BLE Remote" an ist; Verbindung mit NimBLE-Passkey-Bonding (verschlüsselt) als Default.
- Ceiling: Bruce-BLE-Angriffe (Spam/BadBLE) und Control-BLE schließen sich gegenseitig aus (gemeinsamer NimBLE-Stack). Ein zentrales Gerät gleichzeitig.
- Kein WiFi-Fallback, keine Feature-Screens (SubGHz/IR/NFC/…), keine Standalone-Lab-Tools — das sind spätere Sub-Projekte.
- Firmware-Verifikation ist Hardware-Loopback (kein Unit-Framework auf dem MCU); Swift-Kernlogik wird per XCTest TDD-getestet.

---

## File Structure

**Firmware (im Bruce-Fork):**
- `src/ble/BleSerialStream.h` / `.cpp` — Arduino-`Stream` über BLE-Ringpuffer (neu).
- `src/ble/BleRemote.h` / `.cpp` — NUS-Setup, Callbacks, Loop-Poll, Settings-Toggle-Gate (neu).
- Bruces Haupt-Loop (Datei per Task 2 lokalisiert) — 2 Zeilen: `bleRemoteSetup()` / `bleRemoteLoop()` (modify).
- Bruces Settings-Menü/Struct (per Task 5 lokalisiert) — Toggle „BLE Remote" (modify).

**App (`app/NMRFRemote/` Xcode-Projekt):**
- `NMRFRemoteApp.swift` — App-Entry, DI des `BruceLink` (neu).
- `Link/BruceLink.swift` — Protokoll + `ConnectionState` (neu).
- `Link/LineAssembler.swift` — Byte→Zeilen-Reassembly, pure (neu).
- `Link/Chunker.swift` — Data→MTU-Chunks, pure (neu).
- `Link/BleBruceLink.swift` — CoreBluetooth-Impl. von `BruceLink` (neu).
- `Link/MockBruceLink.swift` — In-Memory-Fake für Previews/Tests (neu).
- `Screens/ConnectionView.swift`, `Screens/TerminalView.swift`, `Screens/NavPadView.swift` (neu).
- `Info.plist` — `NSBluetoothAlwaysUsageDescription` (modify).
- Tests: `NMRFRemoteTests/LineAssemblerTests.swift`, `ChunkerTests.swift`, `TerminalViewModelTests.swift`.

---

## Task 1: Bruce-Fork bauen & Baseline flashen

**Files:**
- Create: `firmware/` (Klon des Bruce-Forks), lokale `platformio.ini`-Notiz.

**Interfaces:**
- Produces: lauffähige, **unveränderte** Bruce-Baseline auf dem CYD; bekannter `env`-Name in `platformio.ini`; funktionierende PlatformIO-CLI.

- [ ] **Step 1: Bruce-Firmware forken & klonen**

```bash
# Auf GitHub BruceDevices/firmware forken (Web-UI), dann:
cd /Users/leonfrohlich/Claude/Projects/NM-RF-HAT-APP
git clone https://github.com/<dein-user>/firmware.git firmware
cd firmware && git checkout -b nmrf-ble-remote
```

- [ ] **Step 2: PlatformIO-CLI installieren (falls nötig)**

```bash
python3 -m pip install --user platformio
pio --version   # erwartet: PlatformIO Core, x.y.z
```

- [ ] **Step 3: CYD-Env identifizieren**

Run: `grep -nE '^\[env:' firmware/platformio.ini`
Die NM-RF-HAT sitzt auf dem ESP32-2432S028 (CYD). Wähle das Env, das der aktuell geflashten `.bin` entspricht (CYD-Variante, Touch/USB). Notiere den Namen als `<CYD_ENV>`.
Erwartet: eine Zeile wie `[env:CYD-2432S028]` o.ä.

- [ ] **Step 4: Baseline bauen**

Run: `cd firmware && pio run -e <CYD_ENV>`
Expected: `SUCCESS`; Artefakt unter `.pio/build/<CYD_ENV>/firmware.bin`.

- [ ] **Step 5: Baseline flashen & prüfen**

```bash
pio run -e <CYD_ENV> -t upload
pio device monitor -b 115200   # Bruce bootet, Serial-Banner erscheint
```
Am Display Bruce-Menü sichtbar → Baseline OK. `Ctrl+C` beendet Monitor.

- [ ] **Step 6: Commit**

```bash
cd firmware && git add -A && git commit -m "chore: branch nmrf-ble-remote from Bruce baseline"
```

---

## Task 2: CLI-I/O-Routing untersuchen (Integrationsentscheidung)

**Files:**
- Modify: keine (reine Untersuchung); Ergebnis als `firmware/BLE_REMOTE_NOTES.md`.

**Interfaces:**
- Consumes: Bruce-Baseline aus Task 1.
- Produces: dokumentierte Antwort auf: (a) Typ/Name des aktiven CLI-Geräts (erwartet `serialDevice`, ein `Stream*`), (b) wie `parseSerialCommand()` aufgerufen wird, (c) wohin die Ausgabe geht → Entscheidung **Zeiger-Swap** vs. **Tee**; außerdem installierte NimBLE-Version + exakte `nav`-Token.

- [ ] **Step 1: CLI-Einstiegspunkte finden**

```bash
cd firmware
grep -rn "parseSerialCommand\|handleSerialCommands\|serialDevice\|SerialCli" src/ | head -50
```
Erwartet: Fundstellen in `src/core/serialcmds.*`.

- [ ] **Step 2: Signatur & Ausgabeweg bestimmen**

Öffne die Fundstellen. Beantworte in `firmware/BLE_REMOTE_NOTES.md`:
- Ist `serialDevice` ein globaler `Stream*` (oder `HWCDC*`/`Print*`)? Exakter Typ + Datei:Zeile.
- Nimmt `parseSerialCommand(...)` das Ausgabe-Gerät als Parameter, oder schreibt der Command-Code direkt an das globale `serialDevice`/`Serial`?
- **Entscheidung:**
  - Schreibt der Code an das **aktive `serialDevice`** → Ansatz **Zeiger-Swap** (Task 4): vor dem BLE-Kommando `serialDevice = &bleStream;` setzen, danach zurück.
  - Schreibt der Code **fest an globales `Serial`** → Ansatz **Tee** (Task 4-Fallback): einen `Print`-Wrapper, der zusätzlich in `bleStream` schreibt, temporär als Ziel setzen — oder, falls nicht möglich, nur `nav`/kurze Kommandos ohne Rückkanal (dokumentieren).
- NimBLE-Arduino-Version aus `firmware/.pio/libdeps/<CYD_ENV>/NimBLE-Arduino/library.json` notieren (bestimmt Callback-Signaturen in Task 4).

- [ ] **Step 3: `nav`-Token bestimmen (für Task 11)**

```bash
grep -rn "\"nav\"\|nav \|cmd_nav\|navigate\|\"option\"" src/core/serialcmds.* src/ | head -30
```
Notiere die exakten Richtungs-Token (z.B. `nav up`/`down`/`left`/`right`/`sel`/`esc` — genaue Schreibweise aus dem Source) in `BLE_REMOTE_NOTES.md` unter „Nav-Mapping".

- [ ] **Step 4: Commit**

```bash
git add BLE_REMOTE_NOTES.md && git commit -m "docs: CLI I/O routing + nav tokens investigation"
```

---

## Task 3: `BleSerialStream` (Arduino-Stream über BLE-Puffer)

**Files:**
- Create: `firmware/src/ble/BleSerialStream.h`, `firmware/src/ble/BleSerialStream.cpp`

**Interfaces:**
- Consumes: Arduino `Stream`, NimBLE `NimBLECharacteristic`.
- Produces: Klasse `BleSerialStream` mit `pushRx(const uint8_t*, size_t)`, `setTxChar(NimBLECharacteristic*)`, `setConnected(bool)`, `setMtu(uint16_t)`; implementiert `available/read/peek/write/flush`.

- [ ] **Step 1: Header schreiben**

```cpp
// firmware/src/ble/BleSerialStream.h
#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include <deque>
#include <vector>

class BleSerialStream : public Stream {
public:
    void setTxChar(NimBLECharacteristic* tx) { _tx = tx; }
    void setConnected(bool c) { _connected = c; }
    void setMtu(uint16_t mtu) { _payload = (mtu > 3) ? (mtu - 3) : 20; } // ATT-Header 3 Bytes
    void pushRx(const uint8_t* data, size_t len);

    int available() override;
    int read() override;
    int peek() override;
    size_t write(uint8_t b) override;
    size_t write(const uint8_t* buf, size_t size) override;
    void flush() override;

private:
    std::deque<uint8_t> _rx;
    std::vector<uint8_t> _tx_buf;
    NimBLECharacteristic* _tx = nullptr;
    volatile bool _connected = false;
    size_t _payload = 20;
    portMUX_TYPE _mux = portMUX_INITIALIZER_UNLOCKED; // schützt _rx (BLE-Task vs. Loop-Task)
};
```

- [ ] **Step 2: Implementierung schreiben**

```cpp
// firmware/src/ble/BleSerialStream.cpp
#include "BleSerialStream.h"

void BleSerialStream::pushRx(const uint8_t* data, size_t len) {
    portENTER_CRITICAL(&_mux);
    for (size_t i = 0; i < len; i++) _rx.push_back(data[i]);
    portEXIT_CRITICAL(&_mux);
}

int BleSerialStream::available() {
    portENTER_CRITICAL(&_mux);
    int n = (int)_rx.size();
    portEXIT_CRITICAL(&_mux);
    return n;
}

int BleSerialStream::read() {
    portENTER_CRITICAL(&_mux);
    int c = _rx.empty() ? -1 : _rx.front();
    if (!_rx.empty()) _rx.pop_front();
    portEXIT_CRITICAL(&_mux);
    return c;
}

int BleSerialStream::peek() {
    portENTER_CRITICAL(&_mux);
    int c = _rx.empty() ? -1 : _rx.front();
    portEXIT_CRITICAL(&_mux);
    return c;
}

size_t BleSerialStream::write(uint8_t b) {
    _tx_buf.push_back(b);
    if (b == '\n') flush();
    return 1;
}

size_t BleSerialStream::write(const uint8_t* buf, size_t size) {
    for (size_t i = 0; i < size; i++) write(buf[i]);
    return size;
}

void BleSerialStream::flush() {
    if (!_connected || _tx == nullptr || _tx_buf.empty()) { _tx_buf.clear(); return; }
    for (size_t off = 0; off < _tx_buf.size(); off += _payload) {
        size_t n = std::min(_payload, _tx_buf.size() - off);
        _tx->setValue(_tx_buf.data() + off, n);
        _tx->notify();
        delay(3); // ponytail: kleine Pause gegen Notify-Überlauf; feiner tunen wenn Durchsatz zählt
    }
    _tx_buf.clear();
}
```

- [ ] **Step 3: Commit** (Kompilieren erfolgt verdrahtet in Task 4).

```bash
git add src/ble/BleSerialStream.* && git commit -m "feat(ble): BleSerialStream Arduino stream over BLE buffers"
```

---

## Task 4: NUS-Service + Verdrahtung in die CLI-Schleife (Loopback grün)

**Files:**
- Create: `firmware/src/ble/BleRemote.h`, `firmware/src/ble/BleRemote.cpp`
- Modify: Bruce-Haupt-Loop-Datei (aus Task 2), 2 Zeilen.

**Interfaces:**
- Consumes: `BleSerialStream` (Task 3), Routing-Entscheidung + NimBLE-Version (Task 2), `parseSerialCommand`/`serialDevice`-Symbole (Task 2).
- Produces: `void bleRemoteSetup();`, `void bleRemoteLoop();`, `bool bleRemoteEnabled;` (global, von Task 5 gesetzt).

- [ ] **Step 1: `BleRemote.h`**

```cpp
// firmware/src/ble/BleRemote.h
#pragma once
extern bool bleRemoteEnabled;   // von Settings-Toggle gesetzt (Task 5)
void bleRemoteSetup();          // startet NUS-Advertising, wenn bleRemoteEnabled
void bleRemoteLoop();           // pollt BLE-Zeilen und füttert die CLI
```

- [ ] **Step 2: `BleRemote.cpp` — NUS + Callbacks + Loop-Bridge**

```cpp
// firmware/src/ble/BleRemote.cpp
#include "BleRemote.h"
#include "BleSerialStream.h"
#include <NimBLEDevice.h>

// Symbole aus Bruce (Task 2 bestätigt exakte Namen/Header):
extern Stream* serialDevice;                 // aktives CLI-Gerät (Zeiger-Swap-Ansatz)
void parseSerialCommand(String cmd);         // Bruce-CLI-Parser (Signatur ggf. anpassen)

#define NUS_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX_UUID      "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_TX_UUID      "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

bool bleRemoteEnabled = false;
static BleSerialStream bleStream;
static NimBLECharacteristic* txChar = nullptr;
static bool started = false;

class RxCallbacks : public NimBLECharacteristicCallbacks {
    // Signatur an installierte NimBLE-Version anpassen (v2.x: onWrite(chr, connInfo)).
    void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override {
        std::string v = c->getValue();
        bleStream.pushRx((const uint8_t*)v.data(), v.size());
    }
};

class SrvCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* s, NimBLEConnInfo& info) override {
        bleStream.setMtu(info.getMTU());
        bleStream.setConnected(true);
    }
    void onDisconnect(NimBLEServer* s, NimBLEConnInfo& info, int reason) override {
        bleStream.setConnected(false);
        NimBLEDevice::startAdvertising();
    }
};

void bleRemoteSetup() {
    if (!bleRemoteEnabled || started) return;
    NimBLEDevice::init("NM-RF-HAT");
    NimBLEDevice::setSecurityAuth(true, true, true);        // Bonding + MITM + SC
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_ONLY); // Passkey am CYD anzeigen
    NimBLEServer* srv = NimBLEDevice::createServer();
    srv->setCallbacks(new SrvCallbacks());
    NimBLEService* svc = srv->createService(NUS_SERVICE_UUID);
    NimBLECharacteristic* rx = svc->createCharacteristic(
        NUS_RX_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
    rx->setCallbacks(new RxCallbacks());
    txChar = svc->createCharacteristic(NUS_TX_UUID, NIMBLE_PROPERTY::NOTIFY);
    bleStream.setTxChar(txChar);
    svc->start();
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(NUS_SERVICE_UUID);
    adv->start();
    started = true;
}

void bleRemoteLoop() {
    if (!started) return;
    static String line;
    while (bleStream.available()) {
        int c = bleStream.read();
        if (c == '\n') {
            line.trim();
            if (line.length()) {
                Stream* prev = serialDevice;   // Zeiger-Swap: Ausgabe → BLE
                serialDevice = &bleStream;
                parseSerialCommand(line);
                bleStream.flush();
                serialDevice = prev;
            }
            line = "";
        } else if (c >= 0 && c != '\r') {
            line += (char)c;
        }
    }
}
```

> Falls Task 2 den **Tee**-Ansatz ergab: statt `serialDevice`-Swap den dort dokumentierten Tee-`Print` als Ziel setzen. Falls Bruce NimBLE bereits initialisiert hat, `NimBLEDevice::init`/Security nicht doppelt aufrufen — dann nur Server/Service/Advertising ergänzen.

- [ ] **Step 3: In Bruce-Loop einhängen**

In der Haupt-Loop-Datei (Task 2): `#include "ble/BleRemote.h"`, in `setup()` nach BLE-Init `bleRemoteSetup();`, in `loop()` `bleRemoteLoop();`.

- [ ] **Step 4: Bauen**

Run: `cd firmware && pio run -e <CYD_ENV>`
Expected: `SUCCESS`. Bei NimBLE-Signaturfehlern die Callback-Signaturen an die installierte NimBLE-Version anpassen (Version aus Task 2).

- [ ] **Step 5: Flashen & Loopback-Check (Akzeptanztest)**

```bash
pio run -e <CYD_ENV> -t upload
```
Mit einer generischen BLE-App (z.B. „nRF Connect") zum Gerät `NM-RF-HAT` verbinden, NUS finden, an RX `info\n` schreiben, TX-Notifies abonnieren.
Expected: nicht-leere Textantwort (Bruce-Versionsinfo) über TX. → Kette RX→parseSerialCommand→TX bewiesen.

- [ ] **Step 6: Commit**

```bash
git add src/ble/BleRemote.* <loop-datei> && git commit -m "feat(ble): NUS service bridged into Bruce CLI; info loopback works"
```

---

## Task 5: Settings-Toggle „BLE Remote" + Passkey

**Files:**
- Modify: Bruce-Settings-Struct/-Menü (per grep lokalisiert).

**Interfaces:**
- Consumes: `bleRemoteEnabled` (Task 4), Bruce-Settings/NVS.
- Produces: persistenter Menüpunkt, der `bleRemoteEnabled` setzt und `bleRemoteSetup()`/Advertising-Stopp auslöst.

- [ ] **Step 1: Settings-Persistenz finden**

```bash
cd firmware
grep -rn "EEPROM\|Preferences\|nvs\|saveSettings\|settings\." src/core/settings* src/ | head -30
```

- [ ] **Step 2: Toggle ergänzen**

Analog zu einem bestehenden bool-Setting (z.B. `dimmerSet`/`ledBright`): Feld `bleRemote` in die Settings-Struktur, Laden/Speichern über NVS, und einen Menüeintrag „BLE Remote" (an/aus). Beim Umschalten `bleRemoteEnabled = settings.bleRemote;` und bei „an" `bleRemoteSetup();`, bei „aus" `NimBLEDevice::getAdvertising()->stop();`.

- [ ] **Step 3: Bauen & prüfen**

Run: `cd firmware && pio run -e <CYD_ENV> -t upload`
Am Gerät Toggle aus → `NM-RF-HAT` nicht mehr advertised; an → wieder sichtbar. Beim Pairing zeigt das Display einen Passkey.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(ble): settings toggle 'BLE Remote' + passkey bonding"
```

---

## Task 6: Xcode-Projekt + `BruceLink`-Protokoll

**Files:**
- Create: `app/NMRFRemote.xcodeproj` (Xcode: iOS App, SwiftUI, iOS 17), `app/NMRFRemote/NMRFRemoteApp.swift`, `app/NMRFRemote/Link/BruceLink.swift`
- Modify: `app/NMRFRemote/Info.plist`

**Interfaces:**
- Produces: `protocol BruceLink`, `enum ConnectionState`, `struct DiscoveredDevice`.

- [ ] **Step 1: Xcode-Projekt anlegen**

Xcode → New Project → iOS App, Interface SwiftUI, Name `NMRFRemote`, Ordner `app/`. Deployment Target iOS 17.

- [ ] **Step 2: Bluetooth-Usage-String**

In `Info.plist` ergänzen: Key `NSBluetoothAlwaysUsageDescription`, Wert `Steuert den NM-RF-HAT über Bluetooth.`

- [ ] **Step 3: `BruceLink`-Protokoll**

```swift
// app/NMRFRemote/Link/BruceLink.swift
import Foundation

enum ConnectionState: Equatable {
    case disconnected, scanning, connecting, connected
}

struct DiscoveredDevice: Identifiable, Equatable {
    let id: UUID          // CBPeripheral.identifier
    let name: String
    let rssi: Int
}

@MainActor
protocol BruceLink: AnyObject {
    var state: ConnectionState { get }
    var discovered: [DiscoveredDevice] { get }
    var output: AsyncStream<String> { get }   // vollständige Zeilen
    func startScan()
    func connect(_ id: UUID)
    func disconnect()
    func send(_ command: String)
}
```

- [ ] **Step 4: Commit**

```bash
cd app && git add -A && git commit -m "feat(app): Xcode project + BruceLink protocol"
```

---

## Task 7: Reassembly + Chunking (pure Logik, TDD)

**Files:**
- Create: `app/NMRFRemote/Link/LineAssembler.swift`, `app/NMRFRemote/Link/Chunker.swift`
- Test: `app/NMRFRemoteTests/LineAssemblerTests.swift`, `app/NMRFRemoteTests/ChunkerTests.swift`

**Interfaces:**
- Produces: `struct LineAssembler { mutating func feed(_ chunk: Data) -> [String] }`; `func chunk(_ data: Data, maxLength: Int) -> [Data]`.

- [ ] **Step 1: Failing tests schreiben**

```swift
// app/NMRFRemoteTests/LineAssemblerTests.swift
import XCTest
@testable import NMRFRemote

final class LineAssemblerTests: XCTestCase {
    func test_fragmented_notifies_reassemble_into_lines() {
        var a = LineAssembler()
        XCTAssertEqual(a.feed(Data("Bru".utf8)), [])
        XCTAssertEqual(a.feed(Data("ce v1\r\nfree".utf8)), ["Bruce v1"])
        XCTAssertEqual(a.feed(Data(": 200k\n".utf8)), ["free: 200k"])
    }
    func test_multiple_lines_in_one_chunk() {
        var a = LineAssembler()
        XCTAssertEqual(a.feed(Data("a\nb\nc\n".utf8)), ["a", "b", "c"])
    }
}
```

```swift
// app/NMRFRemoteTests/ChunkerTests.swift
import XCTest
@testable import NMRFRemote

final class ChunkerTests: XCTestCase {
    func test_splits_into_max_length_pieces() {
        let d = Data("abcdefg".utf8)
        let parts = chunk(d, maxLength: 3)
        XCTAssertEqual(parts.map { String(decoding: $0, as: UTF8.self) }, ["abc", "def", "g"])
    }
    func test_short_data_single_chunk() {
        XCTAssertEqual(chunk(Data("ab".utf8), maxLength: 20).count, 1)
    }
}
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `xcodebuild test -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: FAIL (Symbole `LineAssembler`/`chunk` nicht gefunden).

- [ ] **Step 3: Implementierung**

```swift
// app/NMRFRemote/Link/LineAssembler.swift
import Foundation

struct LineAssembler {
    private var buffer = Data()
    mutating func feed(_ chunk: Data) -> [String] {
        buffer.append(chunk)
        var lines: [String] = []
        while let nl = buffer.firstIndex(of: 0x0A) {
            var lineData = buffer.subdata(in: buffer.startIndex..<nl)
            if lineData.last == 0x0D { lineData.removeLast() }
            lines.append(String(decoding: lineData, as: UTF8.self))
            buffer.removeSubrange(buffer.startIndex...nl)
        }
        return lines
    }
}
```

```swift
// app/NMRFRemote/Link/Chunker.swift
import Foundation

func chunk(_ data: Data, maxLength: Int) -> [Data] {
    guard maxLength > 0, data.count > maxLength else { return [data] }
    var out: [Data] = []
    var i = data.startIndex
    while i < data.endIndex {
        let end = data.index(i, offsetBy: maxLength, limitedBy: data.endIndex) ?? data.endIndex
        out.append(data.subdata(in: i..<end))
        i = end
    }
    return out
}
```

- [ ] **Step 4: Tests laufen lassen — müssen bestehen**

Run: `xcodebuild test -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/NMRFRemote/Link/LineAssembler.swift app/NMRFRemote/Link/Chunker.swift app/NMRFRemoteTests/ && git commit -m "feat(app): line reassembly + MTU chunking (TDD)"
```

---

## Task 8: `BleBruceLink` (CoreBluetooth) + `MockBruceLink`

**Files:**
- Create: `app/NMRFRemote/Link/BleBruceLink.swift`, `app/NMRFRemote/Link/MockBruceLink.swift`

**Interfaces:**
- Consumes: `BruceLink`, `ConnectionState`, `DiscoveredDevice` (Task 6); `LineAssembler`, `chunk` (Task 7); NUS-UUIDs (Global Constraints).
- Produces: `final class BleBruceLink: NSObject, BruceLink, ObservableObject`; `final class MockBruceLink: BruceLink, ObservableObject`.

- [ ] **Step 1: `BleBruceLink` implementieren**

```swift
// app/NMRFRemote/Link/BleBruceLink.swift
import Foundation
import CoreBluetooth

private let kService = CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private let kRx      = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private let kTx      = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

@MainActor
final class BleBruceLink: NSObject, BruceLink, ObservableObject {
    @Published private(set) var state: ConnectionState = .disconnected
    @Published private(set) var discovered: [DiscoveredDevice] = []

    let output: AsyncStream<String>
    private let outCont: AsyncStream<String>.Continuation

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var rxChar: CBCharacteristic?
    private var assembler = LineAssembler()
    private var mtu = 20

    override init() {
        var c: AsyncStream<String>.Continuation!
        output = AsyncStream { c = $0 }
        outCont = c
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func startScan() {
        discovered = []
        guard central.state == .poweredOn else { return }
        state = .scanning
        central.scanForPeripherals(withServices: [kService])
    }
    func connect(_ id: UUID) {
        guard let p = central.retrievePeripherals(withIdentifiers: [id]).first else { return }
        central.stopScan()
        state = .connecting
        peripheral = p
        p.delegate = self
        central.connect(p)
    }
    func disconnect() {
        if let p = peripheral { central.cancelPeripheralConnection(p) }
    }
    func send(_ command: String) {
        guard let p = peripheral, let rx = rxChar else { return }
        let data = Data((command + "\n").utf8)
        for part in chunk(data, maxLength: max(1, mtu)) {
            p.writeValue(part, for: rx, type: .withoutResponse)
        }
    }

    // MARK: CBCentralManagerDelegate
    nonisolated func centralManagerDidUpdateState(_ c: CBCentralManager) {}
    nonisolated func centralManager(_ c: CBCentralManager, didDiscover p: CBPeripheral,
                                    advertisementData: [String: Any], rssi: NSNumber) {
        Task { @MainActor in
            let dev = DiscoveredDevice(id: p.identifier, name: p.name ?? "NM-RF-HAT", rssi: rssi.intValue)
            if !discovered.contains(where: { $0.id == dev.id }) { discovered.append(dev) }
        }
    }
    nonisolated func centralManager(_ c: CBCentralManager, didConnect p: CBPeripheral) {
        Task { @MainActor in
            mtu = p.maximumWriteValueLength(for: .withoutResponse)
            p.discoverServices([kService])
        }
    }
    nonisolated func centralManager(_ c: CBCentralManager, didDisconnectPeripheral p: CBPeripheral, error: Error?) {
        Task { @MainActor in state = .disconnected; rxChar = nil }
    }

    // MARK: CBPeripheralDelegate
    nonisolated func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        Task { @MainActor in
            if let svc = p.services?.first(where: { $0.uuid == kService }) {
                p.discoverCharacteristics([kRx, kTx], for: svc)
            }
        }
    }
    nonisolated func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) {
        Task { @MainActor in
            for ch in s.characteristics ?? [] {
                if ch.uuid == kRx { rxChar = ch }
                if ch.uuid == kTx { p.setNotifyValue(true, for: ch) }
            }
            state = .connected
        }
    }
    nonisolated func peripheral(_ p: CBPeripheral, didUpdateValueFor ch: CBCharacteristic, error: Error?) {
        guard ch.uuid == kTx, let d = ch.value else { return }
        Task { @MainActor in for line in assembler.feed(d) { outCont.yield(line) } }
    }
}
```

> Hinweis: `BleBruceLink` erbt von `NSObject` und deklariert die CB-Delegate-Konformität in einer `extension` oder direkt; die Delegate-Methoden sind `nonisolated` und hüpfen per `Task { @MainActor }` zurück auf den Main-Actor (CB-Queue ist hier `.main`, daher unkritisch).

- [ ] **Step 2: `MockBruceLink`**

```swift
// app/NMRFRemote/Link/MockBruceLink.swift
import Foundation

@MainActor
final class MockBruceLink: BruceLink, ObservableObject {
    @Published private(set) var state: ConnectionState = .connected
    @Published private(set) var discovered: [DiscoveredDevice] =
        [DiscoveredDevice(id: UUID(), name: "NM-RF-HAT (Mock)", rssi: -42)]
    let output: AsyncStream<String>
    private let cont: AsyncStream<String>.Continuation
    init() { var c: AsyncStream<String>.Continuation!; output = AsyncStream { c = $0 }; cont = c }
    func startScan() {}
    func connect(_ id: UUID) { state = .connected }
    func disconnect() { state = .disconnected }
    func send(_ command: String) {
        cont.yield("$ \(command)")
        cont.yield(command == "info" ? "Bruce vMock" : "ok")
    }
}
```

- [ ] **Step 3: Bauen**

Run: `xcodebuild build -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add app/NMRFRemote/Link/BleBruceLink.swift app/NMRFRemote/Link/MockBruceLink.swift && git commit -m "feat(app): CoreBluetooth BleBruceLink + MockBruceLink"
```

---

## Task 9: Verbindungs-Screen + Tab-Shell

**Files:**
- Create: `app/NMRFRemote/Screens/ConnectionView.swift`
- Modify: `app/NMRFRemote/NMRFRemoteApp.swift`

**Interfaces:**
- Consumes: `BleBruceLink` (Task 8).

- [ ] **Step 1: `ConnectionView`**

```swift
// app/NMRFRemote/Screens/ConnectionView.swift
import SwiftUI

struct ConnectionView: View {
    @ObservedObject var link: BleBruceLink
    var body: some View {
        List {
            Section("Status") { Text(String(describing: link.state)) }
            Section("Geräte") {
                ForEach(link.discovered) { d in
                    Button { link.connect(d.id) } label: {
                        HStack { Text(d.name); Spacer(); Text("\(d.rssi) dBm").foregroundStyle(.secondary) }
                    }
                }
            }
        }
        .navigationTitle("Verbindung")
        .toolbar {
            Button(link.state == .scanning ? "Scan…" : "Scan") { link.startScan() }
            if link.state == .connected { Button("Trennen") { link.disconnect() } }
        }
        .onAppear { link.startScan() }
    }
}
```

- [ ] **Step 2: App-Entry mit TabView**

```swift
// app/NMRFRemote/NMRFRemoteApp.swift
import SwiftUI

@main
struct NMRFRemoteApp: App {
    @StateObject private var link = BleBruceLink()
    var body: some Scene {
        WindowGroup {
            TabView {
                NavigationStack { ConnectionView(link: link) }
                    .tabItem { Label("Verbindung", systemImage: "antenna.radiowaves.left.and.right") }
                NavigationStack { TerminalView(link: link) }
                    .tabItem { Label("Terminal", systemImage: "terminal") }
                NavigationStack { NavPadView(link: link) }
                    .tabItem { Label("Nav", systemImage: "dpad") }
            }
        }
    }
}
```

- [ ] **Step 3: Bauen**

Run: `xcodebuild build -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: `BUILD SUCCEEDED`. (Realer BLE-Test nur auf echtem iPhone.)

- [ ] **Step 4: Commit**

```bash
git add app/NMRFRemote/Screens/ConnectionView.swift app/NMRFRemote/NMRFRemoteApp.swift && git commit -m "feat(app): connection screen + tab shell"
```

---

## Task 10: Terminal-Screen

**Files:**
- Create: `app/NMRFRemote/Screens/TerminalView.swift`
- Test: `app/NMRFRemoteTests/TerminalViewModelTests.swift`

**Interfaces:**
- Consumes: `BruceLink` (Task 6), `BleBruceLink` (Task 8), `MockBruceLink` (Task 8).
- Produces: `@MainActor final class TerminalViewModel: ObservableObject` mit `lines: [String]`, `history: [String]`, `func run(_ cmd: String)`, `func consume(_ line: String)`.

- [ ] **Step 1: Failing ViewModel-Test**

```swift
// app/NMRFRemoteTests/TerminalViewModelTests.swift
import XCTest
@testable import NMRFRemote

@MainActor
final class TerminalViewModelTests: XCTestCase {
    func test_run_appends_echo_and_records_history() {
        let vm = TerminalViewModel(link: MockBruceLink())
        vm.run("info")
        XCTAssertEqual(vm.history.last, "info")
        XCTAssertTrue(vm.lines.contains("$ info"))
    }
    func test_consume_appends_output_line() {
        let vm = TerminalViewModel(link: MockBruceLink())
        vm.consume("Bruce vMock")
        XCTAssertEqual(vm.lines.last, "Bruce vMock")
    }
    func test_blank_command_is_ignored() {
        let vm = TerminalViewModel(link: MockBruceLink())
        vm.run("   ")
        XCTAssertTrue(vm.history.isEmpty)
    }
}
```

- [ ] **Step 2: Test läuft, schlägt fehl**

Run: `xcodebuild test -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: FAIL (`TerminalViewModel` fehlt).

- [ ] **Step 3: ViewModel + View**

```swift
// app/NMRFRemote/Screens/TerminalView.swift
import SwiftUI

@MainActor
final class TerminalViewModel: ObservableObject {
    @Published var lines: [String] = []
    @Published var history: [String] = []
    private let link: BruceLink
    init(link: BruceLink) { self.link = link }
    func consume(_ line: String) { lines.append(line) }
    func run(_ cmd: String) {
        let c = cmd.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !c.isEmpty else { return }
        lines.append("$ \(c)")
        history.append(c)
        link.send(c)
    }
}

struct TerminalView: View {
    @ObservedObject var link: BleBruceLink
    @StateObject private var vm: TerminalViewModel
    @State private var input = ""
    init(link: BleBruceLink) {
        self.link = link
        _vm = StateObject(wrappedValue: TerminalViewModel(link: link))
    }
    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(vm.lines.enumerated()), id: \.offset) { idx, l in
                            Text(l).font(.system(.footnote, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading).id(idx)
                        }
                    }.padding(8)
                }
                .onChange(of: vm.lines.count) { _, n in if n > 0 { proxy.scrollTo(n - 1) } }
            }
            HStack {
                TextField("Befehl…", text: $input)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .font(.system(.body, design: .monospaced))
                    .onSubmit(sendCurrent)
                Button("Senden", action: sendCurrent)
            }.padding(8)
        }
        .navigationTitle("Terminal")
        .task { for await line in link.output { vm.consume(line) } }
    }
    private func sendCurrent() { vm.run(input); input = "" }
}
```

- [ ] **Step 4: Test läuft, besteht**

Run: `xcodebuild test -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/NMRFRemote/Screens/TerminalView.swift app/NMRFRemoteTests/TerminalViewModelTests.swift && git commit -m "feat(app): terminal screen + view model (TDD)"
```

---

## Task 11: Nav-Pad-Screen

**Files:**
- Create: `app/NMRFRemote/Screens/NavPadView.swift`

**Interfaces:**
- Consumes: `BleBruceLink` (Task 8); exakte `nav`-Token aus `firmware/BLE_REMOTE_NOTES.md` (Task 2).

- [ ] **Step 1: Nav-Token einsetzen**

Ersetze unten die Beispiel-Token durch die in Task 2 bestätigte Schreibweise.

```swift
// app/NMRFRemote/Screens/NavPadView.swift
import SwiftUI

struct NavPadView: View {
    @ObservedObject var link: BleBruceLink
    // Token aus firmware/BLE_REMOTE_NOTES.md (Task 2) einsetzen:
    private let up = "nav up", down = "nav down", left = "nav left"
    private let right = "nav right", sel = "nav sel", esc = "nav esc"

    var body: some View {
        VStack(spacing: 16) {
            btn("chevron.up", up)
            HStack(spacing: 16) {
                btn("chevron.left", left)
                btn("circle.fill", sel)
                btn("chevron.right", right)
            }
            btn("chevron.down", down)
            Button("Zurück") { link.send(esc) }.buttonStyle(.bordered).padding(.top, 8)
        }
        .navigationTitle("Nav-Pad")
    }
    private func btn(_ icon: String, _ cmd: String) -> some View {
        Button { link.send(cmd) } label: {
            Image(systemName: icon).font(.title).frame(width: 64, height: 64)
        }.buttonStyle(.borderedProminent)
    }
}
```

- [ ] **Step 2: Bauen & am echten iPhone End-to-End**

Run: `xcodebuild build -scheme NMRFRemote -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: `BUILD SUCCEEDED`.
Dann auf echtem iPhone: Toggle „BLE Remote" am HAT an, pairen (Passkey), verbinden, Nav-Buttons drücken → Bruce-Menü am CYD bewegt sich. Im Terminal `info` → Antwort erscheint.

- [ ] **Step 3: Commit**

```bash
git add app/NMRFRemote/Screens/NavPadView.swift && git commit -m "feat(app): nav-pad screen driving Bruce menus"
```

---

## Self-Review (gegen das Spec)

- **Spec-Coverage:** Firmware BLE-UART-Bridge → Tasks 3–5; App-Core `BruceLink`/BLEManager → Tasks 6–8; Terminal → 10; Nav-Pad → 11; Verbindung → 9; Firmware-Loopback-Test → Task 4 Step 5; App-Unit-Tests (Chunking/Reassembly) → Task 7; Security-Toggle/Passkey → Task 5; Board-Target-Findung → Task 1; Output-Routing-Verifikation zuerst → Task 2. Alle #1-Spec-Punkte abgedeckt.
- **Platzhalter:** Bewusst offene Werte (`<CYD_ENV>`, exakte Bruce-Symbole, `nav`-Token, NimBLE-Version) sind je als konkreter Untersuchungs-Task mit grep-Kommando + Entscheidungskriterium ausgeführt — keine „TODO/später"-Lücken in Code-Schritten.
- **Typkonsistenz:** `BruceLink`/`ConnectionState`/`DiscoveredDevice` (Task 6) werden in Tasks 8–11 identisch verwendet; `LineAssembler.feed(Data)->[String]` und `chunk(Data,maxLength:)->[Data]` (Task 7) exakt so in Task 8 konsumiert; `TerminalViewModel.run/consume` (Task 10) matchen die Tests.
