package com.andmesh.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.andmesh.app.data.local.AppDatabase
import com.andmesh.app.data.repository.MeshRepository
import com.andmesh.app.mesh.MeshRouter

class MeshSdrService : Service() {

    private val binder = LocalBinder()
    var hackRfRepository: HackRfRepository? = null
        private set
    lateinit var meshRepository: MeshRepository
        private set
    lateinit var meshRouter: MeshRouter
        private set

    var isDeviceReady = false
        private set

    // Optional callback to notify the Activity when the device becomes ready.
    var onDeviceReadyListener: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): MeshSdrService = this@MeshSdrService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()

        val db = AppDatabase.getInstance(this)
        meshRepository = MeshRepository(db)
        meshRouter = MeshRouter(this, meshRepository) { hackRfRepository }

        RtlSdrNative.packetListener = { packetJson ->
            meshRouter.onPacketReceived(packetJson)
        }

        // Initialize hardware
        hackRfRepository = HackRfRepository(
            context = this,
            onDeviceReady = {
                isDeviceReady = true
                onDeviceReadyListener?.invoke(true)
                it.startReceiving()
            },
            onDeviceError = { error ->
                isDeviceReady = false
                onDeviceReadyListener?.invoke(false)
                Log.e("MeshSdrService", "Device error: $error")
            }
        )
        hackRfRepository?.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        hackRfRepository?.close()
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("MeshSDR")
            .setContentText("SDR Link Active")
            .setSmallIcon(R.drawable.ic_mesh_notification)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MeshSDR Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "MeshSdrServiceChannel"
    }
}
