use std::env;
use std::path::PathBuf;

fn main() {
    println!("cargo:rerun-if-changed=proto/");

    let _out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    prost_build::Config::new()
        .compile_protos(
            &[
                "proto/meshtastic/mesh.proto",
                "proto/meshtastic/portnums.proto",
                "proto/meshtastic/telemetry.proto",
                "proto/meshtastic/config.proto",
                "proto/meshtastic/apponly.proto",
                "proto/meshtastic/channel.proto",
            ],
            &["proto/"],
        )
        .unwrap();
}
