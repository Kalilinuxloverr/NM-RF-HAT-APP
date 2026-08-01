// NMRF-HAT BLE-Remote-Bridge für Bruce 1.16 — NUS -> serielle CLI.
#pragma once
void bleRemoteStart();  // NUS aufsetzen + advertisen (nur wenn NimBLE frei ist)
void bleRemoteStop();   // NUS/Stack abbauen
bool bleRemoteActive();

void bleRemoteStreamSpectrum(const uint8_t *ch, int n);  // nRF24-Spektrum -> App
