package com.shasan731.networkinvestigator.recording

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import com.shasan731.networkinvestigator.MainActivity
import com.shasan731.networkinvestigator.core.database.*
import com.shasan731.networkinvestigator.platform.NetworkSnapshotReader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ConnectivityRecorderService : Service() {
    @Inject lateinit var dao: ConnectivityDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionId: String? = null
    private var startedAt = 0L
    private var probeTarget: String? = null
    private var intervalMs: Long = 10_000
    @Volatile private var sessionReady = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = recordTransition()
        override fun onLost(network: Network) = recordTransition()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = recordTransition()
    }

    override fun onCreate() { super.onCreate(); createChannel(); getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecording(); return START_NOT_STICKY }
        if (sessionId == null) {
            probeTarget = intent?.getStringExtra(EXTRA_TARGET)?.takeIf(String::isNotBlank)
            intervalMs = (intent?.getLongExtra(EXTRA_INTERVAL_MS, 10_000) ?: 10_000).coerceIn(5_000, 60_000)
            sessionId = UUID.randomUUID().toString(); startedAt = System.currentTimeMillis()
            startForeground(NOTIFICATION_ID, notification("Recording connectivity evidence"))
            scope.launch {
                dao.upsertSession(ConnectivitySessionEntity(requireNotNull(sessionId), "LIVE", startedAt, null, "Live incident"))
                sessionReady = true
                while (isActive) {
                    val snapshot = NetworkSnapshotReader.read(this@ConnectivityRecorderService)
                    val probe = probeTarget?.let { target -> sampleTarget(target) }
                    dao.insertSample(ConnectivitySampleEntity(sessionId = requireNotNull(sessionId), timestamp = System.currentTimeMillis(), transport = snapshot?.transport, latencyMs = probe?.first, packetLossPercent = probe?.second, validated = snapshot?.validated))
                    delay(intervalMs)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }; scope.cancel(); super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopRecording()
    }
    private fun stopRecording() {
        val id = sessionId
        if (id != null) scope.launch { dao.upsertSession(ConnectivitySessionEntity(id, "LIVE", startedAt, System.currentTimeMillis(), "Live incident")); withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Connectivity recording", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ConnectivityRecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(com.shasan731.networkinvestigator.R.drawable.ic_launcher_foreground).setContentTitle("Network Investigator").setContentText(text).setContentIntent(open).setOngoing(true).addAction(0, "Stop", stop).build()
    }
    private fun recordTransition() { val id = sessionId?.takeIf { sessionReady } ?: return; scope.launch { val snapshot = NetworkSnapshotReader.read(this@ConnectivityRecorderService); dao.insertSample(ConnectivitySampleEntity(sessionId = id, timestamp = System.currentTimeMillis(), transport = snapshot?.transport, latencyMs = null, packetLossPercent = null, validated = snapshot?.validated)) } }
    private fun sampleTarget(target: String): Pair<Long?, Double> { var successes = 0; val latencies = mutableListOf<Long>(); repeat(3) { val start = System.nanoTime(); val success = runCatching { val address = java.net.InetAddress.getAllByName(target).first(); java.net.Socket().use { it.connect(java.net.InetSocketAddress(address, 443), 2_000) } }.isSuccess; if (success) { successes++; latencies += (System.nanoTime() - start) / 1_000_000 } }; return latencies.takeIf { it.isNotEmpty() }?.average()?.toLong() to ((3 - successes) * 100.0 / 3) }
    companion object { const val ACTION_START = "recording.start"; const val ACTION_STOP = "recording.stop"; const val EXTRA_TARGET = "recording.target"; const val EXTRA_INTERVAL_MS = "recording.interval"; private const val CHANNEL_ID = "connectivity_recorder"; private const val NOTIFICATION_ID = 731 }
}
