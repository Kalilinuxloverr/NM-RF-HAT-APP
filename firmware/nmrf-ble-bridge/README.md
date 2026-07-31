# NMRF-HAT — BLE-Remote-Bridge (Bruce-Fork-Overlay, Basis 1.16)

Macht Bruces serielle CLI über **BLE (Nordic UART Service)** erreichbar, damit die
Android-App `NMRFRemote` den NM-RF-HAT fernsteuert — ohne WLAN, ohne USB.

## Basis: Bruce Release `1.16`
Enthält den **funktionierenden nRF24-Jammer** (Fix-Commit `7839a7c7c`) samt modernisiertem
Menü. Hintergrund: das schöne UI kam in **1.15** (PRs #2147/#2194), brach dort aber den
Jammer (Issues **#2576/#2475/#2326**: nRF24 wurde nicht neu power-cycled, der Redraw
hungerte die Jam-Schleife aus). 1.16 rollte die Jammer-Internals zurück und behielt das Menü.

## Ansatz (verifiziert gegen die 1.16-Quelle)
Bruce hat eine **FreeRTOS-Queue-CLI**: `bool parseSerialCommand(const String&, bool)` legt
Befehle in `cmdQueue`. Unsere RX-Callback ruft `parseSerialCommand(line, false)` → nicht
blockierend, kein Pointer-Swap nötig. NimBLE-Arduino ist auf **@2.5** gepinnt → 2-arg-Callbacks.

- `ble_remote.h/.cpp` — NUS-Service + RX-Injektion + (Re-)Advertising. Kompiliert gegen 1.16.
- `INTEGRATION.md` — exakte Schritte: Ablage, Config-Toggle, settings.cpp, main.cpp, CYD-Env, Sicherheit.

## Transport
NUS: Service `6E400001-…`, RX(write) `6E400002-…`, TX(notify) `6E400003-…`.

## Status
- **v1 (dieses Overlay):** App → HAT (Befehle senden, Menüs per `nav` fernsteuern). Kompiliert.
- **v2:** HAT → App Ausgabe-Echo (SerialDevice-Unterklasse → NUS-TX). Siehe INTEGRATION.md.

Flashen nur auf der Hardware (ESP32-2432S028 + PlatformIO): `pio run -e CYD-2432S028 -t upload`.
