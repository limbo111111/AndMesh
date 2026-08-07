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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.andmesh.app.data.AppDatabase
import com.andmesh.app.ui.tactical.TacticalMainScreen
import com.andmesh.app.ui.tactical.TacticalViewModel

class MainActivity : ComponentActivity() {
    private var meshSdrService: MeshSdrService? = null
    private var isBound by mutableStateOf(false)
    private var hackRfLinked by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshSdrService.LocalBinder
            meshSdrService = binder.getService()
            isBound = true

            meshSdrService?.onDeviceReadyListener = { isReady ->
                hackRfLinked = isReady
            }
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

        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TacticalViewModel(database.nodeDao()) as T
            }
        }).get(TacticalViewModel::class.java)

        RtlSdrNative.packetListener = { packetInfo ->
            viewModel.onPacketReceived(packetInfo)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

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
                        signalDbm = "-95 dBm", // We don't have SNR/RSSI from DSP yet
                        spreadingFactor = "SF11",
                        nodes = uiState.nodes,
                        messages = uiState.messages,
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
