package com.example.poc

import com.example.poc.vault.XVaultConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Server-side xVault proxy.
 *
 * The Ktor server runs on the developer's host machine which has access to
 * the Comcast private network (10.84.89.x). Android emulators cannot reach
 * xVault directly, so all xVault operations are proxied through this server.
 *
 * Architecture:
 *   Android App ──(HTTP)──► Ktor Server (0.0.0.0:8080) ──(HTTPS)──► xVault
 *
 * This also enables cross-platform sync: iOS, Windows, and Mac clients will
 * all talk to this same server, and the server mediates xVault access.
 */
object XVaultProxy {

    @Volatile
    private var clientToken: String? = null

    @Volatile
    private var tokenExpiry: Long = 0L

    // ── Authentication ─────────────────────────────────────────────────────

    /**
     * Authenticate with xVault using AppRole credentials.
     * Caches the token for the lease duration.
     */
    fun login(): String? {
        val now = System.currentTimeMillis()
        clientToken?.let { token ->
            if (now < tokenExpiry - 3600_000L) {
                println("[XVaultProxy] Using cached vault token")
                return token
            }
        }

        println("[XVaultProxy] Authenticating with xVault AppRole...")
        return try {
            val url = URL("${XVaultConfig.BASE_URL}${XVaultConfig.AUTH_APPROLE_LOGIN}")
            val body = """{"role_id":"${XVaultConfig.ROLE_ID}","secret_id":"${XVaultConfig.SECRET_ID}"}"""

            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Vault-Namespace", XVaultConfig.NAMESPACE)
                outputStream.write(body.toByteArray())
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                println("[XVaultProxy] AppRole login FAILED: HTTP $responseCode — $err")
                return null
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            // Simple JSON parsing (no external deps needed)
            val tokenMatch = Regex(""""client_token"\s*:\s*"([^"]+)"""").find(responseBody)
            val leaseMatch = Regex(""""lease_duration"\s*:\s*(\d+)""").find(responseBody)

            val token = tokenMatch?.groupValues?.get(1) ?: return null
            val lease = leaseMatch?.groupValues?.get(1)?.toLongOrNull() ?: 2764800L

            clientToken = token
            tokenExpiry = now + (lease * 1000L)

            println("[XVaultProxy] ✅ AppRole login OK (lease=${lease}s)")
            token
        } catch (e: Exception) {
            println("[XVaultProxy] AppRole login exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun ensureToken(): String? {
        val now = System.currentTimeMillis()
        return clientToken?.takeIf { now < tokenExpiry - 3600_000L } ?: login()
    }

    // ── KV2 CRUD ───────────────────────────────────────────────────────────

    /**
     * Save a credential to xVault.
     * Path: kv2/data/users/{userId}/{entryKey}
     */
    fun saveCredential(userId: String, entryKey: String, credentialJson: String): Pair<Int, String> {
        val token = ensureToken() ?: return 503 to """{"error":"xVault authentication failed"}"""
        val safePath = XVaultConfig.sanitizePathSegment(entryKey)
        val path = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$safePath"
        return vaultRequest("POST", path, token, credentialJson)
    }

    /**
     * Read a single credential from xVault.
     */
    fun readCredential(userId: String, entryKey: String): Pair<Int, String> {
        val token = ensureToken() ?: return 503 to """{"error":"xVault authentication failed"}"""
        val safePath = XVaultConfig.sanitizePathSegment(entryKey)
        val path = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$safePath"
        return vaultRequest("GET", path, token)
    }

    /**
     * List all credential keys for a user.
     */
    fun listCredentialKeys(userId: String): Pair<Int, String> {
        val token = ensureToken() ?: return 503 to """{"error":"xVault authentication failed"}"""
        val path = "${XVaultConfig.KV2_METADATA_PREFIX}/$userId?list=true"
        return vaultRequest("GET", path, token)
    }

    /**
     * Delete a credential from xVault.
     */
    fun deleteCredential(userId: String, entryKey: String): Pair<Int, String> {
        val token = ensureToken() ?: return 503 to """{"error":"xVault authentication failed"}"""
        val safePath = XVaultConfig.sanitizePathSegment(entryKey)
        val path = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$safePath"
        return vaultRequest("DELETE", path, token)
    }

    // ── Favicon fetcher ────────────────────────────────────────────────────

    /**
     * Fetch favicon bytes for a domain.
     * Tries DuckDuckGo Icons API first, then Google Favicon API.
     * Returns the icon bytes or null.
     */
    fun fetchFavicon(domain: String): ByteArray? {
        if (domain.isBlank()) return null

        // Try DuckDuckGo first
        val duckUrl = "https://icons.duckduckgo.com/ip3/$domain.ico"
        fetchBytes(duckUrl)?.let { bytes ->
            if (bytes.size > 100) { // valid icon, not an error page
                println("[XVaultProxy] Favicon fetched from DuckDuckGo for $domain (${bytes.size} bytes)")
                return bytes
            }
        }

        // Fallback to Google
        val googleUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        fetchBytes(googleUrl)?.let { bytes ->
            println("[XVaultProxy] Favicon fetched from Google for $domain (${bytes.size} bytes)")
            return bytes
        }

        println("[XVaultProxy] No favicon found for $domain")
        return null
    }

    private fun fetchBytes(urlStr: String): ByteArray? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) {
                conn.inputStream.readBytes()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun vaultRequest(
        method: String,
        path: String,
        token: String,
        body: String? = null,
    ): Pair<Int, String> {
        return try {
            val url = URL("${XVaultConfig.BASE_URL}$path")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = if (method == "PATCH") "POST" else method
                if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("X-Vault-Token", token)
                setRequestProperty("X-Vault-Namespace", XVaultConfig.NAMESPACE)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
                if (body != null) {
                    doOutput = true
                    outputStream.write(body.toByteArray())
                }
            }

            val responseCode = conn.responseCode
            val responseBody = try {
                if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
            } catch (e: IOException) { "" }

            println("[XVaultProxy] $method $path → HTTP $responseCode")
            responseCode to responseBody
        } catch (e: Exception) {
            println("[XVaultProxy] $method $path EXCEPTION: ${e.message}")
            500 to """{"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }
}

