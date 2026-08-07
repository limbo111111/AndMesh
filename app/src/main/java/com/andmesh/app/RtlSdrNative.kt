package com.andmesh.app

import android.util.Log

object RtlSdrNative {
    init {
        System.loadLibrary("rust_core")
    }

    external fun pushIqSamples(iqSamples: ByteArray)

    @JvmStatic
    fun onPacketDecoded(packetInfo: String) {
        Log.d("RtlSdrNative", "Received from Rust: $packetInfo")
    }
}
