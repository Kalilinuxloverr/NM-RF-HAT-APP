// NMRF-HAT BLE-Remote-Bridge — Fork-Overlay für Bruce Release 1.16.
// v2: + Ausgabe-Echo (CLI-Output -> NUS-TX) + Stealth (Display aus).
// Verifiziert gegen: core/serialcmds.h (parseSerialCommand), include/globals.h
// (serialDevice/USBserial/bruceConfig), include/SerialDevice.h, core/settings.h,
// core/display.h und NimBLE-Arduino @2.5.
// Ablage: src/modules/ble/ble_remote.{h,cpp}
#include <NimBLEDevice.h>
#include <globals.h>          // serialDevice, USBserial, bruceConfig, BLEConnected
#include "core/serialcmds.h"  // bool parseSerialCommand(const String&, bool);
#include "core/settings.h"    // setBrightness(uint8_t, bool)
#include "core/display.h"     // turnOffDisplay()
#include "core/utils.h"        // touchHeatMap
#include "modules/NRF24/nrf_jammer.h"  // nrfSetJamPA
#include "ble_remote.h"
#include <cstring>

// Nordic UART Service
#define NUS_SVC "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX  "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // App -> HAT (Write / Write-NR)
#define NUS_TX  "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // HAT -> App (Notify)

static NimBLECharacteristic *txChar = nullptr;
static String rxbuf;
static bool started = false;
static SerialDevice *savedSerial = nullptr;

// --- HAT -> App: CLI-Ausgabe als Notify (MTU-gestückelt) ---
static void nusSend(const uint8_t *data, size_t len) {
    if (!txChar || len == 0) return;
    const size_t chunk = 180;  // sicher unter ausgehandelter MTU
    size_t off = 0;
    while (off < len) {
        size_t n = (len - off < chunk) ? (len - off) : chunk;
        txChar->setValue(data + off, n);
        txChar->notify();
        off += n;
        delay(2);
    }
}
static void nusSendStr(const char *s) { nusSend((const uint8_t *)s, strlen(s)); }

// SerialDevice-Adapter: spiegelt CLI-Ausgabe in die App. Eingabe kommt über RX (RxCb),
// daher read/available/readStringUntil leer.
class NusSerialDevice : public SerialDevice {
public:
    size_t println(const String &s) override { nusSendStr(s.c_str()); nusSendStr("\r\n"); return s.length(); }
    size_t print(const String &s) override { nusSendStr(s.c_str()); return s.length(); }
    size_t println() override { nusSendStr("\r\n"); return 0; }
    size_t println(size_t n) override { String s(n); nusSendStr(s.c_str()); nusSendStr("\r\n"); return s.length(); }
    size_t println(uint32_t n) override { String s(n); nusSendStr(s.c_str()); nusSendStr("\r\n"); return s.length(); }
    size_t println(int n, int format) override { String s(n, format); nusSendStr(s.c_str()); nusSendStr("\r\n"); return s.length(); }
    size_t print(int n, int format) override { String s(n, format); nusSendStr(s.c_str()); return s.length(); }
    void vprintf(const char *fmt, va_list args) override {
        char b[256];
        int m = vsnprintf(b, sizeof(b), fmt, args);
        if (m > 0) nusSend((const uint8_t *)b, (size_t)(m < (int)sizeof(b) ? m : (int)sizeof(b) - 1));
    }
    int read() override { return -1; }
    size_t write(uint8_t *str, size_t size) override { nusSend(str, size); return size; }
    void flush() override {}
    int available() override { return 0; }
    String readStringUntil(char) override { return String(); }
};
static NusSerialDevice nusSerial;

// --- App -> HAT: eingehende Zeilen in Bruces Command-Queue schieben ---
class RxCb : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic *c, NimBLEConnInfo &info) override {  // NimBLE 2.5: 2 Args
        rxbuf += c->getValue().c_str();
        int nl;
        while ((nl = rxbuf.indexOf('\n')) >= 0) {
            String line = rxbuf.substring(0, nl);
            rxbuf.remove(0, nl + 1);
            line.trim();
            if (!line.length()) continue;
            // Stealth-Sonderbefehle (nicht an die CLI weiterreichen)
            String low = line;
            low.toLowerCase();
            if (low == "stealth" || low == "stealth on") { turnOffDisplay(); continue; }
            if (low == "stealth off" || low == "light" || low == "wake") {
                setBrightness(bruceConfig.bright, false);
                continue;
            }
            if (low == "mirror on") { tft.startAsyncSerial(); continue; }
            if (low == "mirror off") { tft.stopAsyncSerial(); continue; }
            if (low.startsWith("touch ")) {
                int sp = line.indexOf(" ", 6);
                if (sp > 0) {
                    touchPoint.x = (uint16_t)line.substring(6, sp).toInt();
                    touchPoint.y = (uint16_t)line.substring(sp + 1).toInt();
                    touchPoint.pressed = true;
                    AnyKeyPress = true;
                    touchHeatMap(touchPoint);
                }
                continue;
            }
            if (low.startsWith("jampa ")) { nrfSetJamPA((int)line.substring(6).toInt()); continue; }
            parseSerialCommand(line, false);  // in Bruces cmdQueue, nicht blockierend
        }
    }
};

class SrvCb : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer *s, NimBLEConnInfo &i) override {
        BLEConnected = true;
        savedSerial = serialDevice;
        serialDevice = &nusSerial;  // CLI-Ausgabe in die App spiegeln
    }
    void onDisconnect(NimBLEServer *s, NimBLEConnInfo &i, int reason) override {
        BLEConnected = false;
        serialDevice = savedSerial ? savedSerial : &USBserial;  // USB zurück
        tft.stopAsyncSerial();
        rxbuf = "";
        NimBLEDevice::startAdvertising();
    }
};

void bleRemoteStart() {
    if (started) return;
    if (NimBLEDevice::isInitialized()) return;  // koexistiert mit Bruce-BLE-Angriffen

    NimBLEDevice::init("NMRF-HAT");
    // Passkey-Bonding optional (Laborbetrieb: aus, App verbindet ohne Bonding):
    // NimBLEDevice::setSecurityAuth(true, true, true);
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
    if (savedSerial) serialDevice = savedSerial;
    NimBLEDevice::stopAdvertising();
    NimBLEDevice::deinit(true);
    txChar = nullptr;
    started = false;
    BLEConnected = false;
}

bool bleRemoteActive() { return started; }

// v2b: nRF24-Spektrum als "SPEC:v0,..,vN"-Zeile an die App streamen (Kanalwerte 0..100).
void bleRemoteStreamSpectrum(const uint8_t *ch, int n) {
    if (!started || !BLEConnected || txChar == nullptr) return;
    String s = "SPEC:";
    for (int i = 0; i < n; i++) {
        if (i) s += ',';
        s += (int)ch[i];
    }
    s += '\n';
    nusSend((const uint8_t *)s.c_str(), s.length());
}
