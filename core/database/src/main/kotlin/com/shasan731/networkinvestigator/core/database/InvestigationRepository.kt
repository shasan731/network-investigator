package com.shasan731.networkinvestigator.core.database

import com.shasan731.networkinvestigator.core.model.InvestigationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InvestigationRepository(private val dao: InvestigationDao, private val json: Json = Json { ignoreUnknownKeys = true }) {
    suspend fun save(snapshot: InvestigationSnapshot) {
        val target = TargetEntity(originalValue = snapshot.target, normalizedValue = snapshot.target, targetType = "parsed", createdAt = snapshot.startedAtEpochMs)
        val entity = InvestigationEntity(snapshot.id, 0, snapshot.profile.name, snapshot.startedAtEpochMs, snapshot.completedAtEpochMs, json.encodeToString(snapshot))
        val tasks = snapshot.cards.map { DiagnosticTaskEntity(investigationId = snapshot.id, taskType = it.taskType.name, status = it.status.name, source = it.source.name, startedAt = it.startedAtEpochMs, durationMs = it.durationMs) }
        dao.save(target, entity, tasks)
    }
    suspend fun get(id: String): InvestigationSnapshot? = dao.get(id)?.let { json.decodeFromString<InvestigationSnapshot>(it.snapshotJson) }
    fun history(limit: Int = 50, offset: Int = 0): Flow<List<InvestigationSnapshot>> = dao.observeHistory(limit.coerceIn(1, 10_000), offset.coerceAtLeast(0)).map { rows -> rows.mapNotNull { runCatching { json.decodeFromString<InvestigationSnapshot>(it.snapshotJson) }.getOrNull() } }
}
