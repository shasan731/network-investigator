package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

class BootstrapRdapProvider(private val endpoint: String = "https://rdap.org/ip/", private val client: OkHttpClient = HttpInspector.defaultClient()) : RdapProvider {
    override val name = "RDAP bootstrap service"
    override suspend fun lookup(address: String): DiagnosticResult<RdapResult> = withContext(Dispatchers.IO) {
        val parsed = TargetParser.parse(address)
        if (parsed !is TargetParseResult.Valid || parsed.parsed.target !is InvestigationTarget.Ipv4 && parsed.parsed.target !is InvestigationTarget.Ipv6) return@withContext DiagnosticResult.Failure(DiagnosticErrorCode.INVALID_TARGET, "RDAP requires an IP address.", null, false)
        val started = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url(endpoint + address).header("Accept", "application/rdap+json").build()).execute().use { response ->
                require(response.isSuccessful); val root = Json.parseToJsonElement(requireNotNull(response.body).string()).jsonObject
                val cidrs = (root["cidr0_cidrs"] as? JsonArray).orEmpty().mapNotNull { item -> item.jsonObject.let { obj -> val prefix = obj["length"]?.jsonPrimitive?.intOrNull; val value = obj["v4prefix"]?.jsonPrimitive?.contentOrNull ?: obj["v6prefix"]?.jsonPrimitive?.contentOrNull; if (prefix != null && value != null) "$value/$prefix" else null } }
                val now = System.currentTimeMillis(); DiagnosticResult.Success(RdapResult(name, root["handle"]?.jsonPrimitive?.contentOrNull, root["name"]?.jsonPrimitive?.contentOrNull, cidrs, now, now + 24 * 60 * 60 * 1000L), started, now, ResultSource.THIRD_PARTY_PROVIDER)
            }
        } catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.NETWORK_UNAVAILABLE, "Optional RDAP enrichment failed.", error.message, true, ResultSource.THIRD_PARTY_PROVIDER) }
    }
}
