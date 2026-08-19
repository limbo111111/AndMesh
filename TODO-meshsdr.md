# MeshSDR — To-Do (Aktualisierter Status)

Alles hier basiert auf dem, was in der Codebasis tatsächlich geprüft/gebaut/vorhanden ist — nichts Neues erfunden. ✅ = verifiziert/fertig, ⚠️ = teilweise / braucht Prüfung oder Rework, ❌ = noch nicht angefangen.

## 0 — Grundlage (bevor irgendwas testbar ist)
- ✅ Echtes Android-Studio-Projekt anlegen (`app`-Modul + `rust_core`-Modul)
- ✅ cargo-ndk + JNI-Setup einrichten (analog RFCut)
- ✅ RFCuts `HackRfRepository`, `RtlSdrRepository`, `RtlSdrNative`-JNI-Bridge,
  `rtlSdrMutex`-Guard 1:1 übernehmen — nicht neu schreiben
- ✅ `hackrf_android` (demantz/hackrf_android) als Dependency einbinden, wie in RFCut
- ✅ `meshtastic/protobufs` als Submodule/vendored einbinden + `prost-build` in `build.rs`

## Krypto & Pakete (`crypto.rs`, `packet.rs`) — fertig
- ✅ AES-256-CTR-Mechanik
- ✅ Channel-Hash (`xorHash`/`generateHash`) — gegen echten Firmware-Quellcode verifiziert
- ✅ IV/Nonce-Konstruktion (`CryptoEngine::initNonce`) — doppelt gegenbestätigt
- ✅ Letztes Byte des Default-PSK-Arrays geklärt — 0x01 ist verifiziert.
- ✅ `packet.rs`-Feldnamen (`packet.from`, `.id`, `.channel`, `PayloadVariant::…`)
  gegen den echten generierten Code abgeglichen (prost-build läuft erfolgreich).

## LoRa-PHY (`lora_phy.rs`) — Grundsteine RX gelegt, TX fehlt
- ✅ Preamble-Erkennung
- ✅ CFO/STO-Schätzung
- ✅ Dechirp + FFT-Demodulation
- ✅ Gray-Decode (implementiert und getestet)
- ✅ Deinterleaver
- ✅ Hamming-Decode
- ✅ Dewhitening
- ✅ Header-Parsing + CRC (Parsing existiert und CRC-Prüfung in `try_decode_packet` ist aktiv, liefert `None` bei Fehler)
- ✅ RX-Architektur: `try_decode_packet` implementiert einen "Two-Phase Decode" (zuerst Header mit CR=4/8, dann Payload basierend auf den Header-Parametern).
- ✅ TX-Pfad (Pipeline umgekehrt) — vollständig implementiert (inklusive generate_upchirp, whiten, crc, encode, interleave, gray_map, modulate).
- Alle Konstanten (Interleaver/Whitening/Hamming) aus gr-lora_sdr portiert.
- Reihenfolge: RX zuerst gegen echte HackRF-Aufnahmen testen, TX erst danach versuchen.

## Integration & UI
- ✅ `lib.rs`: JNI-Brücke ist vollständig, dekodierte JSON-Daten werden per JNI an `RtlSdrNative.onPacketDecoded` an die Kotlin-UI weitergegeben (inklusive Position, User/NodeInfo, Telemetrie).
- ✅ `MeshSdrService.kt`: `HackRfRepository`, `AppDatabase`, `MeshRepository` und `MeshRouter` sind eingebunden und initialisiert.
- ✅ `TacticalMainScreen.kt`: Nutzt jetzt dynamische Daten (Nodes/Messages) via `TacticalViewModel` und Room Flow.
- ✅ `TacticalNodeDetailScreen.kt`: Vollwertiger Tactical-Detail-Bildschirm für Stationen (GPS-Koordinaten, Telemetrie/Akku/Spannung, Hardware-Modell, direkte Chat-History, Favoriten-Toggle und Notizen).
- ✅ Kanal-/PSK- & Routing-Einstellungen (`TacticalSettingsScreen` bietet Frequenz, Kanal/PSK-Eingabe und Mesh-Relay/Repeater Toggle).
- ✅ Echtes Notification-Icon (`ic_mesh_notification`).
- ✅ `AndroidManifest.xml`: Service mit `foregroundServiceType="connectedDevice"` und USB-Host-Features sind eingerichtet.
- ✅ Frequenz/Region konfigurierbar machen (JNI und HackRfRepository unterstützen dynamische Frequenz).

## Persistenz & Routing
- ✅ Node-Datenbank/Persistenz (Room implementiert über `AppDatabase`, `NodeDao`, `MessageDao`, `NodeEntity`, `MessageEntity`, `MeshRepository`).
- ✅ Flood-Routing & Deduplizierung (implementiert über `MeshRouter` mit LRU-Cache, Hop-Dekrementierung und Jitter-Queue).
- ✅ USB-Berechtigungsdialog (via `hackrf_android` / Manifest intent-filter).
- ✅ Android 13+ Notification-Runtime-Permission

## Kann in dieser Sandbox grundsätzlich nicht passieren
- ❌ Hardware-Test gegen echtes HackRF / echten Meshtastic-Traffic
- ❌ TX-Regelkonformität EU868 SRD860 (Duty-Cycle/Leistung nach ETSI EN 300 220)
  praktisch umsetzen — lizenzfrei, aber nicht regelfrei
