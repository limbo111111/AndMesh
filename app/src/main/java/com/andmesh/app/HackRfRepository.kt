package com.andmesh.app

import android.content.Context
import android.util.Log
import com.mantz_it.hackrf_android.Hackrf
import com.mantz_it.hackrf_android.HackrfCallbackInterface
import com.mantz_it.hackrf_android.HackrfUsbException
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

class HackRfRepository(
    private val context: Context,
    private val onDeviceReady: (HackRfRepository) -> Unit,
    private val onDeviceError: (String) -> Unit
) : HackrfCallbackInterface {

    private var hackrf: Hackrf? = null
    private var rxQueue: ArrayBlockingQueue<ByteArray>? = null
    private var rxThread: Thread? = null
    private var isReceiving = false

    // Default configuration for Meshtastic (EU_868)
    private val FREQUENCY_HZ = 869525000L
    private val SAMPLE_RATE = 2000000 // 2 Msps

    fun initialize() {
        Log.d("HackRfRepository", "Initializing HackRF...")
        // Queue size of 10 is usually fine for HackRF buffer
        Hackrf.initHackrf(context, this, 10)
    }

    override fun onHackrfReady(hackrfInstance: Hackrf) {
        Log.d("HackRfRepository", "HackRF is ready.")
        this.hackrf = hackrfInstance

        try {
            // Configure HackRF
            hackrf?.apply {
                Log.d("HackRfRepository", "Configuring HackRF...")
                setFrequency(FREQUENCY_HZ)
                setSampleRate(SAMPLE_RATE, 1) // Sample rate, divider=1
                setBasebandFilterBandwidth(1750000) // approx for 2 Msps
                setRxVGAGain(32) // reasonable defaults
                setRxLNAGain(32)
                setAmp(true) // LNA
                Log.d("HackRfRepository", "HackRF configuration complete.")
            }
            onDeviceReady(this)
        } catch (e: HackrfUsbException) {
            Log.e("HackRfRepository", "Error configuring HackRF: ${e.message}", e)
            onDeviceError("Config Error: ${e.message}")
        }
    }

    override fun onHackrfError(message: String) {
        Log.e("HackRfRepository", "HackRF Initialization Error: $message")
        onDeviceError(message)
    }

    fun startReceiving() {
        if (hackrf == null || isReceiving) return

        try {
            Log.d("HackRfRepository", "Starting RX...")
            rxQueue = hackrf!!.startRX()
            isReceiving = true

            rxThread = thread(start = true) {
                while (isReceiving) {
                    try {
                        val buffer = rxQueue?.take()
                        if (buffer != null) {
                            // Push IQ samples to Native layer
                            RtlSdrNative.pushIqSamples(buffer)
                        }
                    } catch (e: InterruptedException) {
                        Log.d("HackRfRepository", "RX Thread interrupted.")
                        break
                    }
                }
            }
        } catch (e: HackrfUsbException) {
            Log.e("HackRfRepository", "Error starting RX: ${e.message}", e)
            onDeviceError("RX Start Error: ${e.message}")
        }
    }

    fun stopReceiving() {
        if (!isReceiving) return
        isReceiving = false

        try {
            hackrf?.stop()
        } catch (e: HackrfUsbException) {
            Log.e("HackRfRepository", "Error stopping RX: ${e.message}", e)
        }

        rxThread?.interrupt()
        rxThread = null
        rxQueue = null
        Log.d("HackRfRepository", "RX stopped.")
    }

    fun close() {
        stopReceiving()
        hackrf = null
    }
}
