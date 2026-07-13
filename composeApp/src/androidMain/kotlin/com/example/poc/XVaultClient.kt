package com.example.poc

import android.util.Log
import com.example.poc.vault.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Android client that talks to xVault.
 *
 * Strategy (dual-mode):
 *   1. Try DIRECT HTTPS to xVault (works when device/emulator has network access to prod.xvault.comcast.com)
 *   2. Fallback to Ktor proxy server on 10.0.2.2:8080 (for environments where direct isn't possible)
 *
 * The emulator CAN reach xVault directly via HTTPS (proven by Chrome loading the login page).
 * The previous "Failed to connect to /10.0.2.2:8080" was simply because the Ktor server wasn't running.
 */
object XVaultClient {

    private const val TAG = "XVaultClient"

    /** Base URL for the Ktor proxy server (fallback). On emulator, 10.0.2.2 maps to host's localhost. */
    private const val PROXY_BASE = "http://10.0.2.2:${SERVER_PORT}"

    /** Cached xVault client token from AppRole login. */
    @Volatile
    private var vaultToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0L

    /** Whether to use direct mode (true) or proxy mode (false). Auto-detected on first call. */
    @Volatile
    private var useDirectMode: Boolean? = null

    // ── Health check ───────────────────────────────────────────────────────

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        // Try direct xVault first
        if (tryDirectLogin() != null) {
            useDirectMode = true
            Log.i(TAG, "Health check: DIRECT mode (xVault reachable)")
            return@withContext true
        }
        // Fallback: check proxy server
        try {
            val conn = URL("$PROXY_BASE/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val code = conn.responseCode
            if (code == 200) {
                useDirectMode = false
                Log.i(TAG, "Health check: PROXY mode (server reachable)")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check: proxy also unreachable: ${e.message}")
        }
        false
    }

    // ── Credential CRUD ──────────────────────────────────────────────────

    /**
     * Save a credential to xVault (direct or via proxy).
     */
    suspend fun saveCredential(userId: String, entry: PasswordEntry): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val mode = resolveMode()
                if (mode == Mode.DIRECT) {
                    saveCredentialDirect(userId, entry)
                } else {
                    saveCredentialViaProxy(userId, entry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save exception for ${entry.id}", e)
                VaultTrace.e(TAG, "save exception id=${entry.id}", e)
                false
            }
        }

    /**
     * Fetch ALL credentials for a user.
     * Returns a list of [CredentialWithFavicon] containing both credential data and favicon bytes.
     */
    suspend fun fetchAllCredentials(userId: String): List<CredentialWithFavicon> =
        withContext(Dispatchers.IO) {
            try {
                val mode = resolveMode()
                if (mode == Mode.DIRECT) {
                    fetchAllDirect(userId)
                } else {
                    fetchAllViaProxy(userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch all exception for $userId", e)
                VaultTrace.e(TAG, "fetchAll exception userId=$userId", e)
                emptyList()
            }
        }

    /**
     * Delete a credential from xVault.
     */
    suspend fun deleteCredential(userId: String, entryId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val mode = resolveMode()
                if (mode == Mode.DIRECT) {
                    deleteCredentialDirect(userId, entryId)
                } else {
                    deleteCredentialViaProxy(userId, entryId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete exception for $entryId", e)
                false
            }
        }

    // ══════════════════════════════════════════════════════════════════════
    // ── DIRECT MODE (app → xVault HTTPS) ─────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════

    private fun ensureDirectToken(): String? {
        val now = System.currentTimeMillis()
        vaultToken?.let { t ->
            if (now < tokenExpiry - 3600_000L) return t
        }
        return tryDirectLogin()
    }

    private fun tryDirectLogin(): String? {
        return try {
            val url = URL("${XVaultConfig.BASE_URL}${XVaultConfig.AUTH_APPROLE_LOGIN}")
            val body = """{"role_id":"${XVaultConfig.ROLE_ID}","secret_id":"${XVaultConfig.SECRET_ID}"}"""

            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Vault-Namespace", XVaultConfig.NAMESPACE)
                outputStream.write(body.toByteArray())
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "Direct xVault login failed: HTTP $responseCode")
                return null
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            val tokenMatch = Regex(""""client_token"\s*:\s*"([^"]+)"""").find(responseBody)
            val leaseMatch = Regex(""""lease_duration"\s*:\s*(\d+)""").find(responseBody)

            val token = tokenMatch?.groupValues?.get(1) ?: return null
            val lease = leaseMatch?.groupValues?.get(1)?.toLongOrNull() ?: 2764800L

            vaultToken = token
            tokenExpiry = System.currentTimeMillis() + (lease * 1000L)
            Log.i(TAG, "✅ Direct xVault AppRole login OK (lease=${lease}s)")
            token
        } catch (e: Exception) {
            Log.d(TAG, "Direct xVault unreachable: ${e.message}")
            null
        }
    }

    private fun saveCredentialDirect(userId: String, entry: PasswordEntry): Boolean {
        val token = ensureDirectToken() ?: return false
        val safePath = XVaultConfig.sanitizePathSegment(entry.id)
        val path = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$safePath"
        val innerData = JSONObject().apply {
            put("id", entry.id)
            put("siteName", entry.siteName)
            put("username", entry.username)
            put("password", entry.password)
            put("loginUrl", entry.loginUrl)
            put("dateModified", entry.dateModified.toString())
        }
        val vaultBody = JSONObject().apply { put("data", innerData) }.toString()

        val (code, _) = vaultRequest("POST", path, token, vaultBody)
        if (code in 200..299) {
            Log.i(TAG, "✅ Credential saved DIRECT: $userId/${entry.id}")
            VaultTrace.i(TAG, "save OK (direct) userId=$userId id=${entry.id}")
            return true
        }
        Log.e(TAG, "Direct save failed: HTTP $code")
        return false
    }

    private fun fetchAllDirect(userId: String): List<CredentialWithFavicon> {
        val token = ensureDirectToken() ?: return emptyList()

        // List keys
        val listPath = "${XVaultConfig.KV2_METADATA_PREFIX}/$userId?list=true"
        val (listCode, listBody) = vaultRequest("GET", listPath, token)
        if (listCode == 404) return emptyList()
        if (listCode !in 200..299) return emptyList()

        val keys = parseKeysFromVaultList(listBody)
        Log.i(TAG, "Direct fetch: found ${keys.size} keys for $userId")

        return keys.mapNotNull { key ->
            val readPath = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$key"
            val (readCode, readBody) = vaultRequest("GET", readPath, token)
            if (readCode !in 200..299) return@mapNotNull null

            val credData = parseVaultCredential(readBody) ?: return@mapNotNull null

            // Fetch favicon
            val domain = extractDomainFromUrl(credData["loginUrl"] ?: "")
            val faviconBytes = if (domain.isNotBlank()) fetchFaviconBytes(domain) else null

            CredentialWithFavicon(
                entry = PasswordEntry(
                    id = credData["id"] ?: key,
                    siteName = credData["siteName"] ?: "",
                    username = credData["username"] ?: "",
                    password = credData["password"] ?: "",
                    loginUrl = credData["loginUrl"] ?: "",
                    dateModified = credData["dateModified"]?.toLongOrNull() ?: 0L,
                ),
                faviconBytes = faviconBytes,
            )
        }.also {
            Log.i(TAG, "✅ Fetched ${it.size} credentials DIRECT for $userId")
            VaultTrace.i(TAG, "fetchAll OK (direct) userId=$userId total=${it.size}")
        }
    }

    private fun deleteCredentialDirect(userId: String, entryId: String): Boolean {
        val token = ensureDirectToken() ?: return false
        val safePath = XVaultConfig.sanitizePathSegment(entryId)
        val path = "${XVaultConfig.KV2_DATA_PREFIX}/$userId/$safePath"
        val (code, _) = vaultRequest("DELETE", path, token)
        if (code in 200..299 || code == 204) {
            Log.i(TAG, "✅ Credential deleted DIRECT: $userId/$safePath")
            return true
        }
        return false
    }

    private fun vaultRequest(method: String, path: String, token: String, body: String? = null): Pair<Int, String> {
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
                if (responseCode in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (e: IOException) { "" }
            responseCode to responseBody
        } catch (e: Exception) {
            Log.e(TAG, "vaultRequest $method $path: ${e.message}")
            500 to """{"error":"${e.message}"}"""
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ── PROXY MODE (app → Ktor server → xVault) ──────────────────────────
    // ══════════════════════════════════════════════════════════════════════

    private fun saveCredentialViaProxy(userId: String, entry: PasswordEntry): Boolean {
        val url = URL("$PROXY_BASE/api/credentials/$userId")
        val body = JSONObject().apply {
            put("id", entry.id)
            put("siteName", entry.siteName)
            put("username", entry.username)
            put("password", entry.password)
            put("loginUrl", entry.loginUrl)
            put("dateModified", entry.dateModified.toString())
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            outputStream.write(body.toString().toByteArray())
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            Log.i(TAG, "✅ Credential saved via PROXY: $userId/${entry.id}")
            VaultTrace.i(TAG, "save OK (proxy) userId=$userId id=${entry.id}")
            return true
        }
        val err = readResponse(conn)
        Log.e(TAG, "Proxy save failed: HTTP $responseCode — $err")
        return false
    }

    private fun fetchAllViaProxy(userId: String): List<CredentialWithFavicon> {
        val url = URL("$PROXY_BASE/api/credentials/$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) return emptyList()

        val responseBody = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(responseBody)
        val credArray = json.getJSONArray("credentials")

        return (0 until credArray.length()).map { i ->
            val cred = credArray.getJSONObject(i)
            val faviconB64 = cred.optString("faviconBase64", "")
            val faviconBytes = if (faviconB64.isNotBlank()) {
                try { android.util.Base64.decode(faviconB64, android.util.Base64.DEFAULT) }
                catch (e: Exception) { null }
            } else null

            CredentialWithFavicon(
                entry = PasswordEntry(
                    id = cred.getString("id"),
                    siteName = cred.optString("siteName", ""),
                    username = cred.optString("username", ""),
                    password = cred.optString("password", ""),
                    loginUrl = cred.optString("loginUrl", ""),
                    dateModified = cred.optString("dateModified", "0").toLongOrNull() ?: 0L,
                ),
                faviconBytes = faviconBytes,
            )
        }.also {
            Log.i(TAG, "✅ Fetched ${it.size} credentials via PROXY for $userId")
        }
    }

    private fun deleteCredentialViaProxy(userId: String, entryId: String): Boolean {
        val safeId = XVaultConfig.sanitizePathSegment(entryId)
        val url = URL("$PROXY_BASE/api/credentials/$userId/$safeId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return conn.responseCode in 200..299
    }

    // ══════════════════════════════════════════════════════════════════════
    // ── Helpers ──────────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════

    private enum class Mode { DIRECT, PROXY }

    private fun resolveMode(): Mode {
        useDirectMode?.let { return if (it) Mode.DIRECT else Mode.PROXY }
        // Auto-detect: try direct login
        if (tryDirectLogin() != null) {
            useDirectMode = true
            return Mode.DIRECT
        }
        useDirectMode = false
        return Mode.PROXY
    }

    private fun readResponse(conn: HttpURLConnection): String {
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText()
            else conn.errorStream?.bufferedReader()?.readText() ?: ""
        } catch (e: IOException) { "" }
    }

    private fun parseKeysFromVaultList(json: String): List<String> {
        val keysMatch = Regex(""""keys"\s*:\s*\[([^\]]*)]""").find(json) ?: return emptyList()
        return Regex(""""([^"]+)"""").findAll(keysMatch.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private fun parseVaultCredential(json: String): Map<String, String>? {
        return try {
            // xVault KV2 response: { "data": { "data": { ...fields... }, "metadata": {...} } }
            val dataPattern = Regex(""""data"\s*:\s*\{[^}]*"data"\s*:\s*\{([^}]+)\}""")
            val match = dataPattern.find(json) ?: return null
            val fields = mutableMapOf<String, String>()
            Regex(""""(\w+)"\s*:\s*"([^"]*?)"""").findAll(match.groupValues[1]).forEach {
                fields[it.groupValues[1]] = it.groupValues[2]
            }
            fields
        } catch (e: Exception) { null }
    }

    private fun extractDomainFromUrl(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')

    /**
     * Fetch favicon from public APIs (DuckDuckGo then Google).
     * This runs directly from the app — no need for proxy for public favicon APIs.
     */
    private fun fetchFaviconBytes(domain: String): ByteArray? {
        // Try DuckDuckGo
        try {
            val conn = URL("https://icons.duckduckgo.com/ip3/$domain.ico").openConnection() as HttpsURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) {
                val bytes = conn.inputStream.readBytes()
                if (bytes.size > 100) return bytes
            }
        } catch (_: Exception) {}

        // Fallback: Google
        try {
            val conn = URL("https://www.google.com/s2/favicons?domain=$domain&sz=128").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) return conn.inputStream.readBytes()
        } catch (_: Exception) {}

        return null
    }
}

/**
 * A credential with optional favicon image bytes.
 */
data class CredentialWithFavicon(
    val entry: PasswordEntry,
    val faviconBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialWithFavicon) return false
        return entry == other.entry
    }
    override fun hashCode(): Int = entry.hashCode()
}

