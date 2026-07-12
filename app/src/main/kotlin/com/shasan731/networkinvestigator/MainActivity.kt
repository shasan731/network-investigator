package com.shasan731.networkinvestigator

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.shasan731.networkinvestigator.core.datastore.ThemePreference
import com.shasan731.networkinvestigator.core.model.*
import com.shasan731.networkinvestigator.core.network.*
import com.shasan731.networkinvestigator.core.reporting.ReportExporter
import com.shasan731.networkinvestigator.core.security.BiometricLock
import com.shasan731.networkinvestigator.core.ui.DiagnosticResultCard
import com.shasan731.networkinvestigator.core.ui.NetworkInvestigatorTheme
import com.shasan731.networkinvestigator.feature.compare.NetworkCompareFeature
import com.shasan731.networkinvestigator.feature.dns.DnsDetectiveFeature
import com.shasan731.networkinvestigator.feature.evidence.EvidenceCollectorFeature
import com.shasan731.networkinvestigator.feature.lan.LanExplorerFeature
import com.shasan731.networkinvestigator.feature.networktools.NetworkToolsFeature
import com.shasan731.networkinvestigator.feature.ports.PortInspectorFeature
import com.shasan731.networkinvestigator.feature.recorder.ConnectivityRecorderFeature
import com.shasan731.networkinvestigator.feature.route.RouteInvestigatorFeature
import com.shasan731.networkinvestigator.feature.targetintelligence.TargetIntelligenceFeature
import com.shasan731.networkinvestigator.feature.tls.TlsInvestigatorFeature
import com.shasan731.networkinvestigator.feature.website.WebsiteInvestigatorFeature
import com.shasan731.networkinvestigator.feature.wifi.WifiDiagnosticsFeature
import com.shasan731.networkinvestigator.platform.NetworkSnapshotReader
import com.shasan731.networkinvestigator.recording.ConnectivityRecorderService
import com.shasan731.networkinvestigator.recording.ConnectivityWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.work.*

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val featureViewModel: FeatureViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            var unlocked by remember { mutableStateOf(false) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (settings.theme) { ThemePreference.SYSTEM -> systemDark; ThemePreference.DARK -> true; ThemePreference.LIGHT -> false }
            NetworkInvestigatorTheme(dark, settings.dynamicColor) { if (!settings.biometricLock || unlocked) InvestigatorApp(viewModel, featureViewModel) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button({ BiometricLock(this@MainActivity).authenticate({ unlocked = true }, {}) }) { Text("Unlock saved network evidence") } } }
        }
    }
}

private data class TopDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val topDestinations = listOf(
    TopDestination("home", "Home", Icons.Default.Home), TopDestination("investigate", "Investigate", Icons.Default.Search),
    TopDestination("tools", "Tools", Icons.Default.Build), TopDestination("recorder", "Recorder", Icons.Default.FiberManualRecord),
    TopDestination("history", "History", Icons.Default.History), TopDestination("settings", "Settings", Icons.Default.Settings)
)
private val moduleSpecs = listOf(TargetIntelligenceFeature.spec, NetworkToolsFeature.spec, WebsiteInvestigatorFeature.spec, DnsDetectiveFeature.spec, LanExplorerFeature.spec, WifiDiagnosticsFeature.spec, RouteInvestigatorFeature.spec, TlsInvestigatorFeature.spec, PortInspectorFeature.spec, NetworkCompareFeature.spec, EvidenceCollectorFeature.spec)

@Composable
private fun InvestigatorApp(vm: MainViewModel, features: FeatureViewModel) {
    val nav = rememberNavController(); val entry by nav.currentBackStackEntryAsState(); val route = entry?.destination?.route
    BoxWithConstraints { val wide = maxWidth >= 840.dp
    Scaffold(
        topBar = { TopAppBar(title = { Text("Network Investigator") }) },
        bottomBar = { if (!wide) {
            NavigationBar {
                topDestinations.forEach { destination ->
                    NavigationBarItem(selected = route == destination.route, onClick = { nav.navigate(destination.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label, maxLines = 1) })
                }
            }
        } }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
        if (wide) NavigationRail { topDestinations.forEach { destination -> NavigationRailItem(selected = route == destination.route, onClick = { nav.navigate(destination.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) }) } }
        NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") { HomeScreen({ profile -> vm.setProfile(profile); nav.navigate("investigate") }, { destination -> nav.navigate(destination) }) }
            composable("investigate") { InvestigateScreen(vm) }
            composable("tools") { ToolsScreen { nav.navigate("module/$it") } }
            composable("recorder") { RecorderScreen(features) }
            composable("history") { HistoryScreen(vm) { vm.useSnapshot(it); nav.navigate("investigate") } }
            composable("settings") { SettingsScreen(vm, features) }
            composable("module/{id}") { ModuleScreen(moduleSpecs.firstOrNull { it.route == entry?.arguments?.getString("id") } ?: NetworkToolsFeature.spec, vm, features) }
        }
        }
    }
    }
}

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }

@Composable
private fun HomeScreen(onProfile: (InvestigationProfile) -> Unit, open: (String) -> Unit) {
    Page {
        Text("Evidence before assumptions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Diagnostics run only when you start them. Results stay on this device unless you explicitly export them.")
        listOf(
            "Quick Investigation" to InvestigationProfile.QUICK_CHECK, "Website Down" to InvestigationProfile.WEBSITE_DOWN,
            "DNS Problem" to InvestigationProfile.DNS_PROBLEM, "Internet Problem" to InvestigationProfile.INTERNET_PROBLEM,
            "Local Device" to InvestigationProfile.LOCAL_DEVICE, "TLS Problem" to InvestigationProfile.TLS_PROBLEM
        ).forEach { (label, profile) -> ElevatedCard(onClick = { onProfile(profile) }, modifier = Modifier.fillMaxWidth()) { ListItem(headlineContent = { Text(label) }, supportingContent = { Text(profile.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) }, leadingContent = { Icon(Icons.Default.Troubleshoot, null) }) } }
        Text("More tools", style = MaterialTheme.typography.titleLarge)
        listOf("LAN Explorer" to "module/lan-explorer", "Wi-Fi Diagnostics" to "module/wifi-diagnostics", "Compare Networks" to "module/network-compare", "Create Incident" to "module/evidence-collector").forEach { (label, route) -> ElevatedCard(onClick = { open(route) }, modifier = Modifier.fillMaxWidth()) { ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(Icons.Default.Build, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestigateScreen(vm: MainViewModel) {
    val state by vm.investigation.collectAsState(); var profiles by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Unified investigation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(state.input, vm::setInput, Modifier.fillMaxWidth(), label = { Text("URL, host, IP, host:port, or CIDR") }, singleLine = true, supportingText = { Text("Examples: example.com, https://example.com/path, [2001:db8::1]:443") }) }
        item {
            ExposedDropdownMenuBox(profiles, { profiles = it }) {
                OutlinedTextField(state.profile.name.replace('_', ' '), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Profile") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profiles) })
                ExposedDropdownMenu(profiles, { profiles = false }) { InvestigationProfile.entries.forEach { profile -> DropdownMenuItem({ Text(profile.name.replace('_', ' ')) }, { vm.setProfile(profile); profiles = false }) } }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.run() }, enabled = !state.running) { Text("Run investigation") }; if (state.running) OutlinedButton(vm::cancel) { Text("Cancel") } } }
        state.validationError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.running) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Running bounded concurrent checks…") }
        state.snapshot?.let { snapshot ->
            item { Text("Saved locally • ${snapshot.cards.size} observations", style = MaterialTheme.typography.titleMedium) }
            snapshot.diagnosis?.let { diagnosis -> item { DiagnosisCard(diagnosis) } }
            items(snapshot.cards, key = { it.taskType }) { DiagnosticResultCard(it) }
        }
    }
}

@Composable private fun DiagnosisCard(diagnosis: Diagnosis) { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(diagnosis.title, style = MaterialTheme.typography.titleMedium); Text("Confidence: ${diagnosis.confidence} • Layer: ${diagnosis.probableLayer ?: "Unknown"}"); diagnosis.observedFacts.forEach { Text("• ${it.label}: ${it.value} [${it.source}]") } } } }

@Composable
private fun ToolsScreen(open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Diagnostic tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Each tool uses the same evidence model and remains independently usable.") }
        items(moduleSpecs, key = { it.route }) { spec -> ElevatedCard(onClick = { open(spec.route) }, modifier = Modifier.fillMaxWidth()) { ListItem(headlineContent = { Text(spec.title) }, supportingContent = { Text("${spec.category} • ${spec.description}", maxLines = 2) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) } }
    }
}

@Composable
private fun ModuleScreen(spec: FeatureSpec, vm: MainViewModel, features: FeatureViewModel) {
    var target by remember(spec.route) { mutableStateOf(when (spec.route) { "wifi-diagnostics" -> ""; "website-investigator" -> "https://example.com/"; "lan-explorer" -> "192.168.1.0/24"; else -> "example.com" }) }
    val state by vm.investigation.collectAsState(); val history by vm.history.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(spec.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(spec.description); Text("Tasks: ${spec.tasks.joinToString { it.name.replace('_', ' ') }}", style = MaterialTheme.typography.labelMedium) }
        spec.limitation?.let { item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("Platform/safety limitation: $it", Modifier.padding(14.dp)) } } }
        if (spec.route != "wifi-diagnostics") item { OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Target") }, singleLine = true) }
        when (spec.route) {
            "target-intelligence" -> item { TargetIntelligenceTool(target, vm, features) }
            "network-tools" -> item { NetworkToolkit(target, vm, features) }
            "website-investigator" -> item { WebsiteTool(target, features) }
            "dns-detective" -> item { DnsTool(target, features) }
            "lan-explorer" -> item { LanTool(target, { target = it }, features) }
            "wifi-diagnostics" -> item { WifiTool(features) }
            "route-investigator" -> item { RouteTool(target, features) }
            "tls-investigator" -> item { TlsTool(target, features) }
            "port-inspector" -> item { PortTool(target, features) }
            "network-compare" -> item { CompareCard(history) }
            "evidence-collector" -> item { EvidenceTool(state.snapshot ?: history.firstOrNull(), features) }
            else -> item { Button({ vm.run(target, profileFor(spec)); }, enabled = target.isNotBlank() && !state.running) { Text("Run applicable checks") } }
        }
        if (state.running) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.snapshot?.target == target) items(state.snapshot!!.cards) { DiagnosticResultCard(it) }
    }
}

@Composable private fun WebsiteTool(target: String, features: FeatureViewModel) {
    var method by remember { mutableStateOf(HttpMethod.GET) }; var body by remember { mutableStateOf("") }; var bodyKind by remember { mutableStateOf(BodyKind.JSON) }; var headers by remember { mutableStateOf("") }; var query by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var bearer by remember { mutableStateOf("") }; var follow by remember { mutableStateOf(true) }; var family by remember { mutableStateOf(AddressFamily.SYSTEM) }; var userAgent by remember { mutableStateOf(UserAgentProfile.MOBILE) }; var timeout by remember { mutableStateOf("15000") }; var templateName by remember { mutableStateOf("") }
    val result by features.http.collectAsState(); val saved by features.savedRequests.collectAsState(); val message by features.message.collectAsState()
    fun requestSpec(): HttpRequestSpec { val parsedHeaders = headers.lineSequence().mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim() to line.substring(it + 1).trim() } }.toMap(); val parsedQuery = query.lineSequence().mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it).trim() to line.substring(it + 1).trim() } }.toMap(); return HttpRequestSpec(target, method, parsedHeaders, parsedQuery, body.takeIf(String::isNotBlank), bodyKind, username.takeIf(String::isNotBlank)?.let { it to password }, bearer.takeIf(String::isNotBlank), timeout.toLongOrNull() ?: 15_000, follow, userAgent, family) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).forEach { FilterChip(method == it, { method = it }, { Text(it.name) }) } }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS).forEach { FilterChip(method == it, { method = it }, { Text(it.name) }) } }
        OutlinedTextField(headers, { headers = it }, Modifier.fillMaxWidth(), label = { Text("Headers, one Name: value per line") }, minLines = 2)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Query parameters, one name=value per line") })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(username, { username = it }, Modifier.weight(1f), label = { Text("Basic user") }); OutlinedTextField(password, { password = it }, Modifier.weight(1f), label = { Text("Basic password") }) }
        OutlinedTextField(bearer, { bearer = it }, Modifier.fillMaxWidth(), label = { Text("Bearer token for this request only") })
        if (method !in setOf(HttpMethod.GET, HttpMethod.HEAD)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(BodyKind.JSON, BodyKind.FORM_URL_ENCODED, BodyKind.RAW_TEXT).forEach { FilterChip(bodyKind == it, { bodyKind = it }, { Text(it.name.replace('_', ' ')) }) } }; OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Request body (not persisted)") }, minLines = 3) }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(follow, { follow = it }); Text("Follow redirects") }
        OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, label = { Text("Timeout milliseconds") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AddressFamily.entries.forEach { FilterChip(family == it, { family = it }, { Text(it.name) }) } }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { UserAgentProfile.entries.forEach { FilterChip(userAgent == it, { userAgent = it }, { Text("${it.name} user agent") }) } }
        Button({ features.inspectHttp(requestSpec()) }, enabled = !result.running) { Text("Send diagnostic request") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(templateName, { templateName = it }, Modifier.weight(1f), label = { Text("Template name") }); OutlinedButton({ features.saveRequestTemplate(templateName, requestSpec()) }) { Text("Save safely") } }; Text("Saved templates: ${saved.size}. Credentials and request bodies are never included."); message?.let { Text(it) }
        OperationStatus(result.running, result.error) { features.cancel("http") }
        result.value?.let { value -> SelectionContainer { Text("HTTP ${value.statusCode} • ${value.protocol}\nDNS ${value.timing.dnsMs ?: "n/a"} ms • TCP ${value.timing.connectMs ?: "n/a"} ms • TLS ${value.timing.tlsMs ?: "n/a"} ms • TTFB ${value.timing.timeToFirstByteMs ?: "n/a"} ms • total ${value.timing.totalMs} ms\n${value.responseSize} bytes • SHA-256 ${value.bodySha256}\nRedirects: ${value.redirectChain.joinToString(" → ")}\nMissing security headers: ${value.missingSecurityHeaders.joinToString().ifBlank { "None detected" }}${value.pageMetadata?.let { metadata -> "\nTitle: ${metadata.title ?: "Unavailable"}\nDescription: ${metadata.description ?: "Unavailable"}\nOpenGraph: ${metadata.openGraphTitle ?: "Unavailable"} — ${metadata.openGraphDescription ?: "Unavailable"}" }.orEmpty()}\n\n${value.bodyPreview}${if (value.bodyTruncated) "\n[Preview truncated]" else ""}") } }
    }
}

@Composable private fun TargetIntelligenceTool(target: String, vm: MainViewModel, features: FeatureViewModel) { val investigation by vm.investigation.collectAsState(); val settings by vm.settings.collectAsState(); val rdap by features.rdap.collectAsState(); val notes by features.notes.collectAsState(); var note by remember(target) { mutableStateOf("") }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Button({ vm.run(target, InvestigationProfile.QUICK_CHECK); features.loadNotes(target) }, enabled = !investigation.running) { Text("Resolve and inspect target") }; OutlinedButton({ features.lookupRdap(target) }, enabled = settings.onlineEnrichment && !rdap.running) { Text("Optional RDAP enrichment") }; if (!settings.onlineEnrichment) Text("RDAP is disabled. Enable optional online enrichment in privacy settings to contact the identified provider."); OperationStatus(rdap.running, rdap.error) { features.cancel("rdap") }; rdap.value?.let { Text("Provider: ${it.provider}\nHandle: ${it.handle ?: "Unavailable"}\nNetwork: ${it.networkName ?: "Unavailable"}\nCIDRs: ${it.cidrs.joinToString().ifBlank { "Unavailable" }}\nThis network registration is not exact geographic location.") }; OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Local note") }); Button({ features.saveNote(target, note); note = "" }) { Text("Save note") }; notes.forEach { Text("Note: ${it.note}") } } }

@Composable private fun NetworkToolkit(target: String, vm: MainViewModel, features: FeatureViewModel) { var port by remember { mutableStateOf("443") }; val sampling by features.sampling.collectAsState(); val investigation by vm.investigation.collectAsState(); val settings by vm.settings.collectAsState(); val publicIp by features.publicIp.collectAsState(); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { NetworkSnapshotCard(); OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("TCP sampling port") }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ features.sampleReachability(target, port.toIntOrNull() ?: 443) }, enabled = !sampling.running) { Text("Sample latency/loss") }; OutlinedButton({ vm.run(target, InvestigationProfile.QUICK_CHECK) }, enabled = !investigation.running) { Text("DNS/TCP/HTTP/TLS") } }; OutlinedButton(features::lookupPublicIp, enabled = settings.onlineEnrichment && !publicIp.running) { Text("Optional public IP via identified provider") }; OperationStatus(sampling.running || publicIp.running, sampling.error ?: publicIp.error) { features.cancel("sampling"); features.cancel("public-ip") }; publicIp.value?.let { Text("Public IP: ${it.address} • provider ${it.provider} • source THIRD_PARTY_PROVIDER") }; sampling.value?.let { Text("${it.successes}/${it.attempts} successful • loss ${"%.1f".format(it.packetLossPercent)}% • jitter ${"%.1f".format(it.jitterMs)} ms\nLatencies: ${it.latenciesMs.joinToString()} ms") }; Text("ICMP and path-MTU probing are unsupported when the device exposes no safe unprivileged implementation. TCP/HTTP success is still treated as reachability evidence.") } }

@Composable private fun DnsTool(target: String, features: FeatureViewModel) {
    var type by remember { mutableStateOf(DnsRecordType.A) }; var resolvers by remember { mutableStateOf("android\nsystem\n1.1.1.1\n8.8.8.8\nhttps://cloudflare-dns.com/dns-query") }; val state by features.dns.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(DnsRecordType.A, DnsRecordType.AAAA, DnsRecordType.MX, DnsRecordType.NS, DnsRecordType.TXT).forEach { FilterChip(type == it, { type = it }, { Text(it.name) }) } }; Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(DnsRecordType.CNAME, DnsRecordType.SOA, DnsRecordType.SRV, DnsRecordType.CAA, DnsRecordType.PTR).forEach { FilterChip(type == it, { type = it }, { Text(it.name) }) } }
        OutlinedTextField(resolvers, { resolvers = it }, Modifier.fillMaxWidth(), label = { Text("Resolvers: IP[:port], tls://host, or DoH URL") }, minLines = 3)
        Button({ features.queryDns(target, type, resolvers.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()) }, enabled = !state.running) { Text("Compare resolvers") }
        OperationStatus(state.running, state.error) { features.cancel("dns") }
        state.value?.let { results -> val successful = results.mapNotNull { (name, result) -> if (result is DiagnosticResult.Success) name to result.data.records else null }; if (successful.size >= 2) { val baseline = successful.first(); successful.drop(1).forEach { candidate -> val difference = ResolverComparator.compare(baseline.second, candidate.second); Text("${baseline.first} vs ${candidate.first}: added ${difference.added.size}, removed ${difference.removed.size}, TTL changed ${difference.ttlChanged.size}${if (difference.added.isNotEmpty() || difference.removed.isNotEmpty()) " • possible split DNS or inconsistency" else ""}") } }; results.forEach { (name, result) -> Text("$name\n${when (result) { is DiagnosticResult.Success -> result.data.records.joinToString("\n") { "${it.name} ${it.ttl}s ${it.type} ${it.value}" }.ifBlank { "No answer records" }; is DiagnosticResult.Failure -> "${result.code}: ${result.message}"; is DiagnosticResult.Partial -> "Partial: ${result.warnings.joinToString { it.message }}"; is DiagnosticResult.Unsupported -> result.reason; is DiagnosticResult.Cancelled -> result.reason }}") } }
    }
}

@Composable private fun LanTool(target: String, setTarget: (String) -> Unit, features: FeatureViewModel) {
    val context = LocalContext.current; var confirmed by remember { mutableStateOf(false) }; var localPermission by remember { mutableStateOf(Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == android.content.pm.PackageManager.PERMISSION_GRANTED) }; val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localPermission = it }; val state by features.lan.collectAsState()
    val mdns by features.mdns.collectAsState(); val ssdp by features.ssdp.collectAsState(); val devices by features.lanDevices.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (!target.contains('/')) TextButton({ setTarget("192.168.1.0/24") }) { Text("Use /24 example") }; if (!localPermission) OutlinedButton({ permission.launch("android.permission.ACCESS_LOCAL_NETWORK") }) { Text("Allow local network access") }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmed, { confirmed = it }); Text("I am authorized to scan this local range (maximum 256 addresses)") }; Button({ features.scanLan(target) }, enabled = confirmed && localPermission && !state.running) { Text("Start bounded LAN discovery") }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ features.discoverMdns() }, enabled = localPermission && !mdns.running) { Text("Discover mDNS") }; OutlinedButton(features::discoverSsdp, enabled = localPermission && !ssdp.running) { Text("Discover SSDP") } }; OperationStatus(state.running || mdns.running || ssdp.running, state.error ?: mdns.error ?: ssdp.error) { features.cancel("lan"); features.cancel("mdns"); features.cancel("ssdp") }; state.value?.forEach { Text("${it.address} • ${it.reverseName ?: "hostname unavailable"} • ports ${it.openPorts.joinToString()}") }; mdns.value?.forEach { Text("mDNS: ${it.name} • ${it.type} • ${it.host ?: "address unresolved"}:${it.port ?: "?"}") }; ssdp.value?.forEach { Text("SSDP: ${it.server ?: "Unknown device"} • ${it.location ?: it.sourceAddress}") }; Text("Device history: ${devices.size}"); devices.take(20).forEach { Text("${it.ipAddress} • ${it.hostname ?: "hostname unavailable"} • MAC unavailable • last seen ${it.lastSeen}") }; if (state.value?.isEmpty() == true) Text("No hosts answered the selected TCP probes. This does not prove the range is offline.") }
}

@Composable private fun WifiTool(features: FeatureViewModel) { var label by remember { mutableStateOf("") }; val saved by features.wifiMeasurements.collectAsState(); val nearby by features.nearbyWifi.collectAsState(); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { NetworkSnapshotCard(); OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Room or location label") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ features.saveWifi(label) }) { Text("Save measurement") }; OutlinedButton(features::scanNearbyWifi, enabled = !nearby.running) { Text("One nearby scan") } }; OperationStatus(nearby.running, nearby.error); nearby.value?.let { networks -> val overlap = networks.groupBy { "${it.band} ch ${it.channel}" }.filterValues { it.size > 1 }; Text("Channel overlap: ${overlap.entries.joinToString { "${it.key} (${it.value.size})" }.ifBlank { "No overlap in available scan" }}"); networks.take(20).forEach { Text("${it.ssid} • ${it.rssi} dBm • ${it.band} ch ${it.channel} • ${it.capabilities.take(40)}") } }; Text("Saved measurements", style = MaterialTheme.typography.titleMedium); saved.take(10).forEach { Text("${it.label ?: "Unlabelled"}: ${it.rssi ?: "?"} dBm • ${it.frequencyMhz ?: "?"} MHz • ${it.linkSpeedMbps ?: "?"} Mbps") } } }

@Composable private fun RouteTool(target: String, features: FeatureViewModel) { val state by features.route.collectAsState(); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Button({ features.trace(target) }, enabled = !state.running) { Text("Trace route") }; OperationStatus(state.running, state.error) { features.cancel("route") }; state.value?.let { route -> route.hops.forEach { Text("${it.number}. ${it.address ?: "*"} ${it.latencyMs?.let { ms -> "$ms ms" } ?: ""}") }; route.limitation?.let { Text(it) } } } }

@Composable private fun TlsTool(target: String, features: FeatureViewModel) { var port by remember { mutableStateOf("443") }; val state by features.tls.collectAsState(); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true); Button({ features.inspectTls(target, port.toIntOrNull() ?: 443) }, enabled = !state.running) { Text("Compare verified TLS across addresses") }; OperationStatus(state.running, state.error) { features.cancel("tls") }; state.value?.forEach { (address, result) -> when (result) { is DiagnosticResult.Success -> { Text("$address • ${result.data.protocol} • ${result.data.cipherSuite}", fontWeight = FontWeight.Bold); result.data.certificates.forEachIndexed { index, certificate -> Text("Certificate ${index + 1}: ${certificate.subject}\nIssuer: ${certificate.issuer}\nExpires: ${java.time.Instant.ofEpochMilli(certificate.notAfterEpochMs)}\nKey: ${certificate.publicKeyAlgorithm} ${certificate.publicKeyBits ?: "?"} bits\nSHA-256: ${certificate.sha256}") } }; is DiagnosticResult.Failure -> Text("$address • ${result.code}: ${result.message}"); else -> Text("$address • unavailable") } } } }

@Composable private fun PortTool(target: String, features: FeatureViewModel) { var ports by remember { mutableStateOf("80,443,8080,8443") }; val state by features.services.collectAsState(); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(ports, { ports = it }, Modifier.fillMaxWidth(), label = { Text("Ports, comma lists, or ranges (maximum 256)") }); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Web" to "80,443,8080,8443", "Remote" to "22,23,3389,5900", "Mail" to "25,110,143,465,587,993,995").forEach { (name, value) -> AssistChip({ ports = value }, { Text(name) }) } }; Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Database" to "1433,3306,5432,6379,27017", "Network" to "53,67,68,161,162,8291,8728,8729", "TR-069" to "7547,7557,3000,8000").forEach { (name, value) -> AssistChip({ ports = value }, { Text(name) }) } }; Button({ features.inspectServices(target, ports) }, enabled = !state.running) { Text("Start safe TCP inspection") }; OperationStatus(state.running, state.error) { features.cancel("services") }; state.value?.forEach { Text("${it.port}: ${it.state}${it.latencyMs?.let { ms -> " in $ms ms" }.orEmpty()} • ${it.serviceHint ?: "unknown"}${if (it.tls) " • TLS verified" else ""}${it.bannerPreview?.let { banner -> "\n${banner.take(200)}" }.orEmpty()}") } } }

@Composable private fun EvidenceTool(snapshot: InvestigationSnapshot?, features: FeatureViewModel) {
    val context = LocalContext.current; val incidents by features.incidents.collectAsState(); val message by features.message.collectAsState(); val bundle by features.incidentBundle.collectAsState()
    var title by remember { mutableStateOf("Network incident") }; var customer by remember { mutableStateOf("") }; var problem by remember { mutableStateOf("Describe the observed problem") }; var notes by remember { mutableStateOf("") }; var cause by remember { mutableStateOf("") }; var resolution by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var networkType by remember { mutableStateOf("") }; var deviceInfo by remember { mutableStateOf("") }; var severity by remember { mutableStateOf("MEDIUM") }; var format by remember { mutableStateOf("zip") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val report = snapshot
        if (uri != null && report != null) context.contentResolver.openOutputStream(uri)?.use { out ->
            when (format) {
                "json" -> out.write(ReportExporter.json(report).toByteArray())
                "csv" -> out.write(ReportExporter.csv(report).toByteArray())
                "txt" -> out.write(ReportExporter.plainText(report).toByteArray())
                "pdf" -> ReportExporter.writePdf(report, out)
                else -> bundle?.let { prepared -> ReportExporter.writeIncidentZip(ReportExporter.IncidentReport(prepared.incident.id, prepared.incident.title, prepared.incident.customerOrSite, prepared.incident.target, prepared.incident.problemDescription, prepared.incident.status, prepared.incident.severity), prepared.investigations, prepared.attachments.map { ReportExporter.ReportAttachment(it.displayName, it.persistedUri) }, out) { value -> context.contentResolver.openInputStream(android.net.Uri.parse(value)) } } ?: ReportExporter.writeZip(report, out)
            }
        }
    }
    val attachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> val incident = incidents.firstOrNull(); if (uri != null && incident != null) features.addAttachment(incident.id, uri) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Incident title") }); OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("Customer or site") }); OutlinedTextField(problem, { problem = it }, Modifier.fillMaxWidth(), label = { Text("Problem description") }, minLines = 3)
        OutlinedTextField(networkType, { networkType = it }, Modifier.fillMaxWidth(), label = { Text("Network type") }); OutlinedTextField(deviceInfo, { deviceInfo = it }, Modifier.fillMaxWidth(), label = { Text("Device information") }); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Technician notes") }); OutlinedTextField(cause, { cause = it }, Modifier.fillMaxWidth(), label = { Text("Probable cause") }); OutlinedTextField(resolution, { resolution = it }, Modifier.fillMaxWidth(), label = { Text("Final resolution") }); OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("Comma-separated tags") })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { FilterChip(severity == it, { severity = it }, { Text(it) }) } }
        Button({ features.createIncident(title, customer, snapshot?.target.orEmpty(), problem, severity, notes, cause, resolution, tags, networkType, deviceInfo, snapshot?.id) }, enabled = snapshot != null) { Text("Create incident and link investigation") }
        OutlinedButton({ attachment.launch(arrayOf("image/*", "text/*", "application/pdf")) }, enabled = incidents.isNotEmpty()) { Text("Attach file to latest incident") }
        message?.let { Text(it) }; Text("Local incidents: ${incidents.size}"); incidents.firstOrNull()?.let { incident -> Text("Latest: ${incident.title} • ${incident.status}"); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("OPEN", "INVESTIGATING", "MONITORING", "RESOLVED", "CLOSED").forEach { status -> AssistChip({ features.setIncidentStatus(incident, status) }, { Text(status) }) } }; TextButton({ features.loadIncidentBundle(incident.id) }) { Text("Prepare latest incident and attachments for ZIP") } }
        Text("Redaction preview", style = MaterialTheme.typography.titleMedium); SelectionContainer { Text(snapshot?.let(ReportExporter::plainText)?.take(1800) ?: "Run or open an investigation first.") }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("json", "csv", "txt", "pdf", "zip").forEach { AssistChip({ format = it; if (snapshot != null) export.launch("network-investigator-${snapshot.id}.$it") }, { Text(it.uppercase()) }) } }
        Text("The system document picker controls every destination. Common credentials are redacted before serialization.")
    }
}

@Composable private fun OperationStatus(running: Boolean, error: String?, onCancel: (() -> Unit)? = null) { if (running) { LinearProgressIndicator(Modifier.fillMaxWidth()); onCancel?.let { TextButton(it) { Text("Cancel") } } }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }

private fun profileFor(spec: FeatureSpec) = when (spec.route) { "website-investigator" -> InvestigationProfile.WEBSITE_DOWN; "dns-detective" -> InvestigationProfile.DNS_PROBLEM; "lan-explorer" -> InvestigationProfile.LOCAL_DEVICE; "tls-investigator" -> InvestigationProfile.TLS_PROBLEM; else -> InvestigationProfile.QUICK_CHECK }

@Composable private fun NetworkSnapshotCard() { val context = LocalContext.current; var value by remember { mutableStateOf(NetworkSnapshotReader.read(context)) }; val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { value = NetworkSnapshotReader.read(context) }; ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Current network", style = MaterialTheme.typography.titleMedium); if (value == null) Text("No active network is available.") else { Text("${value!!.transport} • validated=${value!!.validated} • captive portal=${value!!.captivePortal} • metered=${value!!.metered} • VPN=${value!!.vpn}"); Text("Addresses: ${value!!.addresses.joinToString().ifBlank { "Unavailable" }}"); Text("Gateway: ${value!!.gateway ?: "Unavailable"}"); Text("DNS: ${value!!.dnsServers.joinToString().ifBlank { "Unavailable" }}"); value!!.wifi?.let { Text("SSID: ${it.ssid ?: "Unavailable"} • BSSID: ${it.bssid ?: "Unavailable"}\nRSSI: ${it.rssi ?: "Unavailable"} • ${it.band ?: "Unknown band"} ch ${it.channel ?: "?"} • ${it.frequencyMhz ?: "?"} MHz\nLink: ${it.linkSpeedMbps ?: "?"} Mbps • RX ${it.receiveLinkSpeedMbps ?: "?"} • TX ${it.transmitLinkSpeedMbps ?: "?"} • ${it.securityType ?: "Security unavailable"}") } }; Row { TextButton({ value = NetworkSnapshotReader.read(context) }) { Text("Refresh") }; TextButton({ permission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Allow Wi-Fi details") } } } } }

@Composable private fun CompareCard(runs: List<InvestigationSnapshot>) { var leftIndex by remember(runs.size) { mutableStateOf(0) }; var rightIndex by remember(runs.size) { mutableStateOf(1) }; var threshold by remember { mutableStateOf("20") }; ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Saved investigation comparison", style = MaterialTheme.typography.titleMedium); if (runs.size < 2) Text("Save at least two investigations to compare them.") else { Text("First run"); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { runs.take(5).forEachIndexed { index, run -> FilterChip(leftIndex == index, { leftIndex = index }, { Text("${index + 1}: ${run.target.take(12)}") }) } }; Text("Second run"); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { runs.take(5).forEachIndexed { index, run -> FilterChip(rightIndex == index, { rightIndex = index }, { Text("${index + 1}: ${run.target.take(12)}") }) } }; OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit) }, label = { Text("Significant duration change threshold (ms)") }, singleLine = true); val a = runs[leftIndex.coerceAtMost(runs.lastIndex)]; val b = runs[rightIndex.coerceAtMost(runs.lastIndex)]; Text("${a.target} versus ${b.target}", fontWeight = FontWeight.Bold); val left = a.cards.associateBy { it.taskType }; val right = b.cards.associateBy { it.taskType }; (left.keys + right.keys).forEach { task -> val x = left[task]; val y = right[task]; val limit = threshold.toLongOrNull() ?: 20; val label = when { x == null -> "Added"; y == null -> "Removed"; x.status != y.status || x.primaryResult != y.primaryResult || x.technicalDetails != y.technicalDetails -> "Changed"; kotlin.math.abs(x.durationMs - y.durationMs) < limit -> "Unchanged"; x.durationMs < y.durationMs -> "Improved by ${y.durationMs - x.durationMs} ms"; else -> "Degraded by ${x.durationMs - y.durationMs} ms" }; Text("$task: $label") } } } } }

@Composable
private fun RecorderScreen(features: FeatureViewModel) {
    val context = LocalContext.current; var recording by remember { mutableStateOf(false) }; var periodic by remember { mutableStateOf(false) }; var notificationDenied by remember { mutableStateOf(false) }; var probeTarget by remember { mutableStateOf("example.com") }; var interval by remember { mutableStateOf("10") }; val sessions by features.recorderSessions.collectAsState(); val samples by features.windowSamples.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationDenied = !granted }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.appendLine("timestamp,transport,validated,latency_ms,packet_loss_percent"); samples.forEach { writer.appendLine("${it.timestamp},${it.transport.orEmpty()},${it.validated ?: ""},${it.latencyMs ?: ""},${it.packetLossPercent ?: ""}") } } } }
    Page {
        Text("Connectivity Recorder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Live mode samples the active transport and validation state every 10 seconds. It uses a visible foreground notification and can increase battery use.")
        OutlinedTextField(probeTarget, { probeTarget = it }, Modifier.fillMaxWidth(), label = { Text("Explicit TCP probe target") }); OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, label = { Text("Sample interval, 5–60 seconds") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS); ContextCompat.startForegroundService(context, Intent(context, ConnectivityRecorderService::class.java).setAction(ConnectivityRecorderService.ACTION_START).putExtra(ConnectivityRecorderService.EXTRA_TARGET, probeTarget).putExtra(ConnectivityRecorderService.EXTRA_INTERVAL_MS, (interval.toLongOrNull() ?: 10) * 1000)); recording = true }, enabled = !recording) { Text("Start live") }
            OutlinedButton({ context.startService(Intent(context, ConnectivityRecorderService::class.java).setAction(ConnectivityRecorderService.ACTION_STOP)); recording = false }, enabled = recording) { Text("Stop") }
        }
        Text(if (recording) "Recording requested; the notification is the source of truth." else "Not recording")
        if (notificationDenied) Text("Notification permission was denied. Android still exposes active foreground-service controls, but notification visibility may be reduced.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ val request = PeriodicWorkRequestBuilder<ConnectivityWorker>(15, TimeUnit.MINUTES).build(); WorkManager.getInstance(context).enqueueUniquePeriodicWork("connectivity-monitor", ExistingPeriodicWorkPolicy.UPDATE, request); periodic = true }) { Text("Enable standard monitoring") }; OutlinedButton({ WorkManager.getInstance(context).cancelUniqueWork("connectivity-monitor"); periodic = false }) { Text("Disable") } }
        if (periodic) Text("Scheduled at Android's minimum periodic interval. Execution may be deferred by battery and background restrictions.")
        sessions.firstOrNull()?.let { session -> Text("Latest session: ${session.mode} • ${session.startedAt}"); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(5, 15, 30, null).forEach { minutes -> AssistChip({ features.loadRecorderWindow(session.id, minutes) }, { Text(minutes?.let { "$it min" } ?: "Full") }) } }; Text("Loaded samples: ${samples.size}"); OutlinedButton({ export.launch("connectivity-${session.id}.csv") }, enabled = samples.isNotEmpty()) { Text("Save loaded window") } }
    }
}

@Composable
private fun HistoryScreen(vm: MainViewModel, open: (InvestigationSnapshot) -> Unit) { val history by vm.history.collectAsState(); LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Saved investigations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }; if (history.isEmpty()) item { Text("No saved investigations yet.") }; items(history, key = { it.id }) { run -> ElevatedCard(onClick = { open(run) }, Modifier.fillMaxWidth()) { ListItem(headlineContent = { Text(run.target) }, supportingContent = { Text("${run.profile.name.replace('_', ' ')} • ${run.cards.size} observations") }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) } } } }

@Composable
private fun SettingsScreen(vm: MainViewModel, features: FeatureViewModel) {
    val settings by vm.settings.collectAsState(); val history by vm.history.collectAsState(); val message by vm.message.collectAsState(); val incidents by features.incidents.collectAsState(); val wifi by features.wifiMeasurements.collectAsState(); val lan by features.lanDevices.collectAsState(); val sessions by features.recorderSessions.collectAsState(); val samples by features.recorderSamples.collectAsState(); val requests by features.savedRequests.collectAsState(); val scope = rememberCoroutineScope(); val context = LocalContext.current; var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { destination -> context.contentResolver.openOutputStream(destination)?.use { output -> ReportExporter.writeAllDataZip(history, mapOf("incidents.csv" to ("id,title,status,severity,target,problem,notes,cause,resolution\n" + incidents.joinToString("\n") { "${it.id},${it.title},${it.status},${it.severity},${it.target.orEmpty()},${it.problemDescription},${it.technicianNotes.orEmpty()},${it.probableCause.orEmpty()},${it.finalResolution.orEmpty()}" }), "wifi-measurements.csv" to ("timestamp,label,ssid,rssi,frequency,link_speed\n" + wifi.joinToString("\n") { "${it.measuredAt},${it.label.orEmpty()},${it.ssid.orEmpty()},${it.rssi ?: ""},${it.frequencyMhz ?: ""},${it.linkSpeedMbps ?: ""}" }), "lan-devices.csv" to ("ip,hostname,category,first_seen,last_seen\n" + lan.joinToString("\n") { "${it.ipAddress},${it.hostname.orEmpty()},${it.category},${it.firstSeen},${it.lastSeen}" }), "connectivity-sessions.csv" to ("id,mode,started,completed\n" + sessions.joinToString("\n") { "${it.id},${it.mode},${it.startedAt},${it.completedAt ?: ""}" }), "connectivity-samples.csv" to ("session,timestamp,transport,validated,latency,loss\n" + samples.joinToString("\n") { "${it.sessionId},${it.timestamp},${it.transport.orEmpty()},${it.validated ?: ""},${it.latencyMs ?: ""},${it.packetLossPercent ?: ""}" }), "saved-requests.csv" to ("id,name,method,url\n" + requests.joinToString("\n") { "${it.id},${it.name},${it.method},${it.url}" })), output) } } }
    Page {
        Text("Settings & privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Local-first defaults: no account, cloud sync, analytics, ads, telemetry, automatic upload, or automatic third-party IP lookup.")
        ListItem(headlineContent = { Text("Offline-only mode") }, supportingContent = { Text("Direct diagnostics contact only targets you enter; optional enrichment is disabled.") }, trailingContent = { Switch(settings.offlineOnly, { scope.launch { vm.preferences.setOfflineOnly(it) } }) })
        ListItem(headlineContent = { Text("Optional online enrichment") }, supportingContent = { Text("Providers are identified before use and failures never block core diagnostics.") }, trailingContent = { Switch(settings.onlineEnrichment, { scope.launch { vm.preferences.setOnlineEnrichment(it) } }, enabled = !settings.offlineOnly) })
        ListItem(headlineContent = { Text("Dynamic color") }, trailingContent = { Switch(settings.dynamicColor, { scope.launch { vm.preferences.setDynamicColor(it) } }) })
        ListItem(headlineContent = { Text("Biometric/device lock") }, supportingContent = { Text("Requires device biometric or credential before opening saved evidence.") }, trailingContent = { Switch(settings.biometricLock, { enabled -> if (!enabled) scope.launch { vm.preferences.setBiometricLock(false) } else { val activity = context as? FragmentActivity; if (activity != null) BiometricLock(activity).authenticate({ scope.launch { vm.preferences.setBiometricLock(true) } }, {}) } }) })
        Text("Theme"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemePreference.entries.forEach { theme -> FilterChip(settings.theme == theme, { scope.launch { vm.preferences.setTheme(theme) } }, { Text(theme.name) }) } }
        Text("Retention"); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(7, 30, 90, 365, null).forEach { days -> FilterChip(settings.retentionDays == days, { scope.launch { vm.preferences.setRetentionDays(days) } }, { Text(days?.let { if (it == 365) "1 year" else "$it days" } ?: "Forever") }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(vm::enforceRetention) { Text("Apply retention") }; OutlinedButton({ export.launch("network-investigator-all-data.zip") }) { Text("Export all") } }
        OutlinedButton({ confirmClear = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear all local data") }
        message?.let { Text(it) }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear all local data?") }, text = { Text("Investigations, incidents, measurements, device history, requests, and recorder sessions will be permanently deleted.") }, confirmButton = { TextButton({ confirmClear = false; vm.clearAllData() }) { Text("Clear permanently") } }, dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}
