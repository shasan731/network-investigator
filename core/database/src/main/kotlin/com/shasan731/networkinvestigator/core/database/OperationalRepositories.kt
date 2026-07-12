package com.shasan731.networkinvestigator.core.database

import com.shasan731.networkinvestigator.core.model.InvestigationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

data class IncidentBundle(val incident: IncidentEntity, val investigations: List<InvestigationSnapshot>, val attachments: List<AttachmentEntity>)

class IncidentRepository(private val incidentDao: IncidentDao, private val investigationDao: InvestigationDao, private val json: Json = Json { ignoreUnknownKeys = true }) {
    suspend fun create(incident: IncidentEntity, investigationIds: List<String>) { incidentDao.upsert(incident); investigationIds.distinct().forEach { incidentDao.link(IncidentInvestigationCrossRef(incident.id, it)) } }
    suspend fun update(incident: IncidentEntity) = incidentDao.upsert(incident)
    fun observeAll() = incidentDao.observeAll()
    suspend fun addAttachment(incidentId: String, displayName: String, uri: String, mimeType: String) = incidentDao.addAttachment(AttachmentEntity(incidentId = incidentId, displayName = displayName, persistedUri = uri, mimeType = mimeType, addedAt = System.currentTimeMillis()))
    suspend fun bundle(id: String): IncidentBundle? { val incident = incidentDao.get(id) ?: return null; val investigations = incidentDao.investigationIds(id).mapNotNull { investigationDao.get(it)?.snapshotJson?.let { raw -> runCatching { json.decodeFromString<InvestigationSnapshot>(raw) }.getOrNull() } }; return IncidentBundle(incident, investigations, incidentDao.attachments(id)) }
}

class RetentionRepository(private val dao: MaintenanceDao) {
    suspend fun enforce(retentionDays: Int?, now: Long = System.currentTimeMillis()): Int = retentionDays?.let { dao.enforceRetention(now - it * 86_400_000L, now) } ?: dao.deleteExpiredCache(now)
    suspend fun clearAll() = dao.clearAll()
}
