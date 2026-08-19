package com.andmesh.app.mesh

import android.content.Context
import android.util.Log
import com.andmesh.app.HackRfRepository
import com.andmesh.app.RtlSdrNative
import com.andmesh.app.data.local.entity.MessageEntity
import com.andmesh.app.data.local.entity.NodeEntity
import com.andmesh.app.data.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.random.Random

class MeshRouter(
    private val context: Context,
    private val repository: MeshRepository,
    private val hackRfRepositoryProvider: () -> HackRfRepository?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deduplication cache with LRU eviction: max 1000 items
    private val seenPackets = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 1000
            }
        }
    )

    var isRelayEnabled: Boolean
        get() = context.getSharedPreferences("AndMeshPrefs", Context.MODE_PRIVATE)
            .getBoolean("mesh_relay_enabled", true)
        set(value) {
            context.getSharedPreferences("AndMeshPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("mesh_relay_enabled", value)
                .apply()
        }

    fun onPacketReceived(jsonString: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonString)
                if (json.has("error")) {
                    Log.w("MeshRouter", "Dropped error packet: ${json.getString("error")}")
                    return@launch
                }

                val from = json.optLong("from", 0L)
                val to = json.optLong("to", 0xFFFFFFFFL)
                val packetId = json.optLong("id", 0L)
                val portnum = json.optInt("portnum", 0)
                val hopLimit = json.optInt("hop_limit", 3)
                val hopStart = json.optInt("hop_start", 3)
                val hopsAway = (hopStart - hopLimit).coerceAtLeast(0)

                val packetKey = "$from:$packetId"
                val now = System.currentTimeMillis()

                // Check Deduplication
                val lastSeenTime = seenPackets[packetKey]
                if (lastSeenTime != null && (now - lastSeenTime) < 15 * 60 * 1000) {
                    Log.d("MeshRouter", "Duplicate packet $packetKey ignored.")
                    return@launch
                }
                seenPackets[packetKey] = now

                val hexId = "!${from.toString(16).padStart(8, '0').uppercase()}"
                val defaultName = "Node ${from.toString(16).uppercase()}"

                // Check existing node in DB
                val existingNode = repository.getNodeById(from).firstOrNull()

                val longName = json.optString("node_long_name", existingNode?.longName ?: defaultName)
                    .ifBlank { existingNode?.longName ?: defaultName }
                val shortName = json.optString("node_short_name", existingNode?.shortName ?: hexId.takeLast(2))
                    .ifBlank { existingNode?.shortName ?: hexId.takeLast(2) }

                val hwModelInt = if (json.has("node_hw_model")) json.optInt("node_hw_model") else null
                val hwModelStr = when (hwModelInt) {
                    1 -> "TLORA_V2"
                    4 -> "TBEAM"
                    5 -> "HELTEC_V2"
                    9 -> "RAK4631"
                    30 -> "RP2040_LORA"
                    else -> existingNode?.hwModel ?: "UNKNOWN"
                }

                val latitude = if (json.has("latitude") && !json.isNull("latitude")) json.getDouble("latitude") else existingNode?.latitude
                val longitude = if (json.has("longitude") && !json.isNull("longitude")) json.getDouble("longitude") else existingNode?.longitude
                val altitude = if (json.has("altitude") && !json.isNull("altitude")) json.getInt("altitude") else existingNode?.altitude
                val batteryLevel = if (json.has("battery_level") && !json.isNull("battery_level")) json.getInt("battery_level") else existingNode?.batteryLevel
                val voltage = if (json.has("voltage") && !json.isNull("voltage")) json.getDouble("voltage").toFloat() else existingNode?.voltage

                // Upsert Node
                val updatedNode = NodeEntity(
                    nodeId = from,
                    hexId = hexId,
                    longName = longName,
                    shortName = shortName,
                    hwModel = hwModelStr,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    batteryLevel = batteryLevel,
                    voltage = voltage,
                    hopsAway = hopsAway,
                    lastHeard = now,
                    isFavorite = existingNode?.isFavorite ?: false,
                    notes = existingNode?.notes
                )
                repository.upsertNode(updatedNode)

                // Handle Text Message
                if (portnum == 1 && json.has("payload_text") && !json.isNull("payload_text")) {
                    val text = json.getString("payload_text")
                    val message = MessageEntity(
                        packetId = packetId,
                        fromNodeId = from,
                        fromNodeName = longName,
                        toNodeId = to,
                        channelName = hackRfRepositoryProvider()?.channelName ?: "LongFast",
                        portNum = 1,
                        text = text,
                        timestamp = now,
                        isOutgoing = false,
                        hopLimit = hopLimit,
                        hopStart = hopStart
                    )
                    repository.insertMessage(message)
                }

                // Handle Relay / Flood Routing
                val myNodeId = hackRfRepositoryProvider()?.nodeId?.toLong() ?: 0L
                if (isRelayEnabled && from != myNodeId && hopLimit > 0) {
                    val rawPayloadJson = json.optJSONArray("raw_payload_bytes")
                    val rawPayload = if (rawPayloadJson != null) {
                        ByteArray(rawPayloadJson.length()) { i -> rawPayloadJson.getInt(i).toByte() }
                    } else if (json.has("payload_text")) {
                        json.getString("payload_text").toByteArray(Charsets.UTF_8)
                    } else {
                        ByteArray(0)
                    }

                    val nextHopLimit = hopLimit - 1
                    // Jitter delay between 100ms and 400ms to avoid channel collisions
                    val jitterMs = Random.nextLong(100, 400)
                    scope.launch {
                        delay(jitterMs)
                        try {
                            val rawIqBytes = RtlSdrNative.encodeMeshPacket(
                                to = to,
                                from = from,
                                id = packetId,
                                hopLimit = nextHopLimit,
                                hopStart = hopStart,
                                portnum = portnum,
                                payloadBytes = rawPayload
                            )
                            if (rawIqBytes != null && rawIqBytes.isNotEmpty()) {
                                hackRfRepositoryProvider()?.sendRawIqBytes(rawIqBytes)
                                Log.d("MeshRouter", "Relayed packet $packetKey with hop_limit=$nextHopLimit")
                            }
                        } catch (e: Exception) {
                            Log.e("MeshRouter", "Failed to relay packet $packetKey", e)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("MeshRouter", "Error processing packet", e)
            }
        }
    }
}
