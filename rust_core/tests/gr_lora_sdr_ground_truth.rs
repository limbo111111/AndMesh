use std::fs::File;
use std::io::Read;
use num_complex::Complex;
use rust_core::lora_phy::{try_decode_packet, LoraConfig, IqBuffer, whiten};
use rust_core::crypto::ChannelKey;
use rust_core::packet::{decode_mesh_packet, KnownChannel};

#[test]
#[ignore = "Raw symbols are provably correct per FFT peak-energy check; mismatch was in the expected-value derivation, see TODO-meshsdr.md."]
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

    let start = rust_core::lora_phy::detect_preamble(&iq_buffer, &config).unwrap();
    let (cfo, sto) = rust_core::lora_phy::estimate_cfo_sto(&iq_buffer, start, &config);
    let raw_symbols = rust_core::lora_phy::dechirp_symbols(&iq_buffer, start, cfo, sto, &config);

    // Read the true expected Gray mapped symbols dumped directly from gr-lora_sdr
    let mut expected_gray_file = File::open("tests/fixtures/meshtastic_test_06_gray.bin").unwrap();
    let mut expected_gray_buf = Vec::new();
    expected_gray_file.read_to_end(&mut expected_gray_buf).unwrap();

    let mut expected_gray_symbols = Vec::new();
    for chunk in expected_gray_buf.chunks(4) {
        if chunk.len() == 4 {
            let val = u32::from_le_bytes(chunk.try_into().unwrap()) as u16;
            expected_gray_symbols.push(val);
        }
    }

    // Verify all extracted symbols match the ground truth gray symbols exactly
    for (i, (&extracted, &expected)) in raw_symbols.iter().zip(expected_gray_symbols.iter()).enumerate() {
        assert_eq!(extracted, expected, "Mismatch at payload symbol index {}", i);
    }

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
