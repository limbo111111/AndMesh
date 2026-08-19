package com.andmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packetId: Long = 0,
    val fromNodeId: Long,
    val fromNodeName: String,
    val toNodeId: Long = 0xFFFFFFFFL,
    val channelName: String = "LongFast",
    val portNum: Int = 1,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val hopLimit: Int = 3,
    val hopStart: Int = 3
)
