package com.andmesh.app

object RtlSdrNative {
    init {
        System.loadLibrary("rust_core")
    }

    external fun pushIqSamples(iqSamples: ByteArray)
}
