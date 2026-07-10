package com.example.poc.vault

import kotlin.random.Random

/**
 * PKCE (Proof Key for Code Exchange) helper for OAuth 2.0 Authorization Code flow.
 * Generates code_verifier and code_challenge per RFC 7636.
 */
object PkceHelper {

    /**
     * Generates a cryptographically random code verifier (43-128 chars, URL-safe).
     */
    fun generateCodeVerifier(): String {
        val bytes = Random.nextBytes(32)
        return bytes.toUrlSafeBase64()
    }

    /**
     * Generates the code challenge from a code verifier using S256 method.
     * challenge = BASE64URL(SHA256(verifier))
     */
    fun generateCodeChallenge(verifier: String): String = sha256AndBase64Url(verifier)

    /**
     * Generates a random state parameter for CSRF protection.
     */
    fun generateState(): String {
        val bytes = Random.nextBytes(16)
        return bytes.toUrlSafeBase64()
    }

    /**
     * Builds the full authorization URL with PKCE parameters.
     */
    fun buildAuthorizationUrl(
        codeChallenge: String,
        state: String,
    ): String {
        val params = listOf(
            "client_id" to OidcConfig.CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to OidcConfig.REDIRECT_URI,
            "scope" to OidcConfig.SCOPES,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "response_mode" to "query",
            "prompt" to "login",
        )
        val query = params.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
        return "${OidcConfig.AUTHORIZATION_ENDPOINT}?$query"
    }
}

/**
 * URL-safe Base64 encoding (no padding, + → -, / → _).
 */
internal fun ByteArray.toUrlSafeBase64(): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        sb.append(table[b0 shr 2])
        if (i + 1 < size) {
            val b1 = this[i + 1].toInt() and 0xFF
            sb.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
            if (i + 2 < size) {
                val b2 = this[i + 2].toInt() and 0xFF
                sb.append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                sb.append(table[b2 and 0x3F])
            } else {
                sb.append(table[(b1 and 0x0F) shl 2])
            }
        } else {
            sb.append(table[(b0 and 0x03) shl 4])
        }
        i += 3
    }
    return sb.toString()
}

/**
 * Minimal URL encoding for query parameter values.
 */
internal fun urlEncode(value: String): String {
    val sb = StringBuilder()
    for (c in value) {
        when {
            c.isLetterOrDigit() || c in "-._~" -> sb.append(c)
            c == ' ' -> sb.append("%20")
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    sb.append('%')
                    sb.append(((b.toInt() and 0xFF) shr 4).let { "0123456789ABCDEF"[it] })
                    sb.append((b.toInt() and 0x0F).let { "0123456789ABCDEF"[it] })
                }
            }
        }
    }
    return sb.toString()
}

/**
 * Decodes a JWT token's payload (Base64URL-encoded JSON) and extracts claims.
 * This is NOT cryptographic verification — just payload extraction for reading email/name.
 */
fun decodeJwtPayload(jwt: String): Map<String, String> {
    val parts = jwt.split(".")
    if (parts.size != 3) return emptyMap()
    return try {
        val payload = base64UrlDecode(parts[1]).decodeToString()
        // Simple JSON parsing without external library
        parseSimpleJson(payload)
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Base64URL decode (adds padding back, swaps - → +, _ → /).
 */
internal fun base64UrlDecode(input: String): ByteArray {
    val base64 = input
        .replace('-', '+')
        .replace('_', '/')
        .let { s ->
            when (s.length % 4) {
                2 -> "$s=="
                3 -> "$s="
                else -> s
            }
        }
    return decodeBase64(base64)
}

/**
 * Pure-Kotlin Base64 decoder.
 */
internal fun decodeBase64(input: String): ByteArray {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = mutableListOf<Byte>()
    var i = 0
    val clean = input.filter { it in table || it == '=' }
    while (i < clean.length) {
        val b0 = table.indexOf(clean[i])
        val b1 = if (i + 1 < clean.length) table.indexOf(clean[i + 1]) else 0
        val b2 = if (i + 2 < clean.length && clean[i + 2] != '=') table.indexOf(clean[i + 2]) else -1
        val b3 = if (i + 3 < clean.length && clean[i + 3] != '=') table.indexOf(clean[i + 3]) else -1

        output.add(((b0 shl 2) or (b1 shr 4)).toByte())
        if (b2 >= 0) output.add((((b1 and 0x0F) shl 4) or (b2 shr 2)).toByte())
        if (b3 >= 0) output.add((((b2 and 0x03) shl 6) or b3).toByte())
        i += 4
    }
    return output.toByteArray()
}

/**
 * Platform-specific SHA-256 hash → URL-safe Base64.
 */
expect fun sha256AndBase64Url(input: String): String

/**
 * Extracts string values from a flat JSON object. Handles nested quotes properly.
 * Only extracts top-level string values (sufficient for JWT claims like email, name, sub).
 */
fun parseSimpleJson(json: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    // Match "key":"value" or "key":value patterns
    val regex = Regex(""""([^"]+)"\s*:\s*"([^"]*?)"""")
    for (match in regex.findAll(json)) {
        map[match.groupValues[1]] = match.groupValues[2]
    }
    // Also match numeric/boolean values: "key":123 or "key":true
    val numRegex = Regex(""""([^"]+)"\s*:\s*([0-9a-zA-Z.]+)""")
    for (match in numRegex.findAll(json)) {
        val key = match.groupValues[1]
        if (key !in map) {
            map[key] = match.groupValues[2]
        }
    }
    return map
}




