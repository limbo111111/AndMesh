package com.andmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andmesh.app.data.local.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY isFavorite DESC, lastHeard DESC")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE nodeId = :nodeId LIMIT 1")
    fun getNodeById(nodeId: Long): Flow<NodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: NodeEntity)

    @Query("UPDATE nodes SET isFavorite = :isFavorite WHERE nodeId = :nodeId")
    suspend fun setFavorite(nodeId: Long, isFavorite: Boolean)

    @Query("UPDATE nodes SET notes = :notes WHERE nodeId = :nodeId")
    suspend fun updateNotes(nodeId: Long, notes: String?)
}
