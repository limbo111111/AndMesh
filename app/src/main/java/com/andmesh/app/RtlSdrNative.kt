package com.andmesh.app

import android.util.Log

object RtlSdrNative {
    init {
        System.loadLibrary("rust_core")
    }

    external fun pushIqSamples(iqSamples: ByteArray)
    external fun setFrequencyHz(freqHz: Long)
    external fun setChannel(channelName: String, psk: ByteArray)
    external fun encodeTextMessage(text: String, fromNodeId: Int): ByteArray
    external fun encodeMeshPacket(
        to: Long,
        from: Long,
        id: Long,
        hopLimit: Int,
        hopStart: Int,
        portnum: Int,
        payloadBytes: ByteArray
    ): ByteArray

    var packetListener: ((String) -> Unit)? = null

    @JvmStatic
    fun onPacketDecoded(packetInfo: String) {
        Log.d("RtlSdrNative", "Received from Rust: $packetInfo")
        packetListener?.invoke(packetInfo)
    }
}
