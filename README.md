# 📻 AndMesh

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Rust](https://img.shields.io/badge/rust-%23000000.svg?style=for-the-badge&logo=rust&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue.svg?style=for-the-badge)

**AndMesh** is an Android application that enables communication with the **Meshtastic** LoRa network via a **HackRF** Software Defined Radio (SDR). It utilizes a custom software LoRa PHY layer implemented in Rust, bridging directly to a modern Jetpack Compose Kotlin UI.

## ✨ Features

*   **📱 Modern Android UI:** Built with Jetpack Compose, featuring a "Tactical" theme for node management, direct chat history, and runtime settings.
*   **🦀 Rust Core (JNI):** High-performance DSP and packet processing handled entirely in Rust, seamlessly integrated via `cargo-ndk`.
*   **📡 Software LoRa PHY:** Full implementation of LoRa Physical Layer (RX and TX paths) including preamble detection, dechirp, FFT demodulation, Gray decoding, deinterleaving, Hamming encoding/decoding, and whitening.
*   **🔒 Meshtastic Integration:** Strictly compatible with Meshtastic protobufs and utilizes AES-256-CTR encryption for secure mesh communication.
*   **🔌 HackRF USB OTG Support:** Direct communication with HackRF devices via USB using `demantz/hackrf_android` (Requires USB OTG).
*   **💾 Local Persistence:** Nodes and messages are efficiently stored using the Android Room database.
*   **🕸️ Mesh Routing:** Implements flood routing, deduplication, and jitter queues for robust message delivery.

## 🏗️ Architecture

The project is split into two primary modules:
1.  **`app` (Kotlin/Android):** Handles the UI (Jetpack Compose), local database (Room), background services (`MeshSdrService`), and USB hardware permissions.
2.  **`rust_core` (Rust):** Contains the DSP logic, LoRa PHY encoding/decoding pipelines, cryptography, and JNI bridges to communicate with the Kotlin UI.

## 🚀 Getting Started

### Prerequisites

To build and run AndMesh, you will need the following development environment setup:
*   [Android Studio](https://developer.android.com/studio) (Latest version recommended)
*   Android NDK (`25.1.8937393` - configured via SDK Manager)
*   [Rust Toolchain](https://rustup.rs/) (rustup)
*   `cargo-ndk` (Install via `cargo install cargo-ndk`)
*   `protobuf-compiler` (System package, e.g., `apt install protobuf-compiler` or `brew install protobuf`)
*   Target Rust architectures for Android:
    ```bash
    rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
    ```

### Building the Project

1.  Clone the repository and ensure submodules are initialized (for Meshtastic protobufs):
    ```bash
    git clone https://github.com/your-username/AndMesh.git
    cd AndMesh
    git submodule update --init --recursive
    ```
2.  Open the project in Android Studio.
3.  Let Gradle sync the project. The Gradle build script in `app/build.gradle.kts` is configured to automatically invoke `cargo-ndk` to compile the Rust core into JNI libraries.
4.  Build and deploy the APK to your Android device.

## 🔌 Hardware Setup

1. Connect your **HackRF** to your Android device using a USB OTG adapter/cable.
2. Launch **AndMesh**.
3. Grant the required USB permissions when prompted.
4. Navigate to the Settings screen to configure your frequency, channel, and PSK base64.

## ⚠️ Current Status & Limitations

*   **SDR Support:** Currently, **only HackRF is supported**. (RTL-SDR is explicitly excluded as per requirements).
*   **Regulatory Compliance:** Ensure you are operating within the legal limits and duty cycles (e.g., ETSI EN 300 220 for EU868) of your region. The software is provided as-is, and you are solely responsible for legal transmission.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
