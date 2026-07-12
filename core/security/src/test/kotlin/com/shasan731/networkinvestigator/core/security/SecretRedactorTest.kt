package com.shasan731.networkinvestigator.core.security
import org.junit.Assert.*
import org.junit.Test
class SecretRedactorTest {
    @Test fun `redacts bearer basic assignments and custom names`() { val value = SecretRedactor.redact("Authorization: Bearer abc.def password=hunter2 tenantSecret=xyz", setOf("tenantSecret")); assertFalse(value.contains("abc.def")); assertFalse(value.contains("hunter2")); assertFalse(value.contains("xyz")); assertTrue(value.contains("[REDACTED]")) }
    @Test fun `redacts sensitive header values only`() { val headers = SecretRedactor.redactHeaders(mapOf("Cookie" to "id=secret", "Server" to "nginx")); assertEquals("[REDACTED]", headers["Cookie"]); assertEquals("nginx", headers["Server"]) }
}

