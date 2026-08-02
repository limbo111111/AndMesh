package com.andmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.andmesh.app.ui.tactical.MeshNode
import com.andmesh.app.ui.tactical.TacticalMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dummyNodes = listOf(
            MeshNode("Alpha Base", "0 HOPS"),
            MeshNode("Bravo Team", "1 HOP"),
            MeshNode("Charlie Patrol", "2 HOPS")
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TacticalMainScreen(
                        hackRfLinked = false,
                        frequencyMhz = "868.125",
                        channelName = "LongFast",
                        signalDbm = "-95 dBm",
                        spreadingFactor = "SF11",
                        nodes = dummyNodes,
                        onSendClick = { /* TODO */ }
                    )
                }
            }
        }
    }
}
