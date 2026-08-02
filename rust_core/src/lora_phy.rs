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


// Ported from gr-lora_sdr (EPFL, GPL-3.0) tables.h: whitening_seq
pub const WHITENING_SEQ: [u8; 255] = [
    0xFF, 0xFE, 0xFC, 0xF8, 0xF0, 0xE1, 0xC2, 0x85, 0x0B, 0x17, 0x2F, 0x5E, 0xBC, 0x78, 0xF1, 0xE3,
    0xC6, 0x8D, 0x1A, 0x34, 0x68, 0xD0, 0xA0, 0x40, 0x80, 0x01, 0x02, 0x04, 0x08, 0x11, 0x23, 0x47,
    0x8E, 0x1C, 0x38, 0x71, 0xE2, 0xC4, 0x89, 0x12, 0x25, 0x4B, 0x97, 0x2E, 0x5C, 0xB8, 0x70, 0xE0,
    0xC0, 0x81, 0x03, 0x06, 0x0C, 0x19, 0x32, 0x64, 0xC9, 0x92, 0x24, 0x49, 0x93, 0x26, 0x4D, 0x9B,
    0x37, 0x6E, 0xDC, 0xB9, 0x72, 0xE4, 0xC8, 0x90, 0x20, 0x41, 0x82, 0x05, 0x0A, 0x15, 0x2B, 0x56,
    0xAD, 0x5B, 0xB6, 0x6D, 0xDA, 0xB5, 0x6B, 0xD6, 0xAC, 0x59, 0xB2, 0x65, 0xCB, 0x96, 0x2C, 0x58,
    0xB0, 0x61, 0xC3, 0x87, 0x0F, 0x1F, 0x3E, 0x7D, 0xFB, 0xF6, 0xED, 0xDB, 0xB7, 0x6F, 0xDE, 0xBD,
    0x7A, 0xF5, 0xEB, 0xD7, 0xAE, 0x5D, 0xBA, 0x74, 0xE8, 0xD1, 0xA2, 0x44, 0x88, 0x10, 0x21, 0x43,
    0x86, 0x0D, 0x1B, 0x36, 0x6C, 0xD8, 0xB1, 0x63, 0xC7, 0x8F, 0x1E, 0x3C, 0x79, 0xF3, 0xE7, 0xCE,
    0x9C, 0x39, 0x73, 0xE6, 0xCC, 0x98, 0x31, 0x62, 0xC5, 0x8B, 0x16, 0x2D, 0x5A, 0xB4, 0x69, 0xD2,
    0xA4, 0x48, 0x91, 0x22, 0x45, 0x8A, 0x14, 0x29, 0x52, 0xA5, 0x4A, 0x95, 0x2A, 0x54, 0xA9, 0x53,
    0xA7, 0x4E, 0x9D, 0x3B, 0x77, 0xEE, 0xDD, 0xBB, 0x76, 0xEC, 0xD9, 0xB3, 0x67, 0xCF, 0x9E, 0x3D,
    0x7B, 0xF7, 0xEF, 0xDF, 0xBF, 0x7E, 0xFD, 0xFA, 0xF4, 0xE9, 0xD3, 0xA6, 0x4C, 0x99, 0x33, 0x66,
    0xCD, 0x9A, 0x35, 0x6A, 0xD4, 0xA8, 0x51, 0xA3, 0x46, 0x8C, 0x18, 0x30, 0x60, 0xC1, 0x83, 0x07,
    0x0E, 0x1D, 0x3A, 0x75, 0xEA, 0xD5, 0xAA, 0x55, 0xAB, 0x57, 0xAF, 0x5F, 0xBE, 0x7C, 0xF9, 0xF2,
    0xE5, 0xCA, 0x94, 0x28, 0x50, 0xA1, 0x42, 0x84, 0x09, 0x13, 0x27, 0x4F, 0x9F, 0x3F, 0x7F
];

// Ported from gr-lora_sdr (EPFL, GPL-3.0) hamming_dec_impl.cc: cw_LUT and cw_LUT_cr5
pub const HAMMING_LUT: [u8; 16] = [0, 23, 45, 58, 78, 89, 99, 116, 139, 156, 166, 177, 197, 210, 232, 255];
pub const HAMMING_LUT_CR5: [u8; 16] = [0, 24, 40, 48, 72, 80, 96, 120, 136, 144, 160, 184, 192, 216, 232, 240];

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
