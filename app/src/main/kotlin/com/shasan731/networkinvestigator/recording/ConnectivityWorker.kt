package com.shasan731.networkinvestigator.recording

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shasan731.networkinvestigator.core.database.*
import com.shasan731.networkinvestigator.platform.NetworkSnapshotReader

class ConnectivityWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val db = Room.databaseBuilder(applicationContext, NetworkInvestigatorDatabase::class.java, "network-investigator.db").addMigrations(NetworkInvestigatorDatabase.MIGRATION_1_2).build()
        val id = "periodic-monitoring"; val now = System.currentTimeMillis(); val snapshot = NetworkSnapshotReader.read(applicationContext)
        val existing = db.connectivityDao().getSession(id)
        db.connectivityDao().upsertSession(ConnectivitySessionEntity(id, "STANDARD", existing?.startedAt ?: now, null, "Periodic monitoring"))
        db.connectivityDao().insertSample(ConnectivitySampleEntity(sessionId = id, timestamp = now, transport = snapshot?.transport, latencyMs = null, packetLossPercent = null, validated = snapshot?.validated))
        db.close(); Result.success()
    }.getOrElse { Result.retry() }
}
