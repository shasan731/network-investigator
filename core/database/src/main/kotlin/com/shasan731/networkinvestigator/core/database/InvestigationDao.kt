package com.shasan731.networkinvestigator.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestigationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTarget(target: TargetEntity): Long
    @Query("SELECT id FROM TargetEntity WHERE normalizedValue = :value") suspend fun targetId(value: String): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertInvestigation(investigation: InvestigationEntity)
    @Insert suspend fun insertTasks(tasks: List<DiagnosticTaskEntity>)
    @Query("SELECT * FROM InvestigationEntity ORDER BY startedAt DESC LIMIT :limit OFFSET :offset") fun observeHistory(limit: Int, offset: Int): Flow<List<InvestigationEntity>>
    @Query("SELECT * FROM InvestigationEntity WHERE id = :id") suspend fun get(id: String): InvestigationEntity?
    @Query("DELETE FROM InvestigationEntity WHERE startedAt < :cutoff") suspend fun deleteOlderThan(cutoff: Long): Int
    @Transaction suspend fun save(target: TargetEntity, investigation: InvestigationEntity, tasks: List<DiagnosticTaskEntity>) {
        val inserted = insertTarget(target)
        val id = if (inserted == -1L) requireNotNull(targetId(target.normalizedValue)) else inserted
        insertInvestigation(investigation.copy(targetId = id)); insertTasks(tasks)
    }
}

@Dao
interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(incident: IncidentEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(reference: IncidentInvestigationCrossRef)
    @Query("SELECT * FROM IncidentEntity ORDER BY startedAt DESC") fun observeAll(): Flow<List<IncidentEntity>>
    @Query("SELECT * FROM IncidentEntity WHERE id = :id") suspend fun get(id: String): IncidentEntity?
    @Query("SELECT investigationId FROM IncidentInvestigationCrossRef WHERE incidentId = :id") suspend fun investigationIds(id: String): List<String>
    @Insert suspend fun addAttachment(attachment: AttachmentEntity): Long
    @Query("SELECT * FROM AttachmentEntity WHERE incidentId = :incidentId ORDER BY addedAt") fun observeAttachments(incidentId: String): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM AttachmentEntity WHERE incidentId = :incidentId ORDER BY addedAt") suspend fun attachments(incidentId: String): List<AttachmentEntity>
    @Query("DELETE FROM AttachmentEntity WHERE id = :id") suspend fun removeAttachment(id: Long)
}

@Dao
interface ConnectivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(session: ConnectivitySessionEntity)
    @Insert suspend fun insertSample(sample: ConnectivitySampleEntity)
    @Query("SELECT * FROM ConnectivitySampleEntity WHERE sessionId = :sessionId AND timestamp >= :since ORDER BY timestamp") suspend fun samples(sessionId: String, since: Long): List<ConnectivitySampleEntity>
    @Query("SELECT * FROM ConnectivitySessionEntity WHERE id = :id") suspend fun getSession(id: String): ConnectivitySessionEntity?
    @Query("SELECT * FROM ConnectivitySessionEntity ORDER BY startedAt DESC") fun observeSessions(): Flow<List<ConnectivitySessionEntity>>
    @Query("SELECT * FROM ConnectivitySampleEntity WHERE sessionId = :sessionId ORDER BY timestamp") fun observeSamples(sessionId: String): Flow<List<ConnectivitySampleEntity>>
    @Query("SELECT * FROM ConnectivitySampleEntity ORDER BY timestamp") fun observeAllSamples(): Flow<List<ConnectivitySampleEntity>>
}

@Dao
interface WifiDao {
    @Insert suspend fun insert(measurement: WifiMeasurementEntity): Long
    @Query("SELECT * FROM WifiMeasurementEntity ORDER BY measuredAt DESC LIMIT :limit") fun observeRecent(limit: Int = 100): Flow<List<WifiMeasurementEntity>>
}

@Dao
interface LanDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(device: LanDeviceEntity): Long
    @Query("SELECT * FROM LanDeviceEntity WHERE ipAddress = :address") suspend fun byAddress(address: String): LanDeviceEntity?
    @Query("SELECT * FROM LanDeviceEntity ORDER BY lastSeen DESC") fun observeAll(): Flow<List<LanDeviceEntity>>
    @Transaction suspend fun saveObservation(device: LanDeviceEntity): Long { val existing = byAddress(device.ipAddress); return upsert(if (existing == null) device else device.copy(id = existing.id, firstSeen = existing.firstSeen, userLabel = existing.userLabel, notes = existing.notes, category = existing.category)) }
}

@Dao
interface ProviderCacheDao {
    @Query("SELECT * FROM ProviderCacheEntity WHERE cacheKey = :key AND expiresAt > :now") suspend fun getFresh(key: String, now: Long): ProviderCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: ProviderCacheEntity)
    @Query("DELETE FROM ProviderCacheEntity WHERE expiresAt <= :now") suspend fun removeExpired(now: Long): Int
}

@Dao
interface SavedRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(request: SavedRequestEntity)
    @Query("SELECT * FROM SavedRequestEntity ORDER BY updatedAt DESC") fun observeAll(): Flow<List<SavedRequestEntity>>
    @Query("DELETE FROM SavedRequestEntity WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface UserNoteDao {
    @Insert suspend fun insert(note: UserNoteEntity): Long
    @Query("SELECT * FROM UserNoteEntity WHERE targetId = :targetId ORDER BY updatedAt DESC") suspend fun forTarget(targetId: Long): List<UserNoteEntity>
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM InvestigationEntity WHERE startedAt < :cutoff") suspend fun deleteOldInvestigations(cutoff: Long): Int
    @Query("DELETE FROM WifiMeasurementEntity WHERE measuredAt < :cutoff") suspend fun deleteOldWifi(cutoff: Long): Int
    @Query("DELETE FROM ConnectivitySessionEntity WHERE startedAt < :cutoff") suspend fun deleteOldSessions(cutoff: Long): Int
    @Query("DELETE FROM ProviderCacheEntity WHERE expiresAt < :now") suspend fun deleteExpiredCache(now: Long): Int
    @Query("DELETE FROM AttachmentEntity") suspend fun clearAttachments()
    @Query("DELETE FROM IncidentEntity") suspend fun clearIncidents()
    @Query("DELETE FROM InvestigationEntity") suspend fun clearInvestigations()
    @Query("DELETE FROM TargetEntity") suspend fun clearTargets()
    @Query("DELETE FROM WifiMeasurementEntity") suspend fun clearWifi()
    @Query("DELETE FROM ConnectivitySessionEntity") suspend fun clearSessions()
    @Query("DELETE FROM LanDeviceEntity") suspend fun clearLan()
    @Query("DELETE FROM SavedRequestEntity") suspend fun clearRequests()
    @Query("DELETE FROM ProviderCacheEntity") suspend fun clearCache()
    @Transaction suspend fun clearAll() { clearAttachments(); clearIncidents(); clearInvestigations(); clearTargets(); clearWifi(); clearSessions(); clearLan(); clearRequests(); clearCache() }
    @Transaction suspend fun enforceRetention(cutoff: Long, now: Long): Int = deleteOldInvestigations(cutoff) + deleteOldWifi(cutoff) + deleteOldSessions(cutoff) + deleteExpiredCache(now)
}
