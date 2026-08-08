package com.andmesh.app

import android.util.Log

object RtlSdrNative {
    init {
        System.loadLibrary("rust_core")
    }

    external fun pushIqSamples(iqSamples: ByteArray)
    external fun setFrequencyHz(freqHz: Long)

    var packetListener: ((String) -> Unit)? = null

    @JvmStatic
    fun onPacketDecoded(packetInfo: String) {
        Log.d("RtlSdrNative", "Received from Rust: $packetInfo")
        packetListener?.invoke(packetInfo)
    }
}
