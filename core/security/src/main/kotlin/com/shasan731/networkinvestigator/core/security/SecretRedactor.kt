package com.shasan731.networkinvestigator.core.security

object SecretRedactor {
    private val sensitiveNames = setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "password", "passwd", "secret", "api_key", "apikey", "access_token", "refresh_token", "session", "sessionid")
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")
    private val basic = Regex("(?i)\\bBasic\\s+[A-Za-z0-9+/=]+")
    private val assignments = Regex("(?i)(authorization|cookie|password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|session(?:id)?)((?:\\s*[=:]\\s*|%3[dD]))([^&\\s,;\"']+)")

    fun redact(text: String, extraFieldNames: Set<String> = emptySet()): String {
        var output = text.replace(bearer, "Bearer [REDACTED]").replace(basic, "Basic [REDACTED]")
        output = output.replace(assignments) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
        extraFieldNames.filter(String::isNotBlank).forEach { name ->
            val pattern = Regex("(?i)(${Regex.escape(name)})(\\s*[=:]\\s*)([^&\\s,;\"']+)")
            output = output.replace(pattern) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
        }
        return output
    }

    fun redactHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) -> if (name.lowercase() in sensitiveNames) "[REDACTED]" else redact(value) }
}

