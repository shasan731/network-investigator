package com.shasan731.networkinvestigator.core.database

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestigationRepositoryTest {
    @Test fun `repository saves reopens and streams serialized investigations`() = runBlocking {
        val dao = FakeDao(); val repository = InvestigationRepository(dao)
        val snapshot = InvestigationSnapshot("run-1", "example.com", InvestigationProfile.QUICK_CHECK, 1, 2, listOf(DiagnosticCard(DiagnosticTaskType.DNS, DiagnosticStatus.SUCCESS, "DNS", "1 address", "192.0.2.1", null, ResultSource.ANDROID_SYSTEM, 1, 1)), null)
        repository.save(snapshot)
        assertEquals(snapshot, repository.get("run-1")); assertEquals(listOf(snapshot), repository.history().first())
        assertEquals(1, dao.tasks.size)
    }

    private class FakeDao : InvestigationDao {
        private var nextTarget = 1L; private val targetIds = mutableMapOf<String, Long>(); private val investigations = linkedMapOf<String, InvestigationEntity>(); val tasks = mutableListOf<DiagnosticTaskEntity>(); private val flow = MutableStateFlow<List<InvestigationEntity>>(emptyList())
        override suspend fun insertTarget(target: TargetEntity): Long = if (target.normalizedValue in targetIds) -1 else nextTarget++.also { targetIds[target.normalizedValue] = it }
        override suspend fun targetId(value: String) = targetIds[value]
        override suspend fun insertInvestigation(investigation: InvestigationEntity) { investigations[investigation.id] = investigation; flow.value = investigations.values.sortedByDescending { it.startedAt } }
        override suspend fun insertTasks(tasks: List<DiagnosticTaskEntity>) { this.tasks += tasks }
        override fun observeHistory(limit: Int, offset: Int): Flow<List<InvestigationEntity>> = flow
        override suspend fun get(id: String) = investigations[id]
        override suspend fun deleteOlderThan(cutoff: Long): Int { val ids = investigations.values.filter { it.startedAt < cutoff }.map { it.id }; ids.forEach(investigations::remove); flow.value = investigations.values.toList(); return ids.size }
    }
}
