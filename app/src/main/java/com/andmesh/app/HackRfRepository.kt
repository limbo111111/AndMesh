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

    val nodeId: Int

    init {
        val prefs = context.getSharedPreferences("AndMeshPrefs", Context.MODE_PRIVATE)
        if (!prefs.contains("node_id")) {
            val randomId = kotlin.random.Random.nextInt()
            prefs.edit().putInt("node_id", randomId).apply()
        }
        nodeId = prefs.getInt("node_id", 0)
    }

    private var hackrf: Hackrf? = null
    private var rxQueue: ArrayBlockingQueue<ByteArray>? = null
    private var rxThread: Thread? = null
    private var isReceiving = false

    private var txQueue: ArrayBlockingQueue<ByteArray>? = null

    // Configuration
    var frequencyHz = 869525000L
        set(value) {
            field = value
            if (isReceiving) {
                try {
                    hackrf?.setFrequency(value)
                    RtlSdrNative.setFrequencyHz(value)
                } catch (e: Exception) {
                    Log.e("HackRfRepository", "Failed to set frequency: ${e.message}")
                }
            }
        }

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
                setFrequency(frequencyHz)
                RtlSdrNative.setFrequencyHz(frequencyHz)
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

    fun sendTextMessage(text: String, fromNodeId: Int) {
        if (hackrf == null) return

        thread(start = true) {
            try {
                // Determine a reasonable TX gain start value, acting as a tuning parameter
                hackrf?.setTxVGAGain(32)

                val wasReceiving = isReceiving
                if (isReceiving) {
                    stopReceiving()
                }

                txQueue = hackrf?.startTX()
                if (txQueue == null) {
                    Log.e("HackRfRepository", "TX queue is null")
                    return@thread
                }

                // Call Rust to encode the packet (returns interleaved i8 samples as ByteArray)
                val rawBytes = RtlSdrNative.encodeTextMessage(text, fromNodeId)

                val packetSize = hackrf?.getPacketSize() ?: 262144 // Fallback if not available

                var offset = 0
                while (offset < rawBytes.size) {
                    val buffer = hackrf?.getBufferFromBufferPool()
                    if (buffer != null) {
                        val chunkLength = kotlin.math.min(packetSize, rawBytes.size - offset)
                        System.arraycopy(rawBytes, offset, buffer, 0, chunkLength)

                        // If chunk is smaller than buffer size, zero the rest
                        if (chunkLength < buffer.size) {
                            buffer.fill(0.toByte(), chunkLength, buffer.size)
                        }

                        txQueue?.put(buffer)
                        offset += chunkLength
                    } else {
                        // wait a bit for buffer to become available
                        Thread.sleep(10)
                    }
                }

                // According to hackrf API, stopping after transmission ends or keep alive?
                // Depending on the implementation, we might stop it immediately or let it flush.
                // We will sleep for a short while to ensure the queue is processed then stop.
                // Or maybe we can just leave it to hackrf to finish, but usually we stop tx.
                Thread.sleep(500) // allow time to send
                hackrf?.stop()
                txQueue = null

                // Restart RX if we were receiving
                if (wasReceiving) {
                    isReceiving = false // reset flag to allow restart
                    startReceiving()
                }
            } catch (e: Exception) {
                Log.e("HackRfRepository", "Error sending TX message", e)
            }
        }
    }

    fun close() {
        stopReceiving()
        hackrf = null
    }
}
