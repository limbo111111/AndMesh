pub mod crypto;
pub mod lora_phy;
pub mod packet;

use jni::JNIEnv;
use jni::objects::{JClass, JByteArray, JValue};
use std::panic;
use std::sync::atomic::{AtomicU64, Ordering};
use num_complex::Complex32;
use serde::Serialize;
use packet::proto::mesh_packet::PayloadVariant;

static CURRENT_FREQ_HZ: AtomicU64 = AtomicU64::new(869_525_000);

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_setFrequencyHz(
    _env: JNIEnv,
    _class: JClass,
    freq_hz: jni::sys::jlong,
) {
    CURRENT_FREQ_HZ.store(freq_hz as u64, Ordering::Relaxed);
}

#[derive(Serialize)]
struct DecodedPacketJson {
    from: u32,
    to: u32,
    id: u32,
    rx_time: Option<u32>,
    portnum: u32,
    payload_text: Option<String>,
}

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_pushIqSamples(
    mut env: JNIEnv,
    _class: JClass,
    iq_samples: JByteArray,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Ok(bytes) = env.convert_byte_array(iq_samples) {
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
                freq_hz: CURRENT_FREQ_HZ.load(Ordering::Relaxed),
            };

            if let Some(payload) = lora_phy::try_decode_packet(&iq_buf, &cfg) {
                let known_channels = vec![
                    packet::KnownChannel {
                        name: "LongFast".to_string(),
                        key: crypto::resolve_psk(&[1]).unwrap(),
                    }
                ];

                let msg = match packet::decode_mesh_packet(&payload, &known_channels) {
                    Ok(mesh_packet) => {
                        let mut parsed = DecodedPacketJson {
                            from: mesh_packet.from,
                            to: mesh_packet.to,
                            id: mesh_packet.id,
                            rx_time: mesh_packet.rx_time,
                            portnum: 0,
                            payload_text: None,
                        };

                        if let Some(PayloadVariant::Decoded(data)) = &mesh_packet.payload_variant {
                            parsed.portnum = data.portnum as u32;
                            // TEXT_MESSAGE_APP is portnum 1
                            if parsed.portnum == 1 {
                                parsed.payload_text = String::from_utf8(data.payload.clone()).ok();
                            }
                        }

                        serde_json::to_string(&parsed).unwrap_or_else(|_| "{}".to_string())
                    },
                    Err(e) => format!("{{\"error\": \"{:?}\"}}", e),
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
