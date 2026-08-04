plugins {
    id("com.android.library")
}

android {
    namespace = "com.andmesh.rust_core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }
}

val cargoTargetAbis = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
    "x86" to "i686-linux-android",
    "x86_64" to "x86_64-linux-android"
)

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group = "rust"
    workingDir = file(".")

    val ndkDir = android.ndkDirectory.absolutePath
    environment("ANDROID_NDK_HOME", ndkDir)

    // Construct command line
    val args = mutableListOf("cargo", "ndk")
    cargoTargetAbis.keys.forEach { abi ->
        args.add("-t")
        args.add(abi)
    }

    val outDir = file("../app/src/main/jniLibs")
    args.add("-o")
    args.add(outDir.absolutePath)
    args.add("build")

    // Enable release build for typical distribution; can be dynamic based on build type
    args.add("--release")

    commandLine(args)

    // Add cargo check before attempting full ndk build to spot errors quickly
    doFirst {
        println("Building Rust core module for Android using cargo-ndk...")
    }
}

