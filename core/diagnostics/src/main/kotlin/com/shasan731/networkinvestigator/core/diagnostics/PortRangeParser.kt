package com.shasan731.networkinvestigator.core.diagnostics

object PortRangeParser {
    const val MAX_PORTS = 256

    fun parse(input: String): Result<List<Int>> = runCatching {
        val values = linkedSetOf<Int>()
        input.split(',').map(String::trim).filter(String::isNotEmpty).forEach { token ->
            if ('-' in token) {
                val parts = token.split('-')
                require(parts.size == 2) { "Invalid port range: $token" }
                val start = validPort(parts[0]); val end = validPort(parts[1])
                require(start <= end) { "Port range is reversed: $token" }
                require(end - start + 1 <= MAX_PORTS) { "A single range may contain at most $MAX_PORTS ports" }
                (start..end).forEach(values::add)
            } else values += validPort(token)
            require(values.size <= MAX_PORTS) { "At most $MAX_PORTS unique ports are allowed" }
        }
        require(values.isNotEmpty()) { "Enter at least one port" }
        values.sorted()
    }

    private fun validPort(value: String) = value.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: throw IllegalArgumentException("Port must be between 1 and 65535: $value")
}

