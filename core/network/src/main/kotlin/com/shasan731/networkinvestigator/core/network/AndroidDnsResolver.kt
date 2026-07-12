package com.shasan731.networkinvestigator.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import com.shasan731.networkinvestigator.core.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidPlatformDnsResolver(private val context: Context) : DnsResolver {
    override val name = "Android DnsResolver on active network"
    override suspend fun query(query: DnsQuery): DiagnosticResult<ResolverAnswer> {
        if (Build.VERSION.SDK_INT < 29) return DiagnosticResult.Unsupported("Android DnsResolver requires Android 10 or newer.")
        val type = when (query.type) { DnsRecordType.A -> 1; DnsRecordType.AAAA -> 28; else -> return DiagnosticResult.Unsupported("Android platform raw resolver is used here for A and AAAA; select a custom resolver for ${query.type}.") }
        val started = System.currentTimeMillis(); val begin = System.nanoTime(); val cancellation = CancellationSignal()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            val network = context.getSystemService(ConnectivityManager::class.java).activeNetwork
            DnsResolver.getInstance().rawQuery(network, query.name, 1, type, DnsResolver.FLAG_EMPTY, { command -> command.run() }, cancellation, object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) { if (!continuation.isActive) return; continuation.resume(if (rcode == 0) runCatching { DiagnosticResult.Success(ResolverAnswer(name, DnsResponseParser.parse(answer), (System.nanoTime() - begin) / 1_000_000, null), started, System.currentTimeMillis(), ResultSource.ANDROID_SYSTEM) }.getOrElse { DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "Android DNS response could not be parsed.", it.message, true, ResultSource.ANDROID_SYSTEM) } else DiagnosticResult.Failure(if (rcode == 3) DiagnosticErrorCode.DNS_NXDOMAIN else DiagnosticErrorCode.DNS_SERVFAIL, "Android resolver returned rcode $rcode.", "rcode=$rcode", true, ResultSource.ANDROID_SYSTEM)) }
                override fun onError(error: DnsResolver.DnsException) { if (continuation.isActive) continuation.resume(DiagnosticResult.Failure(DiagnosticErrorCode.DNS_SERVFAIL, "Android DnsResolver failed.", "code=${error.code}", true, ResultSource.ANDROID_SYSTEM)) }
            })
        }
    }
}
