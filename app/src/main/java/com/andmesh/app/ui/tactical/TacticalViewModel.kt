package com.andmesh.app.ui.tactical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andmesh.app.data.NodeDao
import com.andmesh.app.data.NodeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MeshMessage(val fromNode: String, val text: String)

data class TacticalState(
    val nodes: List<MeshNode> = emptyList(),
    val messages: List<MeshMessage> = emptyList()
)

class TacticalViewModel(private val nodeDao: NodeDao) : ViewModel() {
    private val _uiState = MutableStateFlow(TacticalState())
    val uiState: StateFlow<TacticalState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            nodeDao.getAllNodes().collect { entities ->
                val now = System.currentTimeMillis()
                val displayNodes = entities.map { entity ->
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
        }
    }

    fun onPacketReceived(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            if (json.has("error")) {
                Log.e("TacticalViewModel", "Error in packet: ${json.getString("error")}")
                return
            }

            val from = json.getLong("from")
            val fromName = "Node ${from.toString(16).uppercase()}"

            // Insert or update node
            viewModelScope.launch {
                nodeDao.insertNode(
                    NodeEntity(
                        id = from.toString(),
                        name = fromName,
                        hops = 0,
                        lastHeard = System.currentTimeMillis()
                    )
                )
            }

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

    fun sendLocalMessage(text: String, nodeId: Int) {
        val fromName = "Node ${nodeId.toString(16).uppercase()}"
        _uiState.update { state ->
            val newMessages = listOf(MeshMessage(fromName, text)) + state.messages
            state.copy(messages = newMessages.take(50)) // Keep last 50 messages
        }
    }
}
