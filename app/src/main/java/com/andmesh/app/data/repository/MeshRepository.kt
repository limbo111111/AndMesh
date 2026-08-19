package com.andmesh.app.data.repository

import com.andmesh.app.data.local.AppDatabase
import com.andmesh.app.data.local.entity.MessageEntity
import com.andmesh.app.data.local.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

class MeshRepository(private val database: AppDatabase) {

    val allNodes: Flow<List<NodeEntity>> = database.nodeDao().getAllNodes()
    val allMessages: Flow<List<MessageEntity>> = database.messageDao().getAllMessages()

    fun getNodeById(nodeId: Long): Flow<NodeEntity?> {
        return database.nodeDao().getNodeById(nodeId)
    }

    fun getMessagesForNode(nodeId: Long): Flow<List<MessageEntity>> {
        return database.messageDao().getMessagesForNode(nodeId)
    }

    suspend fun upsertNode(node: NodeEntity) {
        database.nodeDao().upsertNode(node)
    }

    suspend fun updateNodeLastHeard(nodeId: Long, time: Long, hops: Int) {
        database.nodeDao().updateLastHeard(nodeId, time, hops)
    }

    suspend fun setNodeFavorite(nodeId: Long, isFavorite: Boolean) {
        database.nodeDao().setFavorite(nodeId, isFavorite)
    }

    suspend fun updateNodeNotes(nodeId: Long, notes: String?) {
        database.nodeDao().updateNotes(nodeId, notes)
    }

    suspend fun insertMessage(message: MessageEntity): Long {
        return database.messageDao().insertMessage(message)
    }

    suspend fun clearAll() {
        database.messageDao().clearAllMessages()
        database.nodeDao().clearAllNodes()
    }
}
