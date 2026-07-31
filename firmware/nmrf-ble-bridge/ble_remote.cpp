// NMRF-HAT BLE-Remote-Bridge — Fork-Overlay für Bruce Release 1.16.
// Verifiziert gegen: src/core/serialcmds.h (parseSerialCommand), include/globals.h
// (BLEConnected) und NimBLE-Arduino @2.5 (2-arg-Callbacks).
//
// Ablage: src/modules/ble/ble_remote.{h,cpp} (PlatformIO globbt src/** automatisch).
#include <NimBLEDevice.h>
#include <globals.h>          // extern bool BLEConnected;
#include "core/serialcmds.h"  // bool parseSerialCommand(const String&, bool waitForResponse);
#include "ble_remote.h"

// Nordic UART Service
#define NUS_SVC "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX  "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // App -> HAT (Write / Write-NR)
#define NUS_TX  "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // HAT -> App (Notify)

static NimBLECharacteristic *txChar = nullptr;
static String rxbuf;
static bool started = false;

// --- App -> HAT: eingehende Zeilen in Bruces Command-Queue schieben ---
class RxCb : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic *c, NimBLEConnInfo &info) override {  // NimBLE 2.5: 2 Args
        rxbuf += c->getValue().c_str();
        int nl;
        while ((nl = rxbuf.indexOf('\n')) >= 0) {
            String line = rxbuf.substring(0, nl);
            rxbuf.remove(0, nl + 1);
            line.trim();
            // waitForResponse=false: nur enqueuen, blockiert den NimBLE-Host-Task nicht.
            if (line.length()) parseSerialCommand(line, false);
        }
    }
};

class SrvCb : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer *s, NimBLEConnInfo &i) override { BLEConnected = true; }
    void onDisconnect(NimBLEServer *s, NimBLEConnInfo &i, int reason) override {
        BLEConnected = false;
        rxbuf = "";
        NimBLEDevice::startAdvertising();  // wieder auffindbar
    }
};

void bleRemoteStart() {
    if (started) return;
    // Coexist: hält ein Bruce-BLE-Angriff/-Scan den Stack, nicht kollidieren.
    if (NimBLEDevice::getInitialized()) return;

    NimBLEDevice::init("NMRF-HAT");

    // --- Passkey-Bonding (verschlüsselt) — Laborbetrieb: standardmäßig AUS, da die App
    //     ohne Pairing verbindet. Zum Härten einkommentieren und die App aufs Bonding
    //     erweitern; RX/TX dann auf *_ENC-Properties setzen. ---
    // NimBLEDevice::setSecurityAuth(true, true, true);   // bonding, MITM, SC
    // NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_ONLY);
    // NimBLEDevice::setSecurityPasskey(123456);

    NimBLEServer *srv = NimBLEDevice::createServer();
    srv->setCallbacks(new SrvCb());

    NimBLEService *svc = srv->createService(NUS_SVC);
    txChar = svc->createCharacteristic(NUS_TX, NIMBLE_PROPERTY::NOTIFY);
    NimBLECharacteristic *rx =
        svc->createCharacteristic(NUS_RX, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
    rx->setCallbacks(new RxCb());
    svc->start();

    NimBLEAdvertising *adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(NUS_SVC);
    adv->setName("NMRF-HAT");
    adv->start();

    started = true;
}

void bleRemoteStop() {
    if (!started) return;
    NimBLEDevice::stopAdvertising();
    NimBLEDevice::deinit(true);
    txChar = nullptr;
    started = false;
    BLEConnected = false;
}

bool bleRemoteActive() { return started; }

// Ausgabe HAT -> App (v2): CLI-Output über BLE spiegeln. Bruce schreibt Output an das
// abstrakte `serialDevice` (include/SerialDevice.h). Dazu eine SerialDevice-Unterklasse
// bauen, die in txChar->notify() schreibt, und bei Connect `serialDevice = &bleDev` setzen
// (bei Disconnect zurück auf USB). Siehe INTEGRATION.md. (Ein paar Log-Zeilen in
// serialcmds.cpp gehen fest an USB-Serial und werden nicht gespiegelt.)
