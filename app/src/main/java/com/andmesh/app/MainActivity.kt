package com.andmesh.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.andmesh.app.data.AppDatabase
import com.andmesh.app.data.NodeEntity
import com.andmesh.app.ui.tactical.MeshNode
import com.andmesh.app.ui.tactical.TacticalMainScreen
import java.util.UUID

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        startMeshSdrService()
    }

    private fun startMeshSdrService() {
        val serviceIntent = Intent(this, MeshSdrService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                startMeshSdrService()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMeshSdrService()
        }

        val database = AppDatabase.getDatabase(this)
        val nodeDao = database.nodeDao()

        RtlSdrNative.packetListener = { packetInfo ->
            lifecycleScope.launch {
                val entity = NodeEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Node ${System.currentTimeMillis() % 1000}",
                    hops = 0,
                    lastHeard = System.currentTimeMillis()
                )
                nodeDao.insertNode(entity)
            }
        }

        setContent {
            val nodesFlow by nodeDao.getAllNodes().collectAsState(initial = emptyList())
            val displayNodes = nodesFlow.map { entity ->
                MeshNode(entity.name, "${entity.hops} HOPS")
            }

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
                        nodes = displayNodes,
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
