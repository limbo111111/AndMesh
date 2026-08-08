pub mod crypto;
pub mod lora_phy;
pub mod packet;

use jni::JNIEnv;
use jni::objects::{JClass, JByteArray, JValue, JString};
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

use std::sync::atomic::AtomicU32;
static NEXT_PACKET_ID: AtomicU32 = AtomicU32::new(1);

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_encodeTextMessage(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    from_node_id: jni::sys::jint,
) -> jni::sys::jbyteArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let text_str: String = env.get_string(&text).expect("Couldn't get java string").into();

        let mut data = packet::proto::Data::default();
        data.portnum = 1; // TEXT_MESSAGE_APP
        data.payload = text_str.into_bytes();

        let mut mesh_packet = packet::proto::MeshPacket::default();
        mesh_packet.from = from_node_id as u32;
        mesh_packet.to = 0xFFFFFFFF; // broadcast by default for mesh
        mesh_packet.id = NEXT_PACKET_ID.fetch_add(1, Ordering::Relaxed);
        mesh_packet.payload_variant = Some(packet::proto::mesh_packet::PayloadVariant::Decoded(data));

        let key = crypto::resolve_psk(&[1]).unwrap();
        let encoded_bytes = packet::encode_mesh_packet(mesh_packet, "LongFast", &key);

        let cfg = lora_phy::LoraConfig {
            spreading_factor: 11,
            bandwidth_hz: 250_000,
            coding_rate: 5,
            freq_hz: CURRENT_FREQ_HZ.load(Ordering::Relaxed),
        };

        let complex_samples = lora_phy::encode_packet(&encoded_bytes, &cfg);

        let mut out_bytes = Vec::with_capacity(complex_samples.len() * 2);
        for sample in complex_samples {
            let re_clamped = (sample.re * 128.0).clamp(-128.0, 127.0) as i8;
            let im_clamped = (sample.im * 128.0).clamp(-128.0, 127.0) as i8;
            out_bytes.push(re_clamped as u8);
            out_bytes.push(im_clamped as u8);
        }

        let jbyte_array = env.byte_array_from_slice(&out_bytes).expect("Failed to create byte array");
        jbyte_array
    }));

    match result {
        Ok(jbyte_array) => jbyte_array.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
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
