package com.shasan731.networkinvestigator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.shasan731.networkinvestigator.core.database.*
import com.shasan731.networkinvestigator.core.common.NetworkMetrics
import com.shasan731.networkinvestigator.core.common.PrivacySafeLog
import com.shasan731.networkinvestigator.core.diagnostics.PortRangeParser
import com.shasan731.networkinvestigator.core.model.*
import com.shasan731.networkinvestigator.core.network.*
import com.shasan731.networkinvestigator.core.security.SecretRedactor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.shasan731.networkinvestigator.platform.NetworkSnapshotReader
import com.shasan731.networkinvestigator.platform.NearbyWifiNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

data class FeatureOperationState<T>(val running: Boolean = false, val value: T? = null, val error: String? = null)
data class ReachabilitySample(val attempts: Int, val successes: Int, val latenciesMs: List<Long>, val packetLossPercent: Double, val jitterMs: Double)

@HiltViewModel
class FeatureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiDao: WifiDao,
    private val lanDao: LanDeviceDao,
    private val incidentRepository: IncidentRepository,
    private val connectivityDao: ConnectivityDao,
    private val savedRequestDao: SavedRequestDao,
    private val investigationDao: InvestigationDao,
    private val userNoteDao: UserNoteDao
) : ViewModel() {
    private val jobs = mutableMapOf<String, Job>()
    private val _http = MutableStateFlow(FeatureOperationState<DetailedHttpObservation>()); val http = _http.asStateFlow()
    private val _dns = MutableStateFlow(FeatureOperationState<List<Pair<String, DiagnosticResult<ResolverAnswer>>>>()); val dns = _dns.asStateFlow()
    private val _route = MutableStateFlow(FeatureOperationState<RouteObservation>()); val route = _route.asStateFlow()
    private val _lan = MutableStateFlow(FeatureOperationState<List<LanHostObservation>>()); val lan = _lan.asStateFlow()
    private val _mdns = MutableStateFlow(FeatureOperationState<List<MdnsService>>()); val mdns = _mdns.asStateFlow()
    private val _ssdp = MutableStateFlow(FeatureOperationState<List<SsdpDevice>>()); val ssdp = _ssdp.asStateFlow()
    private val _tls = MutableStateFlow(FeatureOperationState<Map<String, DiagnosticResult<TlsEndpointDetails>>>()); val tls = _tls.asStateFlow()
    private val _services = MutableStateFlow(FeatureOperationState<List<ServiceInspection>>()); val services = _services.asStateFlow()
    private val _rdap = MutableStateFlow(FeatureOperationState<RdapResult>()); val rdap = _rdap.asStateFlow()
    private val _sampling = MutableStateFlow(FeatureOperationState<ReachabilitySample>()); val sampling = _sampling.asStateFlow()
    private val _publicIp = MutableStateFlow(FeatureOperationState<PublicIpObservation>()); val publicIp = _publicIp.asStateFlow()
    val wifiMeasurements = wifiDao.observeRecent(10_000).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _nearbyWifi = MutableStateFlow(FeatureOperationState<List<NearbyWifiNetwork>>()); val nearbyWifi = _nearbyWifi.asStateFlow()
    val lanDevices = lanDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val incidents = incidentRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recorderSessions = connectivityDao.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recorderSamples = connectivityDao.observeAllSamples().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedRequests = savedRequestDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _windowSamples = MutableStateFlow<List<ConnectivitySampleEntity>>(emptyList()); val windowSamples = _windowSamples.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _incidentBundle = MutableStateFlow<IncidentBundle?>(null); val incidentBundle = _incidentBundle.asStateFlow()
    private val _notes = MutableStateFlow<List<UserNoteEntity>>(emptyList()); val notes = _notes.asStateFlow()

    fun inspectHttp(spec: HttpRequestSpec) = launch("http", _http) { AdvancedHttpInspector().execute(spec).valueOrThrow() }
    fun queryDns(host: String, type: DnsRecordType, resolvers: List<String>) {
        jobs["dns"]?.cancel(); jobs["dns"] = viewModelScope.launch {
            _dns.value = FeatureOperationState(running = true)
            try {
            val query = DnsQuery(host, type)
            val clients = resolvers.filter(String::isNotBlank).map { value -> when { value.equals("android", true) -> AndroidPlatformDnsResolver(context); value.equals("system", true) -> object : DnsResolver { override val name = "Android system resolver"; override suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer> { if (query.type !in setOf(DnsRecordType.A, DnsRecordType.AAAA)) return DiagnosticResult.Unsupported("Android's system hostname resolver exposes only address records."); val started = System.currentTimeMillis(); return try { val begin = System.nanoTime(); val records = java.net.InetAddress.getAllByName(query.name).filter { query.type == DnsRecordType.A && it is java.net.Inet4Address || query.type == DnsRecordType.AAAA && it is java.net.Inet6Address }.map { DnsRecord(query.name, if (it is java.net.Inet4Address) 1 else 28, 0, it.hostAddress) }; DiagnosticResult.Success(ResolverAnswer(name, records, (System.nanoTime() - begin) / 1_000_000, null), started, System.currentTimeMillis(), ResultSource.ANDROID_SYSTEM) } catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_NXDOMAIN, "System resolution failed.", error.message, true, ResultSource.ANDROID_SYSTEM) } } }; value.startsWith("https://") -> DnsOverHttpsResolver(value); value.startsWith("tls://") -> DnsOverTlsResolver(URI(value).host, URI(value).port.takeIf { it > 0 } ?: 853); value.startsWith('[') -> UdpTcpDnsResolver(value.substringAfter('[').substringBefore(']'), value.substringAfter("]:", "53").toIntOrNull() ?: 53); TargetParser.parseIpv6(value) != null -> UdpTcpDnsResolver(value); else -> value.substringBefore(':').let { server -> UdpTcpDnsResolver(server, value.substringAfter(':', "53").toIntOrNull() ?: 53) } } }
            _dns.value = FeatureOperationState(value = clients.map { it.name to it.query(query) })
            } catch (cancelled: kotlinx.coroutines.CancellationException) { _dns.value = FeatureOperationState(error = "Cancelled") }
            catch (error: Exception) { PrivacySafeLog.record("dns", error::class.java.simpleName); _dns.value = FeatureOperationState(error = error.message ?: "Invalid resolver configuration") }
        }
    }
    fun trace(host: String) = launch("route", _route) { ProcessRouteProbe().trace(host).valueOrThrow() }
    fun scanLan(cidr: String) {
        val parsed = TargetParser.parse(cidr); if (parsed !is TargetParseResult.Valid || parsed.parsed.target !is InvestigationTarget.Cidr) { _lan.value = FeatureOperationState(error = "Enter a valid IPv4 CIDR."); return }
        val target = parsed.parsed.target as InvestigationTarget.Cidr
        launch("lan", _lan) {
            val results = BoundedLanScanner().scan(target)
            results.forEach { host -> lanDao.saveObservation(LanDeviceEntity(ipAddress = host.address, hostname = host.reverseName, macAddress = null, userLabel = null, notes = null, category = "UNKNOWN", confidence = "LOW", firstSeen = host.firstObservedAt, lastSeen = host.firstObservedAt)) }
            results
        }
    }
    fun discoverMdns(serviceType: String = "_services._dns-sd._udp.") = launch("mdns", _mdns) { val lock = context.getSystemService(android.net.wifi.WifiManager::class.java).createMulticastLock("network-investigator-mdns").apply { setReferenceCounted(false); acquire() }; try { val found = mutableListOf<MdnsService>(); kotlinx.coroutines.withTimeoutOrNull(5_000) { AndroidMdnsDiscovery(context).discover(serviceType).collect { if (it !in found) found += it } }; found } finally { if (lock.isHeld) lock.release() } }
    fun discoverSsdp() = launch("ssdp", _ssdp) { val lock = context.getSystemService(android.net.wifi.WifiManager::class.java).createMulticastLock("network-investigator-ssdp").apply { setReferenceCounted(false); acquire() }; try { SsdpDiscovery().discover() } finally { if (lock.isHeld) lock.release() } }
    fun inspectTls(host: String, port: Int) = launch("tls", _tls) { MultiAddressTlsInspector().inspect(host, port) }
    fun inspectServices(host: String, ports: String) {
        val parsed = PortRangeParser.parse(ports); if (parsed.isFailure) { _services.value = FeatureOperationState(error = parsed.exceptionOrNull()?.message); return }
        launch("services", _services) { ServiceInspector().inspect(host, parsed.getOrThrow()) }
    }
    fun lookupRdap(input: String) = launch("rdap", _rdap) { val parsed = TargetParser.parse(input) as? TargetParseResult.Valid ?: error("Enter a valid target"); val address = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { when (val target = parsed.parsed.target) { is InvestigationTarget.Ipv4 -> target.value; is InvestigationTarget.Ipv6 -> target.value; is InvestigationTarget.Domain -> java.net.InetAddress.getAllByName(target.value).first().hostAddress; is InvestigationTarget.Hostname -> java.net.InetAddress.getAllByName(target.value).first().hostAddress; is InvestigationTarget.HostPort -> java.net.InetAddress.getAllByName(target.host).first().hostAddress; is InvestigationTarget.Url -> java.net.InetAddress.getAllByName(URI(target.value).host).first().hostAddress; is InvestigationTarget.Cidr -> target.address } }; BootstrapRdapProvider().lookup(address).valueOrThrow() }
    fun lookupPublicIp() = launch("public-ip", _publicIp) { ConfigurablePublicIpProvider().lookup().valueOrThrow() }
    fun sampleReachability(host: String, port: Int, attempts: Int = 5) = launch("sampling", _sampling) { val latencies = mutableListOf<Long>(); repeat(attempts.coerceIn(2, 20)) { val start = System.nanoTime(); if (runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 2_000) } }.isSuccess) latencies += (System.nanoTime() - start) / 1_000_000; kotlinx.coroutines.delay(150) }; ReachabilitySample(attempts, latencies.size, latencies, NetworkMetrics.packetLossPercent(attempts, latencies.size), NetworkMetrics.jitterMs(latencies)) }
    fun saveWifi(label: String) { val snapshot = NetworkSnapshotReader.read(context)?.wifi ?: return; viewModelScope.launch { wifiDao.insert(WifiMeasurementEntity(label = label.ifBlank { null }, ssid = snapshot.ssid, bssid = snapshot.bssid, rssi = snapshot.rssi, frequencyMhz = snapshot.frequencyMhz, linkSpeedMbps = snapshot.linkSpeedMbps, measuredAt = System.currentTimeMillis())) } }
    fun scanNearbyWifi() = launch("wifi-scan", _nearbyWifi) { NetworkSnapshotReader.requestNearbyScan(context); kotlinx.coroutines.delay(2_000); NetworkSnapshotReader.nearbyWifi(context) }
    fun createIncident(title: String, customer: String, target: String, problem: String, severity: String, notes: String, cause: String, resolution: String, tags: String, networkType: String, deviceInfo: String, investigationId: String?) { viewModelScope.launch { val id = java.util.UUID.randomUUID().toString(); incidentRepository.create(IncidentEntity(id, title.ifBlank { "Network incident" }, customer.ifBlank { null }, target.ifBlank { null }, problem, System.currentTimeMillis(), null, "OPEN", severity, notes.ifBlank { null }, cause.ifBlank { null }, resolution.ifBlank { null }, Json.encodeToString(tags.split(',').map(String::trim).filter(String::isNotBlank)), networkType.ifBlank { null }, deviceInfo.ifBlank { null }), listOfNotNull(investigationId)); _message.value = "Incident created" } }
    fun setIncidentStatus(incident: IncidentEntity, status: String) { viewModelScope.launch { incidentRepository.update(incident.copy(status = status, resolvedAt = if (status in setOf("RESOLVED", "CLOSED")) System.currentTimeMillis() else incident.resolvedAt)); _message.value = "Incident status updated" } }
    fun loadIncidentBundle(id: String) { viewModelScope.launch { _incidentBundle.value = incidentRepository.bundle(id) } }
    fun addAttachment(incidentId: String, uri: Uri) { viewModelScope.launch { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; var name = uri.lastPathSegment ?: "attachment"; var type = context.contentResolver.getType(uri) ?: "application/octet-stream"; context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) }; incidentRepository.addAttachment(incidentId, name, uri.toString(), type); _message.value = "Attachment linked" } }
    fun loadRecorderWindow(sessionId: String, minutes: Int?) { viewModelScope.launch { _windowSamples.value = connectivityDao.samples(sessionId, minutes?.let { System.currentTimeMillis() - it * 60_000L } ?: 0) } }
    fun saveRequestTemplate(name: String, spec: HttpRequestSpec) { viewModelScope.launch { val safeHeaders = SecretRedactor.redactHeaders(spec.headers).filterValues { it != "[REDACTED]" }; savedRequestDao.upsert(SavedRequestEntity(java.util.UUID.randomUUID().toString(), name.ifBlank { "Saved request" }, spec.method.name, spec.url, Json.encodeToString(safeHeaders), null, System.currentTimeMillis())); _message.value = "Request template saved without credentials or body." } }
    fun saveNote(target: String, note: String) { if (note.isBlank()) return; viewModelScope.launch { val existing = investigationDao.targetId(target); val id = existing ?: investigationDao.insertTarget(TargetEntity(originalValue = target, normalizedValue = target, targetType = "note", createdAt = System.currentTimeMillis())).takeIf { it > 0 } ?: investigationDao.targetId(target) ?: return@launch; userNoteDao.insert(UserNoteEntity(targetId = id, note = note, updatedAt = System.currentTimeMillis())); _notes.value = userNoteDao.forTarget(id); _message.value = "Note saved" } }
    fun loadNotes(target: String) { viewModelScope.launch { _notes.value = investigationDao.targetId(target)?.let { userNoteDao.forTarget(it) }.orEmpty() } }
    fun cancel(key: String) { jobs[key]?.cancel() }

    private fun <T> launch(key: String, state: MutableStateFlow<FeatureOperationState<T>>, block: suspend () -> T) {
        jobs[key]?.cancel(); jobs[key] = viewModelScope.launch { state.value = FeatureOperationState(running = true); state.value = try { FeatureOperationState(value = block()) } catch (_: kotlinx.coroutines.CancellationException) { FeatureOperationState(error = "Cancelled") } catch (error: Exception) { PrivacySafeLog.record(key, error::class.java.simpleName); FeatureOperationState(error = error.message ?: "Operation failed") } }
    }
    private fun <T> DiagnosticResult<T>.valueOrThrow(): T = when (this) { is DiagnosticResult.Success -> data; is DiagnosticResult.Partial -> data ?: error(warnings.joinToString { it.message }); is DiagnosticResult.Failure -> error(message); is DiagnosticResult.Unsupported -> error(reason); is DiagnosticResult.Cancelled -> error(reason) }
}
