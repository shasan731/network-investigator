package com.shasan731.networkinvestigator.core.network

import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class PublicIpObservation(val address: String, val provider: String)
interface PublicIpProvider { val name: String; suspend fun lookup(): DiagnosticResult<PublicIpObservation> }
class ConfigurablePublicIpProvider(private val endpoint: String = "https://api.ipify.org?format=json") : PublicIpProvider {
    override val name = "ipify"
    override suspend fun lookup(): DiagnosticResult<PublicIpObservation> = withContext(Dispatchers.IO) { val started = System.currentTimeMillis(); try { HttpInspector.defaultClient().newCall(Request.Builder().url(endpoint).build()).execute().use { response -> require(response.isSuccessful); val address = Json.parseToJsonElement(requireNotNull(response.body).string()).jsonObject["ip"]?.jsonPrimitive?.content ?: error("Provider omitted address"); DiagnosticResult.Success(PublicIpObservation(address, name), started, System.currentTimeMillis(), ResultSource.THIRD_PARTY_PROVIDER) } } catch (error: Exception) { DiagnosticResult.Failure(DiagnosticErrorCode.NETWORK_UNAVAILABLE, "Optional public-IP lookup failed.", error.message, true, ResultSource.THIRD_PARTY_PROVIDER) } }
}
