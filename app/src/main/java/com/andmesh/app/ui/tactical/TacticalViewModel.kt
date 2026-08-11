package com.andmesh.app.ui.tactical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MeshMessage(val fromNode: String, val text: String)

data class NodeState(val id: String, val name: String, val hops: Int, var lastHeard: Long)

data class TacticalState(
    val nodes: List<MeshNode> = emptyList(),
    val messages: List<MeshMessage> = emptyList()
)

class TacticalViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TacticalState())
    val uiState: StateFlow<TacticalState> = _uiState.asStateFlow()

    private val nodesMap = mutableMapOf<String, NodeState>()

    init {
        // Periodic updates for time since last heard
        viewModelScope.launch {
            while (true) {
                updateNodesUI()
                delay(10000) // Update every 10 seconds
            }
        }
    }

    private fun updateNodesUI() {
        val now = System.currentTimeMillis()
        val displayNodes = nodesMap.values.sortedByDescending { it.lastHeard }.map { entity ->
            val elapsedMs = now - entity.lastHeard
            val statusLabel = when {
                elapsedMs < 60_000 -> "ONLINE"
                elapsedMs < 3600_000 -> "SEEN ${elapsedMs / 60_000} MIN AGO"
                else -> "SEEN ${elapsedMs / 3600_000} HR AGO"
            }
            MeshNode(entity.name, "${entity.hops} HOPS", statusLabel)
        }
        _uiState.update { it.copy(nodes = displayNodes) }
    }

    fun onPacketReceived(jsonString: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(jsonString)
                if (json.has("error")) {
                    Log.e("TacticalViewModel", "Error in packet: ${json.getString("error")}")
                    return@launch
                }

                val from = json.getLong("from")
                val fromName = "Node ${from.toString(16).uppercase()}"
                val idStr = from.toString()

                // Update or add node to memory map
                nodesMap[idStr] = NodeState(
                    id = idStr,
                    name = fromName,
                    hops = 0, // Hardcoded for now
                    lastHeard = System.currentTimeMillis()
                )
                updateNodesUI()

                // Check for text message
                if (json.has("payload_text") && !json.isNull("payload_text")) {
                    val text = json.getString("payload_text")
                    _uiState.update { state ->
                        val newMessages = listOf(MeshMessage(fromName, text)) + state.messages
                        state.copy(messages = newMessages.take(50)) // Keep last 50 messages
                    }
                }

            } catch (e: Exception) {
                Log.e("TacticalViewModel", "Failed to parse JSON packet", e)
            }
        }
    }

    fun sendLocalMessage(text: String, nodeId: Int) {
        val fromName = "Node ${nodeId.toString(16).uppercase()}"
        _uiState.update { state ->
            val newMessages = listOf(MeshMessage(fromName, text)) + state.messages
            state.copy(messages = newMessages.take(50)) // Keep last 50 messages
        }
    }
}
