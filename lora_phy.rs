//! LoRa PHY (Chirp Spread Spectrum) software demodulator/modulator — SKELETON ONLY.
//!
//! STATUS: structural pipeline, not a working demodulator. Stage order and
//! function signatures reflect the published architecture (Tapparel et al.,
//! EPFL, "An Open-Source LoRa Physical Layer Prototype on GNU Radio", and the
//! follow-on gr-lora_sdr project). The DSP constants that actually make this
//! decode real signals — interleaver pattern, whitening LFSR seed/polynomial,
//! Hamming generator matrix, sync-word/preamble thresholds — are deliberately
//! left as `todo!()`. They are not something to reconstruct from memory or
//! guess plausibly: port them from gr-lora_sdr
//! (github.com/tapparelj/gr-lora_sdr, GPL-3.0) as parameter VALUES, not
//! copied code (GPL-3.0 is not compatible with silently vendoring into a
//! differently-licensed app) — then validate stage-by-stage against real
//! HackRF/RTL-SDR captures of known LongFast traffic.
//!
//! Even EPFL's own 2024 paper on this states plainly that some LoRa PHY
//! reverse-engineering details are still not fully settled industry-wide.
//! Treat this file as a research task with a hardware-in-the-loop feedback
//! loop, not a spec-to-code implementation task.
//!
//! RECOMMENDED ORDER: get RX solid first — decode real over-the-air LongFast
//! traffic and cross-check against a real Meshtastic node/app running
//! alongside as ground truth. Only attempt TX once RX reliably decodes real
//! signals; transmitting before that just puts malformed energy on a shared
//! ISM band (868 MHz in the EU — SRD860 duty-cycle/power rules under
//! ETSI EN 300 220 apply, license-exempt but not rules-exempt) for no benefit.

use num_complex::Complex32;

pub struct LoraConfig {
    pub spreading_factor: u8, // SF7..SF12; Meshtastic "LongFast" preset = SF11
    pub bandwidth_hz: u32,    // e.g. 250_000
    pub coding_rate: u8,      // 4/5 .. 4/8
    pub freq_hz: u64,         // e.g. 868_125_000 for EU868 LongFast
}

pub struct IqBuffer<'a> {
    pub samples: &'a [Complex32],
    pub sample_rate_hz: u32,
}

/// Stage 1 — find the start of a LoRa preamble (repeated upchirps) in a raw
/// IQ stream. TODO: port detection/threshold logic from gr-lora_sdr's sync block.
pub fn detect_preamble(_iq: &IqBuffer, _cfg: &LoraConfig) -> Option<usize> {
    todo!("port preamble detection from gr-lora_sdr sync block")
}

/// Stage 2 — estimate + correct carrier frequency offset (CFO) and sample
/// timing offset (STO) using the detected preamble.
/// TODO: port from gr-lora_sdr / the Tapparel et al. paper's sync stage.
pub fn estimate_cfo_sto(
    _iq: &IqBuffer,
    _preamble_start: usize,
    _cfg: &LoraConfig,
) -> (f32, f32) {
    todo!("port CFO/STO estimation from gr-lora_sdr")
}

/// Stage 3 — dechirp each symbol: multiply by the conjugate reference
/// (down-)chirp, FFT, the peak bin is the raw symbol value.
/// TODO: port exact windowing/FFT-size handling from gr-lora_sdr.
pub fn dechirp_symbols(_iq: &IqBuffer, _cfg: &LoraConfig) -> Vec<u16> {
    todo!("port dechirp + FFT demodulation from gr-lora_sdr")
}

/// Stage 4 — Gray demapping of raw dechirped symbol values.
/// This step itself is standard Gray-code math once the symbol values coming
/// in are confirmed correct — the risk is entirely upstream (stages 1-3).
pub fn gray_demap(_raw_symbols: &[u16]) -> Vec<u16> {
    todo!("standard gray decode, safe to implement directly — verify with a known symbol vector first")
}

/// Stage 5 — deinterleave.
/// TODO: exact interleaver pattern must come from gr-lora_sdr, not a guess —
/// getting this subtly wrong produces plausible-looking noise, not an
/// obvious error, which burns debugging time on the wrong stage.
pub fn deinterleave(_symbols: &[u16], _cfg: &LoraConfig) -> Vec<u8> {
    todo!("port exact interleaver pattern from gr-lora_sdr")
}

/// Stage 6 — Hamming FEC decode (rate depends on coding_rate 4/5..4/8).
/// TODO: generator matrix / decode tables from gr-lora_sdr.
pub fn hamming_decode(_bits: &[u8], _coding_rate: u8) -> Vec<u8> {
    todo!("port Hamming decode from gr-lora_sdr")
}

/// Stage 7 — dewhitening (XOR with a known PRBS sequence).
/// TODO: exact whitening LFSR seed/polynomial from gr-lora_sdr.
pub fn dewhiten(_bytes: &[u8]) -> Vec<u8> {
    todo!("port whitening sequence from gr-lora_sdr")
}

/// Stage 8 — parse the LoRa PHY header (implicit/explicit mode) and verify CRC.
/// TODO: header bit layout + CRC polynomial from gr-lora_sdr / LoRa
/// reverse-engineering literature.
pub fn parse_header_and_check_crc(_bytes: &[u8]) -> Option<Vec<u8>> {
    todo!("port header parsing + CRC check from gr-lora_sdr")
}

/// Top-level RX entry point wiring the stages above in order. Once this
/// returns Some(bytes), hand them to packet::decode_mesh_packet() and, if
/// the channel is encrypted, crypto::crypt_payload() (see crypto.rs) to get
/// a readable Meshtastic message.
pub fn try_decode_packet(iq: &IqBuffer, cfg: &LoraConfig) -> Option<Vec<u8>> {
    let start = detect_preamble(iq, cfg)?;
    let (_cfo, _sto) = estimate_cfo_sto(iq, start, cfg);
    let raw_symbols = dechirp_symbols(iq, cfg);
    let symbols = gray_demap(&raw_symbols);
    let bits = deinterleave(&symbols, cfg);
    let fec_decoded = hamming_decode(&bits, cfg.coding_rate);
    let dewhitened = dewhiten(&fec_decoded);
    parse_header_and_check_crc(&dewhitened)
}

// TX direction mirrors this pipeline in reverse (whiten -> Hamming encode ->
// interleave -> Gray map -> chirp modulate + upsample). Deliberately not
// sketched here: get RX verified against real captures first. HackRF is the
// TX-capable path (RTL-SDR is RX-only hardware, so it never needs this half).
