package com.andmesh.app.ui.tactical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andmesh.app.data.local.entity.MessageEntity
import com.andmesh.app.data.local.entity.NodeEntity
import com.andmesh.app.data.repository.MeshRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MeshMessage(
    val id: Long,
    val fromNode: String,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val hopLimit: Int
)

data class TacticalState(
    val nodes: List<MeshNode> = emptyList(),
    val messages: List<MeshMessage> = emptyList(),
    val selectedNode: NodeEntity? = null,
    val selectedNodeMessages: List<MessageEntity> = emptyList()
)

class TacticalViewModel(private val repository: MeshRepository) : ViewModel() {

    private val _selectedNodeId = MutableStateFlow<Long?>(null)
    val selectedNodeId: StateFlow<Long?> = _selectedNodeId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _selectedNode = _selectedNodeId.flatMapLatest { id ->
        if (id != null) repository.getNodeById(id) else flowOf(null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _selectedNodeMessages = _selectedNodeId.flatMapLatest { id ->
        if (id != null) repository.getMessagesForNode(id) else flowOf(emptyList())
    }

    val uiState: StateFlow<TacticalState> = combine(
        repository.allNodes,
        repository.allMessages,
        _selectedNode,
        _selectedNodeMessages
    ) { nodes, messages, selectedNode, nodeMessages ->
        val now = System.currentTimeMillis()
        val displayNodes = nodes.map { entity ->
            val elapsedMs = now - entity.lastHeard
            val statusLabel = when {
                elapsedMs < 60_000 -> "ONLINE"
                elapsedMs < 3600_000 -> "SEEN ${elapsedMs / 60_000} MIN AGO"
                else -> "SEEN ${elapsedMs / 3600_000} HR AGO"
            }
            MeshNode(
                nodeId = entity.nodeId,
                name = entity.longName,
                hexId = entity.hexId,
                hopsLabel = "${entity.hopsAway} HOPS",
                statusLabel = statusLabel,
                isFavorite = entity.isFavorite
            )
        }

        val displayMessages = messages.map { entity ->
            MeshMessage(
                id = entity.id,
                fromNode = if (entity.isOutgoing) "LOKAL" else entity.fromNodeName,
                text = entity.text,
                timestamp = entity.timestamp,
                isOutgoing = entity.isOutgoing,
                hopLimit = entity.hopLimit
            )
        }

        TacticalState(
            nodes = displayNodes,
            messages = displayMessages,
            selectedNode = selectedNode,
            selectedNodeMessages = nodeMessages
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TacticalState()
    )

    fun selectNode(nodeId: Long?) {
        _selectedNodeId.value = nodeId
    }

    fun sendLocalMessage(text: String, fromNodeId: Int, toNodeId: Long = 0xFFFFFFFFL) {
        viewModelScope.launch {
            val fromName = "Node ${fromNodeId.toString(16).uppercase()}"
            val message = MessageEntity(
                packetId = kotlin.random.Random.nextLong(),
                fromNodeId = fromNodeId.toLong(),
                fromNodeName = fromName,
                toNodeId = toNodeId,
                channelName = "LongFast",
                portNum = 1,
                text = text,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                hopLimit = 3,
                hopStart = 3
            )
            repository.insertMessage(message)
        }
    }

    fun toggleFavorite(nodeId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setNodeFavorite(nodeId, isFavorite)
        }
    }

    fun updateNotes(nodeId: Long, notes: String) {
        viewModelScope.launch {
            repository.updateNodeNotes(nodeId, notes)
        }
    }
}
