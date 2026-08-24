use std::fs::File;
use std::io::Read;
use num_complex::Complex;
use rust_core::lora_phy::{try_decode_packet, LoraConfig, IqBuffer, whiten};
use rust_core::crypto::ChannelKey;
use rust_core::packet::{decode_mesh_packet, KnownChannel};

#[test]
#[ignore = "RX CFO/STO Dechirp path is defective. Expected gray symbols (1297, 369, 1985...) do not match extracted raw symbols (937, 49, 29...) with a non-constant drift (e.g., 360, 320, 1956...). See TODO-meshsdr.md."]
fn test_sync_word_empirically() {
    let mut file = File::open("tests/fixtures/meshtastic_test.cf32").unwrap();
    let mut buf = Vec::new();
    file.read_to_end(&mut buf).unwrap();

    let mut iq_data = Vec::new();
    for chunk in buf.chunks(8) {
        if chunk.len() == 8 {
            let i = f32::from_le_bytes(chunk[0..4].try_into().unwrap());
            let q = f32::from_le_bytes(chunk[4..8].try_into().unwrap());
            iq_data.push(Complex::new(i, q));
        }
    }

    let default_psk = [
        0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
        0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0x01,
    ];
    let config = LoraConfig {
        spreading_factor: 11,
        coding_rate: 5,
        freq_hz: 903_000_000,
        bandwidth_hz: 250_000,
    };

    let iq_buffer = IqBuffer { samples: &iq_data, sample_rate_hz: 250_000 };

    // Check if the current sync val matches what we generate and if we can decode the header at least.
    let start = rust_core::lora_phy::detect_preamble(&iq_buffer, &config).unwrap();
    let (cfo, sto) = rust_core::lora_phy::estimate_cfo_sto(&iq_buffer, start, &config);
    let _raw_symbols = rust_core::lora_phy::dechirp_symbols(&iq_buffer, start, cfo, sto, &config);

    let result = try_decode_packet(&iq_buffer, &config);

    match result {
        Ok(raw_bytes) => {
            let key = ChannelKey::Aes128(default_psk);
            let channel = KnownChannel {
                name: "LongFast".to_string(),
                key: key,
            };
            let packet = decode_mesh_packet(&raw_bytes, &[channel]).expect("Failed to decrypt");
            if let Some(payload_variant) = packet.payload_variant {
                match payload_variant {
                    rust_core::packet::proto::mesh_packet::PayloadVariant::Decoded(data) => {
                        assert_eq!(data.payload, b"Hello");
                    }
                    _ => panic!("Expected Decoded payload"),
                }
            } else {
                panic!("No payload variant");
            }
        },
        Err(e) => panic!("Failed to decode packet with current sync_val: {:?}", e),
    }
}

#[test]
fn test_whitening_against_ground_truth() {
    let mut file = File::open("tests/fixtures/lora_stages/00_input.bin").unwrap();
    let mut input = Vec::new();
    file.read_to_end(&mut input).unwrap();

    let mut file = File::open("tests/fixtures/lora_stages/01_whitening.bin").unwrap();
    let mut expected_nibbles = Vec::new();
    file.read_to_end(&mut expected_nibbles).unwrap();

    let mut expected = Vec::new();
    // The fixture contains packed nibbles, where the lowest 4 bits are stored in the first byte
    // and the upper 4 bits are stored in the second byte. LSB-first.
    for i in (0..expected_nibbles.len()).step_by(2) {
        expected.push(expected_nibbles[i] | (expected_nibbles[i+1] << 4));
    }

    let our_whitened = whiten(&input);

    assert_eq!(input.len(), 255);
    assert_eq!(our_whitened.len(), 255);

    let mut mismatch = false;
    for i in 0..input.len() {
        if our_whitened[i] != expected[i] {
            println!("Mismatch at index {}: our {:X}, expected {:X}", i, our_whitened[i], expected[i]);
            mismatch = true;
        }
    }
    assert!(!mismatch, "Whitening sequence mismatch");
}
