package com.andmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey
    val nodeId: Long,
    val hexId: String,
    val longName: String,
    val shortName: String,
    val hwModel: String = "UNKNOWN",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Int? = null,
    val batteryLevel: Int? = null,
    val voltage: Float? = null,
    val hopsAway: Int = 0,
    val lastHeard: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val notes: String? = null
)
