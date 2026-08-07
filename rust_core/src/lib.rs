pub mod crypto;
pub mod lora_phy;
pub mod packet;

use jni::JNIEnv;
use jni::objects::{JClass, JByteArray, JValue};
use std::panic;
use num_complex::Complex32;

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_pushIqSamples(
    mut env: JNIEnv,
    _class: JClass,
    iq_samples: JByteArray,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Ok(bytes) = env.convert_byte_array(iq_samples) {
            // Convert byte array to complex IQ samples
            // We assume interleaved 8-bit or 16-bit IQ, usually 8-bit unsigned for RTL-SDR
            // or 8-bit signed for HackRF. We'll treat them as 8-bit signed for this implementation.
            let mut complex_samples = Vec::with_capacity(bytes.len() / 2);
            for chunk in bytes.chunks_exact(2) {
                let i = (chunk[0] as i8) as f32 / 128.0;
                let q = (chunk[1] as i8) as f32 / 128.0;
                complex_samples.push(Complex32::new(i, q));
            }

            let iq_buf = lora_phy::IqBuffer {
                samples: &complex_samples,
                sample_rate_hz: 2_000_000,
            };

            let cfg = lora_phy::LoraConfig {
                spreading_factor: 11,
                bandwidth_hz: 250_000,
                coding_rate: 5,
                freq_hz: 869_525_000,
            };

            if let Some(payload) = lora_phy::try_decode_packet(&iq_buf, &cfg) {
                // Default known channels. Currently supporting standard Meshtastic LongFast.
                let known_channels = vec![
                    packet::KnownChannel {
                        name: "LongFast".to_string(),
                        key: crypto::resolve_psk(&[1]).unwrap(), // Default PSK [1] expands to AQ==
                    }
                ];

                let msg = match packet::decode_mesh_packet(&payload, &known_channels) {
                    Ok(mesh_packet) => format!("Decoded mesh packet: {:?}", mesh_packet),
                    Err(e) => format!("Failed to decode mesh packet: {:?}", e),
                };

                if let Ok(jmsg) = env.new_string(&msg) {
                    let _ = env.call_static_method(
                        "com/andmesh/app/RtlSdrNative",
                        "onPacketDecoded",
                        "(Ljava/lang/String;)V",
                        &[JValue::Object(&jmsg.into())],
                    );
                }
            }
        }
    }));
}
