# MeshSDR — To-Do (Stand: 2026-08-02)

Alles hier basiert auf dem, was in diesem Chat tatsächlich geprüft/gebaut/besprochen
wurde — nichts Neues erfunden. ✅ = verifiziert/fertig, ⚠️ = teilweise / braucht
Prüfung, ❌ = noch nicht angefangen.

## 0 — Grundlage (bevor irgendwas testbar ist)
- ❌ Echtes Android-Studio-Projekt anlegen (`app`-Modul + `rust_core`-Modul)
- ❌ cargo-ndk + JNI-Setup einrichten (analog RFCut)
- ❌ RFCuts `HackRfRepository`, `RtlSdrRepository`, `RtlSdrNative`-JNI-Bridge,
  `rtlSdrMutex`-Guard 1:1 übernehmen — nicht neu schreiben
- ❌ `hackrf_android` (demantz/hackrf_android) als Dependency einbinden, wie in RFCut
- ❌ `meshtastic/protobufs` als Submodule/vendored einbinden + `prost-build` in `build.rs`

## Krypto & Pakete (`crypto.rs`, `packet.rs`) — größtenteils fertig
- ✅ AES-256-CTR-Mechanik
- ✅ Channel-Hash (`xorHash`/`generateHash`) — gegen echten Firmware-Quellcode verifiziert
- ✅ IV/Nonce-Konstruktion (`CryptoEngine::initNonce`) — doppelt gegenbestätigt
- ⚠️ Letztes Byte des Default-PSK-Arrays klären — zwei Quellen widersprechen sich
  (`0xbf` vs. `0x01`), gegen aktuellen `master`-Branch von `Channels.cpp` prüfen
- ⚠️ `packet.rs`-Feldnamen (`packet.from`, `.id`, `.channel`, `PayloadVariant::…`)
  gegen den echten generierten Code abgleichen, sobald `prost-build` läuft

## LoRa-PHY (`lora_phy.rs`) — das Kernstück, faktisch bei 0%
- ❌ Preamble-Erkennung
- ❌ CFO/STO-Schätzung
- ❌ Dechirp + FFT-Demodulation
- ❌ Gray-Decode (unkritisch, direkt implementierbar sobald Symbole korrekt reinkommen)
- ❌ Deinterleaver — exakte Tabelle nötig
- ❌ Hamming-Decode — Generatormatrix nötig
- ❌ Dewhitening — LFSR-Sequenz nötig
- ❌ Header-Parsing + CRC
- ❌ TX-Pfad (Pipeline umgekehrt) — noch nicht mal skizziert
- Alle Konstanten (Interleaver/Whitening/Hamming) aus gr-lora_sdr (EPFL,
  GPL-3.0) als **Parameterwerte** portieren, nicht Code kopieren
- Reihenfolge: RX zuerst gegen echte HackRF-Aufnahmen von bekanntem
  LongFast-Sender testen (Vergleich mit echtem Meshtastic-Node/-App als
  Referenz), TX erst danach versuchen — braucht Hardware-Feedback-Loop,
  kann realistisch nicht blind fertiggestellt werden

## Integration
- ❌ `lib.rs`: `pushIqSamples`-JNI-Funktion schreiben (verbindet lora_phy + crypto
  + packet), `catch_panic()`-gewrappt wie RFCuts andere 17 Einstiegspunkte
- ⚠️ `MeshSdrService.kt`: `HackRfRepositoryLike`-Platzhalter durch RFCuts echtes
  Repository ersetzen, echte Methodennamen prüfen (aktuell nur aus Audit-Historie
  plausibel angenommen)
- ⚠️ `TacticalMainScreen.kt`: Platzhalter-Fonts (`FontFamily.SansSerif`) durch
  echte `.ttf` in `res/font/` ersetzen, Package-Namen an echtes Projekt anpassen
- ❌ Weitere Screens im Aussehen-#2-Look: Nachrichten-Thread, Kanal-/PSK-Einstellungen
- ❌ Echtes Notification-Icon statt Platzhalter
- ⚠️ `AndroidManifest.xml`: Service mit `foregroundServiceType="connectedDevice"` —
  gegen aktuelle Android-Zielversion prüfen (FGS-Anforderungen ändern sich häufig)
- ❌ Frequenz/Region konfigurierbar machen statt hardcoded 868.125 MHz

## Noch gar nicht angefangen
- ❌ Node-Datenbank/Persistenz (Room, wie RFCut)
- ❌ USB-Berechtigungsdialog (RFCuts bestehendes Pattern übernehmen)
- ❌ Android 13+ Notification-Runtime-Permission
- ❌ RTL-SDR-RX-only-Pfad in den Service einbauen (bisher nur HackRF verdrahtet,
  RTL-SDR war Teil der ursprünglichen Anforderung)
- ❌ Flood-Routing, falls "vollwertiger" Routing-Node gewünscht (aktuell nur
  Senden/Empfangen eigener Nachrichten geplant)

## Kann in dieser Sandbox grundsätzlich nicht passieren
- ❌ Kompilieren (kein Rust/Gradle/Android-SDK hier verfügbar)
- ❌ Hardware-Test gegen echtes HackRF / echten Meshtastic-Traffic
- ❌ TX-Regelkonformität EU868 SRD860 (Duty-Cycle/Leistung nach ETSI EN 300 220)
  praktisch umsetzen — lizenzfrei, aber nicht regelfrei

## Im Hinterkopf behalten
- gr-lora_sdr ist GPL-3.0 — nur Parameterwerte portieren, nicht Code kopieren
  (gleiche Überlegung wie bei der rtl_433-Entscheidung in RFCut)
