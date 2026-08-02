pub mod crypto;
pub mod lora_phy;
pub mod packet;

use jni::JNIEnv;
use jni::objects::{JClass, JByteArray};
use jni::sys::jbyteArray;

#[no_mangle]
pub extern "system" fn Java_com_andmesh_app_RtlSdrNative_pushIqSamples(
    env: JNIEnv,
    _class: JClass,
    _iq_samples: JByteArray,
) {
    // TODO: Connect JNI to lora_phy and packet processing here, wrapping in catch_unwind
}
