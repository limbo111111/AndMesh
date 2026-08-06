package com.andmesh.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
    private var meshSdrService: MeshSdrService? = null
    private var isBound by mutableStateOf(false)
    private var hackRfLinked by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshSdrService.LocalBinder
            meshSdrService = binder.getService()
            isBound = true

            // Set the listener to update the UI when the device becomes ready
            meshSdrService?.onDeviceReadyListener = { isReady ->
                hackRfLinked = isReady
            }
            // Check initial state
            hackRfLinked = meshSdrService?.isDeviceReady == true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            meshSdrService?.onDeviceReadyListener = null
            meshSdrService = null
            hackRfLinked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, MeshSdrService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

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
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
