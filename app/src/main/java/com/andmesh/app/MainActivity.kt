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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
        if (!isGranted) {
            Toast.makeText(this, "Benachrichtigungen deaktiviert. Service läuft im Hintergrund.", Toast.LENGTH_SHORT).show()
        }
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

        val db = com.andmesh.app.data.local.AppDatabase.getInstance(this)
        val repository = com.andmesh.app.data.repository.MeshRepository(db)

        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TacticalViewModel(repository) as T
            }
        }).get(TacticalViewModel::class.java)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf("MAIN") } // "MAIN", "SETTINGS", "NODE_DETAIL"

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentFreq = meshSdrService?.hackRfRepository?.frequencyHz ?: 869525000L
                    val currentFreqMhz = currentFreq / 1_000_000.0
                    val currentChannel = meshSdrService?.hackRfRepository?.channelName ?: "LongFast"
                    val currentPsk = meshSdrService?.hackRfRepository?.pskBase64 ?: ""
                    val isRelayEnabled = meshSdrService?.meshRouter?.isRelayEnabled ?: true

                    when (currentScreen) {
                        "SETTINGS" -> {
                            com.andmesh.app.ui.tactical.TacticalSettingsScreen(
                                currentFreqHz = currentFreq,
                                onFrequencySelected = { freq ->
                                    meshSdrService?.hackRfRepository?.frequencyHz = freq
                                },
                                currentChannelName = currentChannel,
                                onChannelNameChanged = { ch ->
                                    meshSdrService?.hackRfRepository?.channelName = ch
                                },
                                currentPsk = currentPsk,
                                onPskChanged = { psk ->
                                    meshSdrService?.hackRfRepository?.pskBase64 = psk
                                },
                                relayEnabled = isRelayEnabled,
                                onRelayEnabledChanged = { enabled ->
                                    meshSdrService?.meshRouter?.isRelayEnabled = enabled
                                },
                                onBackClick = { currentScreen = "MAIN" },
                                onExitClick = {
                                    val serviceIntent = Intent(this@MainActivity, MeshSdrService::class.java)
                                    stopService(serviceIntent)
                                    finish()
                                }
                            )
                        }
                        "NODE_DETAIL" -> {
                            val node = uiState.selectedNode
                            if (node != null) {
                                com.andmesh.app.ui.tactical.TacticalNodeDetailScreen(
                                    node = node,
                                    messages = uiState.selectedNodeMessages,
                                    onBackClick = {
                                        viewModel.selectNode(null)
                                        currentScreen = "MAIN"
                                    },
                                    onSendMessage = { text ->
                                        val nodeId = meshSdrService?.hackRfRepository?.nodeId ?: 0
                                        viewModel.sendLocalMessage(text, nodeId, node.nodeId)
                                        meshSdrService?.hackRfRepository?.sendTextMessage(text, nodeId)
                                    },
                                    onToggleFavorite = { fav ->
                                        viewModel.toggleFavorite(node.nodeId, fav)
                                    },
                                    onUpdateNotes = { notes ->
                                        viewModel.updateNotes(node.nodeId, notes)
                                    }
                                )
                            } else {
                                currentScreen = "MAIN"
                            }
                        }
                        else -> {
                            TacticalMainScreen(
                                hackRfLinked = hackRfLinked,
                                frequencyMhz = String.format("%.3f", currentFreqMhz),
                                channelName = currentChannel,
                                signalDbm = "N/A",
                                spreadingFactor = "SF11",
                                nodes = uiState.nodes,
                                messages = uiState.messages,
                                onSendClick = { text ->
                                    val nodeId = meshSdrService?.hackRfRepository?.nodeId ?: 0
                                    viewModel.sendLocalMessage(text, nodeId)
                                    meshSdrService?.hackRfRepository?.sendTextMessage(text, nodeId)
                                },
                                onNodeClick = { nodeId ->
                                    viewModel.selectNode(nodeId)
                                    currentScreen = "NODE_DETAIL"
                                },
                                onSettingsClick = {
                                    currentScreen = "SETTINGS"
                                }
                            )
                        }
                    }
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

