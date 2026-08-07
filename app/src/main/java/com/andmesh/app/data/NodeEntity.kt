package com.andmesh.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val hops: Int,
    val lastHeard: Long
)
