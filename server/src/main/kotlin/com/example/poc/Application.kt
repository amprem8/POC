package com.example.poc

import com.example.poc.vault.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Base64

fun main() {
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    println("  Vault Server starting on port $SERVER_PORT")
    println("  xVault backend: ${XVaultConfig.BASE_URL}")
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // Pre-authenticate with xVault on startup
    val token = XVaultProxy.login()
    if (token != null) {
        println("  ✅ xVault connection verified")
    } else {
        println("  ⚠️  xVault connection failed — will retry on first request")
    }

    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    routing {
        get("/") {
            call.respondText("Vault Server OK — xVault: ${XVaultConfig.BASE_URL}")
        }

        // Health check
        get("/health") {
            val vaultToken = XVaultProxy.login()
            if (vaultToken != null) {
                call.respond(mapOf("status" to "ok", "xvault" to "connected"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "error", "xvault" to "disconnected"))
            }
        }

        // ── Credential CRUD ────────────────────────────────────────────────

        /**
         * GET /api/credentials/{userId}
         * List all credentials for a user. Returns array with favicon data.
         */
        get("/api/credentials/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))

            println("[API] GET /api/credentials/$userId")

            val (listCode, listBody) = XVaultProxy.listCredentialKeys(userId)

            if (listCode == 404) {
                call.respond(HttpStatusCode.OK, mapOf("credentials" to emptyList<Any>()))
                return@get
            }
            if (listCode !in 200..299) {
                call.respond(HttpStatusCode(listCode, "xVault Error"), mapOf("error" to "xVault list failed", "details" to listBody))
                return@get
            }

            // Parse keys from xVault KV2 list response
            val keys = parseKeysFromListResponse(listBody)
            println("[API] Found ${keys.size} credential keys for $userId")

            // Fetch each credential and its favicon
            val credentials = keys.mapNotNull { key ->
                val (readCode, readBody) = XVaultProxy.readCredential(userId, key)
                if (readCode !in 200..299) return@mapNotNull null

                val credData = parseCredentialData(readBody) ?: return@mapNotNull null

                // Fetch favicon for the domain
                val domain = extractDomain(credData["loginUrl"] ?: "")
                val faviconBase64 = if (domain.isNotBlank()) {
                    val bytes = XVaultProxy.fetchFavicon(domain)
                    bytes?.let { Base64.getEncoder().encodeToString(it) }
                } else null

                mapOf(
                    "id" to (credData["id"] ?: key),
                    "siteName" to (credData["siteName"] ?: credData["name"] ?: ""),
                    "username" to (credData["username"] ?: ""),
                    "password" to (credData["password"] ?: ""),
                    "loginUrl" to (credData["loginUrl"] ?: ""),
                    "dateModified" to (credData["dateModified"] ?: "0"),
                    "faviconBase64" to (faviconBase64 ?: ""),
                )
            }

            println("[API] Returning ${credentials.size} credentials for $userId")
            call.respond(mapOf("credentials" to credentials))
        }

        /**
         * POST /api/credentials/{userId}
         * Save a credential. Body: { id, siteName, username, password, loginUrl, dateModified }
         */
        post("/api/credentials/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))

            val body = call.receiveText()
            println("[API] POST /api/credentials/$userId body=${body.take(200)}")

            // Parse the credential to extract the ID for the vault path key
            val credMap = parseJsonObject(body)
            val entryId = credMap["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing credential id"))

            // Wrap in xVault KV2 data envelope
            val vaultBody = """{"data":$body}"""

            val (code, responseBody) = XVaultProxy.saveCredential(userId, entryId, vaultBody)

            if (code in 200..299) {
                println("[API] ✅ Credential saved: $userId/$entryId")
                call.respond(mapOf("status" to "saved", "id" to entryId))
            } else {
                println("[API] ❌ Save failed: HTTP $code")
                call.respond(HttpStatusCode(code, "xVault Error"), mapOf("error" to "Save failed", "details" to responseBody))
            }
        }

        /**
         * DELETE /api/credentials/{userId}/{entryId}
         * Delete a credential.
         */
        delete("/api/credentials/{userId}/{entryId}") {
            val userId = call.parameters["userId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))
            val entryId = call.parameters["entryId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing entryId"))

            println("[API] DELETE /api/credentials/$userId/$entryId")

            val (code, responseBody) = XVaultProxy.deleteCredential(userId, entryId)

            if (code in 200..299 || code == 204) {
                println("[API] ✅ Credential deleted: $userId/$entryId")
                call.respond(mapOf("status" to "deleted", "id" to entryId))
            } else {
                call.respond(HttpStatusCode(code, "xVault Error"), mapOf("error" to "Delete failed", "details" to responseBody))
            }
        }

        /**
         * GET /api/favicon/{domain}
         * Fetch favicon for a domain. Returns raw image bytes.
         */
        get("/api/favicon/{domain}") {
            val domain = call.parameters["domain"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing domain")

            val bytes = XVaultProxy.fetchFavicon(domain)
            if (bytes != null) {
                call.respondBytes(bytes, ContentType.Image.PNG)
            } else {
                call.respond(HttpStatusCode.NotFound, "No favicon found")
            }
        }
    }
}

// ── JSON helpers (no external JSON lib needed on server) ─────────────────

private fun parseKeysFromListResponse(json: String): List<String> {
    val keysMatch = Regex(""""keys"\s*:\s*\[([^\]]*)]""").find(json) ?: return emptyList()
    val keysContent = keysMatch.groupValues[1]
    return Regex(""""([^"]+)"""").findAll(keysContent).map { it.groupValues[1] }.toList()
}

private fun parseCredentialData(json: String): Map<String, String>? {
    return try {
        val dataPattern = Regex(""""data"\s*:\s*\{[^}]*"data"\s*:\s*\{([^}]+)\}""")
        val match = dataPattern.find(json) ?: return null
        val innerData = match.groupValues[1]
        parseJsonFields(innerData)
    } catch (e: Exception) {
        println("[API] Failed to parse credential data: ${e.message}")
        null
    }
}

private fun parseJsonFields(content: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    Regex(""""(\w+)"\s*:\s*"([^"]*?)"""").findAll(content).forEach {
        result[it.groupValues[1]] = it.groupValues[2]
    }
    return result
}

private fun parseJsonObject(json: String): Map<String, String> = parseJsonFields(json)

private fun extractDomain(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
