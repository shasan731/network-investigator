package com.shasan731.networkinvestigator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shasan731.networkinvestigator.core.database.InvestigationRepository
import com.shasan731.networkinvestigator.core.database.IncidentDao
import com.shasan731.networkinvestigator.core.database.IncidentEntity
import com.shasan731.networkinvestigator.core.database.IncidentInvestigationCrossRef
import com.shasan731.networkinvestigator.core.database.RetentionRepository
import com.shasan731.networkinvestigator.core.datastore.AppPreferences
import com.shasan731.networkinvestigator.core.diagnostics.InvestigationEngine
import com.shasan731.networkinvestigator.core.diagnostics.PortRangeParser
import com.shasan731.networkinvestigator.core.diagnostics.PortScanner
import com.shasan731.networkinvestigator.core.model.*
import com.shasan731.networkinvestigator.core.security.KeystoreCipher
import com.shasan731.networkinvestigator.core.common.PrivacySafeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.content.Intent

data class InvestigationUiState(val input: String = "", val profile: InvestigationProfile = InvestigationProfile.QUICK_CHECK, val running: Boolean = false, val validationError: String? = null, val snapshot: InvestigationSnapshot? = null)

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext private val context: Context, private val engine: InvestigationEngine, private val repository: InvestigationRepository, private val incidentDao: IncidentDao, private val retentionRepository: RetentionRepository, val preferences: AppPreferences) : ViewModel() {
    private val _investigation = MutableStateFlow(InvestigationUiState())
    val investigation = _investigation.asStateFlow()
    val history = repository.history(10_000).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.shasan731.networkinvestigator.core.datastore.UserSettings())
    private var investigationJob: Job? = null
    private val _portResult = MutableStateFlow<List<PortObservation>>(emptyList())
    val portResult = _portResult.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    init { viewModelScope.launch { retentionRepository.enforce(preferences.settings.first().retentionDays) } }

    fun setInput(value: String) = _investigation.update { it.copy(input = value, validationError = null) }
    fun setProfile(value: InvestigationProfile) = _investigation.update { it.copy(profile = value) }
    fun useSnapshot(snapshot: InvestigationSnapshot) = _investigation.update { it.copy(input = snapshot.target, profile = snapshot.profile, snapshot = snapshot, validationError = null) }

    fun run(inputOverride: String? = null, profileOverride: InvestigationProfile? = null) {
        val input = inputOverride ?: _investigation.value.input; val profile = profileOverride ?: _investigation.value.profile
        when (val result = TargetParser.parse(input)) {
            is TargetParseResult.Invalid -> _investigation.update { it.copy(input = input, profile = profile, validationError = result.error.message, snapshot = null) }
            is TargetParseResult.Valid -> {
                investigationJob?.cancel()
                investigationJob = viewModelScope.launch {
                    _investigation.value = InvestigationUiState(input, profile, running = true)
                    try {
                        val snapshot = engine.investigate(result.parsed, profile)
                        repository.save(snapshot)
                        _investigation.value = InvestigationUiState(input, profile, snapshot = snapshot)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        _investigation.update { it.copy(running = false, validationError = "Investigation cancelled.") }
                    } catch (error: Exception) {
                        _investigation.update { it.copy(running = false, validationError = "Investigation could not be saved: ${error.message ?: "unknown error"}") }
                    }
                }
            }
        }
    }
    fun cancel() { investigationJob?.cancel() }
    fun scanPorts(host: String, input: String) {
        val target = TargetParser.parse(host)
        val parsedPorts = PortRangeParser.parse(input)
        if (target !is TargetParseResult.Valid || target.parsed.target is InvestigationTarget.Cidr || parsedPorts.isFailure) {
            _message.value = parsedPorts.exceptionOrNull()?.message ?: "Enter a valid single host."
            return
        }
        val resolvedHost = when (val t = target.parsed.target) {
            is InvestigationTarget.Domain -> t.value
            is InvestigationTarget.Hostname -> t.value
            is InvestigationTarget.Ipv4 -> t.value
            is InvestigationTarget.Ipv6 -> t.value
            is InvestigationTarget.HostPort -> t.host
            is InvestigationTarget.Url -> java.net.URI(t.value).host
            is InvestigationTarget.Cidr -> return
        }
        viewModelScope.launch { _portResult.value = PortScanner().scan(resolvedHost, parsedPorts.getOrThrow()) }
    }
    fun createIncident(title: String) {
        val snapshot = _investigation.value.snapshot ?: history.value.firstOrNull() ?: run { _message.value = "Run or open an investigation first."; return }
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString(); val now = System.currentTimeMillis()
            incidentDao.upsert(IncidentEntity(id, title.ifBlank { "Network incident" }, null, snapshot.target, "Evidence captured from investigation", now, null, "OPEN", "MEDIUM", null, snapshot.diagnosis?.title, null, "[]"))
            incidentDao.link(IncidentInvestigationCrossRef(id, snapshot.id)); _message.value = "Incident created and investigation linked."
        }
    }
    fun enforceRetention() { viewModelScope.launch { retentionRepository.enforce(settings.value.retentionDays); _message.value = "Retention policy applied." } }
    fun clearAllData() { viewModelScope.launch { context.contentResolver.persistedUriPermissions.forEach { permission -> val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0); if (flags != 0) runCatching { context.contentResolver.releasePersistableUriPermission(permission.uri, flags) } }; retentionRepository.clearAll(); preferences.clearAll(); runCatching { KeystoreCipher().deleteKey() }; PrivacySafeLog.clear(); _investigation.value = InvestigationUiState(); _message.value = "All local records, URI grants, preferences, encryption key, and privacy-safe logs cleared." } }
}
