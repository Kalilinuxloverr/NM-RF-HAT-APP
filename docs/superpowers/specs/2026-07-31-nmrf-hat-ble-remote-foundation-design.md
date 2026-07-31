# NM-RF-HAT iOS-App — Sub-Projekt #1: Fundament (BLE-Remote)

**Datum:** 2026-07-31
**Status:** Design freigegeben, bereit für Implementierungsplan
**Hardware:** NM-RF-HAT (RockBase-iot) auf ESP32-2432S028 („CYD", Cheap Yellow Display), Bruce-Firmware

---

## Kontext & Gesamtvorhaben

Ziel des Gesamtprojekts: eine native iOS-App (Xcode, SwiftUI), mit der Leon seinen
NM-RF-HAT unter Bruce-Firmware fernsteuert — plus eigenständige Lab-Tools auf dem iPhone.
Nur für den privaten Laborgebrauch.

**Transport-Entscheidung: BLE + Firmware-Fork.** Bruce gibt seine Steuer-CLI **nicht**
über BLE heraus (BLE = nur Angriffe: Spam, BadBLE-HID). Die volle CLI läuft über USB und
über WiFi (`POST http://bruce.local/cm`). Leon hat sich bewusst gegen den fertigen
WiFi-Weg und für BLE entschieden — das erfordert einen Firmware-Patch.

### Physikalische Plattform-Grenzen (formen den gesamten Umfang)

Ein Stock-iPhone kann **selbst kein Funk** im Sub-GHz-Bereich empfangen (kein SDR) und
bekommt von iOS **keine WiFi-Scan-/Monitor-API**. Konsequenzen:

- „Harte" Funk-Angriffe (SubGHz, WiFi-Deauth/Sniff, NFC-Emulation) laufen **auf dem HAT**;
  die App ist deren Frontend/Fernsteuerung.
- Ein echtes RF-Spektrum/Wasserfall ist ohne Hardware unmöglich. Die ehrliche Version
  (späteres Sub-Projekt #4): CC1101-**RSSI-Kanal-Sweep** auf dem HAT, per BLE gestreamt,
  in der App als Wasserfall gerendert (RSSI-Heatmap über Frequenz/Zeit, kein FFT).
- Standalone **ohne HAT** kann das iPhone: Audio-Spektrum/Spektrogramm (Mikrofon, echtes
  FFT), BLE-Advertisement-Scan, lokaler Netzwerk-Scan (Sockets/Bonjour) — Sub-Projekt #3.

### Dekomposition (jedes Sub-Projekt: eigenes Spec → Plan → Build)

| # | Sub-Projekt | Braucht |
|---|-------------|---------|
| **1** | **Fundament** (dieses Spec): Firmware BLE-UART-Bridge + App-Core (BLE, CLI-Layer, Terminal, Nav-Pad) | — |
| 2 | Bruce-Remote-Screens: SubGHz, IR, NFC, System/GPIO/LED, WiFi-Settings … voller Menü-Mirror | #1 |
| 3 | Standalone Lab-Tools: Audio-Spektrum/Wasserfall/Spektrogramm, BLE-Scanner, Netz-Scanner | — |
| 4 | Sub-GHz-Wasserfall: Firmware CC1101-RSSI-Sweep-Stream + App-Waterfall-Renderer | #1 |

Dieses Dokument spezifiziert **nur Sub-Projekt #1**. Es ist Voraussetzung für #2 und #4.

---

## Architektur (Sub-Projekt #1)

```
┌─────────────── iPhone (SwiftUI, nativ) ───────────────┐        ┌──── NM-RF-HAT / CYD (Bruce + Patch) ────┐
│  Screens:  [Verbindung] [Terminal] [Nav-Pad]          │        │                                          │
│                     │ nutzen nur ↓                     │        │   NimBLE  Nordic-UART-Service (NUS)      │
│              BruceLink (Protokoll)                     │        │     RX(write) ─▶ BleSerialStream ─▶      │
│         send(cmd) · output-Stream · state             │  BLE   │              parseSerialCommand()        │
│                     │                                  │◀──────▶│     TX(notify) ◀─ CLI-Ausgabe ◀─────     │
│              BLEManager (CoreBluetooth)                │  NUS   │                                          │
└───────────────────────────────────────────────────────┘        └──────────────────────────────────────────┘
```

Prinzip: **ein Kanal, ein Protokoll — Text rein, Text raus**, dieselbe CLI wie über USB.
`BruceLink` ist die einzige Naht: alle Screens hängen nur an ihr, nie direkt an
CoreBluetooth. Screens sind damit einzeln mit einem Mock-`BruceLink` testbar/previewbar.

---

## Firmware-Patch (Bruce-Fork)

Bruce nutzt NimBLE-Arduino und besitzt bereits eine serielle CLI (`SerialCli`,
`parseSerialCommand()` in `src/core/serialcmds.cpp`), die u.a. `ir`, `subghz`, `led`,
`gpio`, `i2c`, `badusb`, `js`, `info`, `nav`/`option` kennt (großteils Flipper-CLI-kompatibel).

**Komponenten des Patches:**

1. **Nordic-UART-Service (NUS)** in NimBLE:
   - Service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
   - RX-Characteristic `6E400002-…` (Write / Write-No-Response) — App → Gerät
   - TX-Characteristic `6E400003-…` (Notify) — Gerät → App
2. **`BleSerialStream : public Stream`** — Arduino-`Stream`-Adapter:
   - `available()` / `read()` / `peek()` liefern per BLE-RX empfangene Bytes aus einem Eingangs-Ringpuffer.
   - `write()` puffert Ausgangs-Bytes und flusht sie als TX-Notify, gestückelt auf die
     ausgehandelte MTU (Default 23 → 20 Nutzbytes; höhere MTU nutzen wenn verhandelt).
   - Dadurch sieht Bruces bestehende CLI-Schleife BLE **wie eine zweite serielle Konsole**.
3. **Einhängen in die CLI-Schleife:** Bruce pollt `serialDevice`. Der Patch registriert
   `BleSerialStream` als zusätzliche CLI-Quelle und leitet dieselben Zeilen in
   `parseSerialCommand()`.

**Integrationsrisiko — als ERSTES verifizieren:** Wohin schreibt `parseSerialCommand`
die *Ausgabe*? Erwartung: an das aktive `serialDevice`. Dann tauscht der Patch den
`serialDevice`-Zeiger für die Dauer eines BLE-Kommandos auf `BleSerialStream` (und zurück).
**Fallback**, falls Ausgabe fest an globales `Serial` geht: Ausgabe teen (Print-Wrapper,
der zusätzlich in `BleSerialStream` schreibt). Diese Frage wird vor allem anderen geklärt.

**Aktivierung & Sicherheit (Trust-Boundary):** Der Kanal fährt die volle CLI (inkl.
`badusb`, `gpio`) → offene Fernsteuer-Oberfläche.
- NUS wird **nur beworben/aktiv, wenn in den Bruce-Settings ein Toggle „BLE Remote" an ist.**
- **NimBLE-Bonding mit Passkey (verschlüsselte Verbindung)** als Default.
- Persistenz des Toggles über Bruces bestehende Settings/NVS.

**Deckel (bewusste Grenzen):**
- `// ponytail: control-BLE und Bruce-BLE-Angriffe schließen sich aus` — startet der Nutzer
  am Gerät einen BLE-Angriff (Spam/BadBLE), übernimmt dieser den NimBLE-Stack und die
  Fernsteuerung bricht ab, bis er endet. WiFi-/SubGHz-/IR-Angriffe stören nicht.
- Ein BLE-Zentralgerät (ein iPhone) gleichzeitig.

**Board-Target:** Exaktes CYD-PlatformIO-Env (Touch-/USB-Variante) wird aus der `.bin`
abgeleitet, die Leon aktuell flasht — **erster Bau-Task**. Build-Umgebung (PlatformIO/VSCode)
wird mit aufgesetzt (Leon hat bisher nur fertige `.bin` geflasht, nie selbst gebaut).

---

## App-Core (SwiftUI + CoreBluetooth)

- **BLEManager** (`CBCentralManager`): scannt gezielt nach der NUS-Service-UUID, verbindet,
  entdeckt RX/TX-Characteristics, abonniert TX-Notify. Auto-Reconnect aufs zuletzt
  verbundene Gerät (per `identifier` gespeichert).
- **BruceLink** (Protokoll — die tiefe Naht):
  - `func connect()` / `func disconnect()`
  - `func send(_ command: String)` — hängt `\n` an, zerlegt in MTU-Chunks, schreibt an RX.
  - `var output: AsyncStream<String>` — vollständige, wieder zusammengesetzte Zeilen.
  - `var state: ConnectionState` (`disconnected` / `scanning` / `connecting` / `connected`).
  - Verantwortlich für **MTU-Chunking** (senden) und **Zeilen-Reassembly** (Notifies kommen
    fragmentiert und ohne Zeilengrenzen-Garantie).
  - Konkrete Impl. `BleBruceLink`; Screens kennen nur das Protokoll → Mock für Previews/Tests.
- **Screen „Verbindung":** Scan-Liste gefundener Geräte, Connect/Disconnect, Statusanzeige,
  „letztes Gerät merken".
- **Screen „Terminal":** Monospace-Scrollback, Eingabefeld, Senden-Button, Befehls-History
  (Wiederholen vorheriger Kommandos). Deckt bereits „alles steuern" ab.
- **Screen „Nav-Pad":** D-Pad (hoch/runter/links/rechts/OK/zurück/home) → Bruces
  `nav`/`option`-Befehle; fährt Bruces eigene On-Screen-Menüs fern. Exakte `nav`-Tokens
  werden beim Bau aus Bruces Source verifiziert.

---

## Testing

- **Firmware-Loopback-Check:** BLE verbinden, `info` senden → nicht-leere Antwort über TX.
  Beweist die volle Kette RX → `parseSerialCommand` → TX.
- **App-Unit-Test:** `send` zerlegt einen Befehl korrekt in MTU-Chunks; ein über mehrere
  Notifies fragmentierter Antwort-Stream wird korrekt zu ganzen Zeilen reassembliert.
  Getestet gegen einen In-Memory-Fake statt echtem CoreBluetooth.
- **Screen-Previews:** laufen gegen Mock-`BruceLink` (deterministische Fake-Ausgabe).

---

## Ausdrücklich NICHT in Sub-Projekt #1 (spätere Specs)

- Feature-Screens SubGHz/IR/NFC/System/WiFi-Settings (#2).
- Standalone Audio-Spektrum/Wasserfall/Spektrogramm, BLE-/Netz-Scanner (#3).
- Sub-GHz-RSSI-Wasserfall inkl. Firmware-Sweep-Stream (#4).
- Mehrere gleichzeitige BLE-Clients, WiFi-Fallback-Transport.

---

## Erste Implementierungs-Reihenfolge (Vorschau)

1. Exaktes CYD-PlatformIO-Env bestimmen, Bruce-Build-Umgebung aufsetzen, unveränderten Bruce bauen & flashen (Baseline).
2. **Output-Routing in `parseSerialCommand` verifizieren** (Zeiger-Swap vs. Tee entscheiden).
3. `BleSerialStream` + NUS im Fork, `info`-Loopback grün.
4. Settings-Toggle „BLE Remote" + Passkey-Bonding.
5. App: `BLEManager` + `BleBruceLink` (Chunking/Reassembly) + Unit-Test.
6. App-Screens: Verbindung → Terminal → Nav-Pad.
