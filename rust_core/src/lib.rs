pub mod crypto;
pub mod lora_phy;
pub mod packet;

use jni::objects::{JByteArray, JClass, JString, JValue};
use jni::JNIEnv;
use num_complex::Complex32;
use packet::proto::mesh_packet::PayloadVariant;
use serde::Serialize;
use std::panic;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;

static CURRENT_FREQ_HZ: AtomicU64 = AtomicU64::new(869_525_000);

lazy_static::lazy_static! {
    static ref CURRENT_CHANNEL: Mutex<(String, Vec<u8>)> = Mutex::new(("LongFast".to_string(), vec![1]));
}

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_setFrequencyHz(
    _env: JNIEnv,
    _class: JClass,
    freq_hz: jni::sys::jlong,
) {
    CURRENT_FREQ_HZ.store(freq_hz as u64, Ordering::Relaxed);
}

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_setChannel(
    mut env: JNIEnv,
    _class: JClass,
    channel_name: JString,
    psk: JByteArray,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let ch_name_str: String = match env.get_string(&channel_name) {
            Ok(s) => s.into(),
            Err(_) => "LongFast".to_string(),
        };
        let psk_bytes = env.convert_byte_array(psk).unwrap_or_else(|_| vec![1]);

        if let Ok(mut channel) = CURRENT_CHANNEL.lock() {
            channel.0 = ch_name_str;
            channel.1 = psk_bytes;
        }
    }));
}

#[derive(Serialize)]
struct DecodedPacketJson {
    from: u32,
    to: u32,
    id: u32,
    rx_time: Option<u32>,
    portnum: u32,
    hop_limit: u32,
    hop_start: u32,
    want_ack: bool,
    via_mqtt: bool,
    channel: u32,
    payload_text: Option<String>,
    latitude: Option<f64>,
    longitude: Option<f64>,
    altitude: Option<i32>,
    node_id_str: Option<String>,
    node_long_name: Option<String>,
    node_short_name: Option<String>,
    node_hw_model: Option<i32>,
    battery_level: Option<u32>,
    voltage: Option<f32>,
    raw_payload_bytes: Option<Vec<u8>>,
}

use prost::Message;
use rand::RngExt;

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_encodeTextMessage(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    from_node_id: jni::sys::jint,
) -> jni::sys::jbyteArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let text_str: String = env
            .get_string(&text)
            .expect("Couldn't get java string")
            .into();

        let mut data = packet::proto::Data::default();
        data.portnum = 1; // TEXT_MESSAGE_APP
        data.payload = text_str.into_bytes();

        let mut mesh_packet = packet::proto::MeshPacket::default();
        mesh_packet.from = from_node_id as u32;
        mesh_packet.to = 0xFFFFFFFF; // broadcast by default for mesh
        mesh_packet.id = rand::rng().random::<u32>();
        mesh_packet.hop_limit = 3;
        mesh_packet.hop_start = 3;
        mesh_packet.payload_variant =
            Some(packet::proto::mesh_packet::PayloadVariant::Decoded(data));

        let (ch_name, ch_psk) = {
            if let Ok(channel) = CURRENT_CHANNEL.lock() {
                (channel.0.clone(), channel.1.clone())
            } else {
                ("LongFast".to_string(), vec![1])
            }
        };

        let key =
            crypto::resolve_psk(&ch_psk).unwrap_or_else(|| crypto::resolve_psk(&[1]).unwrap());
        let encoded_bytes = packet::encode_mesh_packet(mesh_packet, &ch_name, &key);

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

        let jbyte_array = env
            .byte_array_from_slice(&out_bytes)
            .expect("Failed to create byte array");
        jbyte_array
    }));

    match result {
        Ok(jbyte_array) => jbyte_array.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_encodeMeshPacket(
    mut env: JNIEnv,
    _class: JClass,
    to: jni::sys::jlong,
    from: jni::sys::jlong,
    id: jni::sys::jlong,
    hop_limit: jni::sys::jint,
    hop_start: jni::sys::jint,
    portnum: jni::sys::jint,
    payload_bytes: JByteArray,
) -> jni::sys::jbyteArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let raw_payload = env
            .convert_byte_array(payload_bytes)
            .unwrap_or_else(|_| Vec::new());

        let mut data = packet::proto::Data::default();
        data.portnum = portnum as i32;
        data.payload = raw_payload;

        let mut mesh_packet = packet::proto::MeshPacket::default();
        mesh_packet.from = from as u32;
        mesh_packet.to = to as u32;
        mesh_packet.id = id as u32;
        mesh_packet.hop_limit = hop_limit as u32;
        mesh_packet.hop_start = hop_start as u32;
        mesh_packet.payload_variant =
            Some(packet::proto::mesh_packet::PayloadVariant::Decoded(data));

        let (ch_name, ch_psk) = {
            if let Ok(channel) = CURRENT_CHANNEL.lock() {
                (channel.0.clone(), channel.1.clone())
            } else {
                ("LongFast".to_string(), vec![1])
            }
        };

        let key =
            crypto::resolve_psk(&ch_psk).unwrap_or_else(|| crypto::resolve_psk(&[1]).unwrap());
        let encoded_bytes = packet::encode_mesh_packet(mesh_packet, &ch_name, &key);

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

        let jbyte_array = env
            .byte_array_from_slice(&out_bytes)
            .expect("Failed to create byte array");
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
                sample_rate_hz: 8_000_000,
            };

            let cfg = lora_phy::LoraConfig {
                spreading_factor: 11,
                bandwidth_hz: 250_000,
                coding_rate: 5,
                freq_hz: CURRENT_FREQ_HZ.load(Ordering::Relaxed),
            };

            if let Ok(payload) = lora_phy::try_decode_packet(&iq_buf, &cfg) {
                let (ch_name, ch_psk) = {
                    if let Ok(channel) = CURRENT_CHANNEL.lock() {
                        (channel.0.clone(), channel.1.clone())
                    } else {
                        ("LongFast".to_string(), vec![1])
                    }
                };

                let known_channels = vec![
                    packet::KnownChannel {
                        name: ch_name,
                        key: crypto::resolve_psk(&ch_psk)
                            .unwrap_or_else(|| crypto::resolve_psk(&[1]).unwrap()),
                    },
                    // Fallback to LongFast always if custom fails
                    packet::KnownChannel {
                        name: "LongFast".to_string(),
                        key: crypto::resolve_psk(&[1]).unwrap(),
                    },
                ];

                let msg = match packet::decode_mesh_packet(&payload, &known_channels) {
                    Ok(mesh_packet) => {
                        let mut parsed = DecodedPacketJson {
                            from: mesh_packet.from,
                            to: mesh_packet.to,
                            id: mesh_packet.id,
                            rx_time: mesh_packet.rx_time,
                            portnum: 0,
                            hop_limit: mesh_packet.hop_limit,
                            hop_start: mesh_packet.hop_start,
                            want_ack: mesh_packet.want_ack,
                            via_mqtt: mesh_packet.via_mqtt,
                            channel: mesh_packet.channel,
                            payload_text: None,
                            latitude: None,
                            longitude: None,
                            altitude: None,
                            node_id_str: None,
                            node_long_name: None,
                            node_short_name: None,
                            node_hw_model: None,
                            battery_level: None,
                            voltage: None,
                            raw_payload_bytes: None,
                        };

                        if let Some(PayloadVariant::Decoded(data)) = &mesh_packet.payload_variant {
                            parsed.portnum = data.portnum as u32;
                            parsed.raw_payload_bytes = Some(data.payload.clone());

                            match data.portnum {
                                1 => {
                                    // TEXT_MESSAGE_APP
                                    parsed.payload_text = String::from_utf8(data.payload.clone()).ok();
                                }
                                3 => {
                                    // POSITION_APP
                                    if let Ok(pos) = packet::proto::Position::decode(data.payload.as_slice()) {
                                        parsed.latitude = pos.latitude_i.map(|lat| lat as f64 * 1e-7);
                                        parsed.longitude = pos.longitude_i.map(|lon| lon as f64 * 1e-7);
                                        parsed.altitude = pos.altitude;
                                    }
                                }
                                4 => {
                                    // NODEINFO_APP
                                    if let Ok(user) = packet::proto::User::decode(data.payload.as_slice()) {
                                        if !user.id.is_empty() {
                                            parsed.node_id_str = Some(user.id);
                                        }
                                        if !user.long_name.is_empty() {
                                            parsed.node_long_name = Some(user.long_name);
                                        }
                                        if !user.short_name.is_empty() {
                                            parsed.node_short_name = Some(user.short_name);
                                        }
                                        parsed.node_hw_model = Some(user.hw_model);
                                    }
                                }
                                67 => {
                                    // TELEMETRY_APP
                                    if let Ok(telem) = packet::proto::Telemetry::decode(data.payload.as_slice()) {
                                        if let Some(packet::proto::telemetry::Variant::DeviceMetrics(dm)) = telem.variant {
                                            parsed.battery_level = dm.battery_level;
                                            parsed.voltage = dm.voltage;
                                        }
                                    }
                                }
                                _ => {}
                            }
                        }

                        serde_json::to_string(&parsed).unwrap_or_else(|_| "{}".to_string())
                    }
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

