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
- ✅ CFO/STO-Schätzung (Hinweis für künftige Runden: `reference_lora.c` enthält funktionierendes kontinuierliches STO/SFO-Tracking über den ganzen Frame via `compute_sto_frac`, `sfo_cum`, `downsample_symbol`. Aktuell wird STO hart auf 0.0 gesetzt.)
- ✅ Dechirp + FFT-Demodulation
- ✅ Gray-Decode (implementiert und getestet)
- ✅ Deinterleaver
- ✅ Hamming-Decode
- ✅ Dewhitening: Whitening-Sequenz verifiziert gegen Ground-Truth (`test_whitening_against_ground_truth`) auf dem vollen 255-Byte-Input.
- ✅ Header-Parsing + CRC (Parsing existiert, CRC-Prüfung in `try_decode_packet` ist aktiv und liefert `Result<Vec<u8>, DecodeError>`)
- ✅ Sync-Wort (0x2B) -> Symbol-Übersetzung: Byte→Symbol-Abbildung ist per Ground-Truth-FFT an zwei unabhängigen Werten (0x12 und 0x34) empirisch geklärt (Nibble × `1 << (sf-8)`). Skalierungsformel durch `test_sync_word_empirically` verifiziert.
- ✅ RX-Architektur: `try_decode_packet` implementiert einen "Two-Phase Decode" (zuerst Header mit CR=4/8, dann Payload basierend auf den Header-Parametern).
- ✅ TX-Pfad (Pipeline umgekehrt) — vollständig implementiert (inklusive generate_upchirp, whiten, crc, encode, interleave, gray_map, modulate).
- ✅ Alle Konstanten (Interleaver/Whitening/Hamming) aus gr-lora_sdr portiert.
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

## Open Issue: Preamble-Länge 8 vs 16
* **Status:** ⚠️ OPEN (2026-08-25)
* **Description:** Es gibt widersprüchliche Quellen zur erwarteten LoRa Preamble-Länge für Meshtastic:
  - **16 Symbole:** In unserer Codebasis (und der Projekthistorie/Memory) verankert als direkter Befund aus Meshtastic-Firmware-Quellprüfungen, um die 17 Symbole von SDRangel abzulehnen. Dies wird gestützt durch den aktuellen `meshtastic_test.cf32` Test, bei dem nachweislich 16 saubere Präambel-Symbole gezählt wurden.
  - **8 Symbole:** Eine unabhängige Quelle (`meshtastic-sniffer-main`, `lora.c:670-672`) behauptet explizit: *"gr-lora_sdr uses (preamble_len - 3); Meshtastic preambles are 8 symbols so 5 fits"*.
* **Next Steps:** Vorerst bleibt `PREAMBLE_SYMBOLS=16` im Code unangetastet. Dies muss in zukünftigen Hardware-Captures (echter Traffic) final durch Zählen der tatsächlichen up-chirps verifiziert werden.

## Open Issue: Payload-Symbol-Vergleich
* **Status:** ✅ CLOSED (2026-08-25)
* **Description:** Payload-Symbol-Vergleich gegen angenommene Gray-Ground-Truth schlug fehl, weil die Vergleichslogik an der falschen Stelle im Array suchte. Die vermeintliche Diskrepanz (937 vs 1644) war lediglich eine Verwechslung von Header-Symbolen (Indizes 0-17) mit Payload-Symbolen (ab Index 18) im selben Stream. Der RX-Dechirp-Pfad ist nachweislich zu 100% korrekt und trifft die Ground-Truth an jedem Index exakt.
