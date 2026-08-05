package com.andmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.andmesh.app.ui.tactical.MeshNode
import com.andmesh.app.ui.tactical.TacticalMainScreen

class MainActivity : ComponentActivity() {
    private var hackRfRepository: HackRfRepository? = null
    private var hackRfLinked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hackRfRepository = HackRfRepository(
            context = this,
            onDeviceReady = {
                hackRfLinked = true
                it.startReceiving()
            },
            onDeviceError = { error ->
                hackRfLinked = false
                // Optional: show error message in UI
            }
        )

        hackRfRepository?.initialize()

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
                    val context = LocalContext.current
                    TacticalMainScreen(
                        hackRfLinked = hackRfLinked,
                        frequencyMhz = "869.525",
                        channelName = "LongFast",
                        signalDbm = "-95 dBm",
                        spreadingFactor = "SF11",
                        nodes = dummyNodes,
                        onSendClick = {
                            Toast.makeText(context, "Send clicked! TX path not yet implemented.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hackRfRepository?.close()
    }
}
