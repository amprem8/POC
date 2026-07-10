package com.example.poc

import com.example.poc.vault.*

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

/**
 * Handles the OAuth 2.0 Authorization Code + PKCE flow using Chrome Custom Tabs.
 *
 * Flow:
 * 1. Starts a localhost HTTP server on port 8769 to catch the redirect
 * 2. Opens Chrome Custom Tab with Azure AD authorization URL
 * 3. User authenticates → Azure AD redirects to http://localhost:8769/auth/callback?code=XXX
 * 4. Localhost server catches the redirect, extracts the authorization code
 * 5. Exchanges the code for tokens via the token endpoint
 * 6. Returns the result to the caller
 */
class SsoAuthHandler(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "SsoAuth"
        private const val REDIRECT_PORT = 8769
    }

    /**
     * Launches the full OIDC + PKCE authentication flow.
     * Returns [SsoAuthResult] with tokens on success, or error details on failure.
     */
    suspend fun authenticate(): SsoAuthResult = withContext(Dispatchers.IO) {
        val codeVerifier = PkceHelper.generateCodeVerifier()
        val codeChallenge = PkceHelper.generateCodeChallenge(codeVerifier)
        val state = PkceHelper.generateState()

        val authUrl = PkceHelper.buildAuthorizationUrl(
            codeChallenge = codeChallenge,
            state = state,
        )

        Log.i(TAG, "Starting OIDC auth flow")
        Log.d(TAG, "  authUrl=$authUrl")
        Log.d(TAG, "  state=$state")

        // Start localhost server to catch the redirect callback
        val callbackResult = CompletableDeferred<CallbackResult>()

        val serverThread = Thread {
            try {
                val serverSocket = ServerSocket(REDIRECT_PORT)
                serverSocket.soTimeout = 120_000 // 2-minute timeout
                Log.i(TAG, "Localhost server listening on port $REDIRECT_PORT")

                val socket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: ""
                Log.d(TAG, "Received request: $requestLine")

                // Parse the GET request to extract query parameters
                // Format: GET /auth/callback?code=XXX&state=YYY HTTP/1.1
                val uri = requestLine.substringAfter("GET ").substringBefore(" HTTP")
                val queryString = uri.substringAfter("?", "")
                val params = queryString.split("&").associate {
                    val (key, value) = it.split("=", limit = 2)
                    key to java.net.URLDecoder.decode(value, "UTF-8")
                }

                // Send a response to the browser so the user sees a confirmation page
                // JavaScript will attempt to close the tab automatically
                val responseBody = """
                    <html>
                    <head><title>Vault - SSO</title></head>
                    <body style="font-family: -apple-system, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #f5f5f7;">
                        <div style="text-align: center; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.1);">
                            <h2 style="color: #111827;">&#10004; Authentication Successful</h2>
                            <p style="color: #6b7280;">Returning to app...</p>
                        </div>
                        <script>
                            setTimeout(function() { window.close(); }, 1500);
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n$responseBody"
                socket.getOutputStream().write(response.toByteArray())
                socket.getOutputStream().flush()
                socket.close()
                serverSocket.close()

                val code = params["code"]
                val returnedState = params["state"]
                val error = params["error"]
                val errorDescription = params["error_description"]

                if (error != null) {
                    Log.e(TAG, "Auth error: $error - $errorDescription")
                    callbackResult.complete(CallbackResult.Error("$error: $errorDescription"))
                } else if (code != null) {
                    if (returnedState != state) {
                        Log.e(TAG, "State mismatch! expected=$state got=$returnedState")
                        callbackResult.complete(CallbackResult.Error("State mismatch — possible CSRF attack"))
                    } else {
                        Log.i(TAG, "Authorization code received (${code.length} chars)")
                        callbackResult.complete(CallbackResult.Success(code))
                    }
                } else {
                    Log.e(TAG, "No code or error in callback params: $params")
                    callbackResult.complete(CallbackResult.Error("No authorization code received"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Localhost server error", e)
                callbackResult.complete(CallbackResult.Error("Authentication timed out or failed: ${e.message}"))
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        // Open Chrome Custom Tab with the authorization URL (must be on main thread)
        withContext(Dispatchers.Main) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(activity, Uri.parse(authUrl))
                Log.i(TAG, "Chrome Custom Tab launched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch browser", e)
                callbackResult.complete(CallbackResult.Error("Failed to open browser: ${e.message}"))
            }
        }

        // Wait for the callback
        val result = callbackResult.await()

        // Bring app back to foreground (closes Chrome Custom Tab visually)
        withContext(Dispatchers.Main) {
            try {
                val bringBackIntent = Intent(activity, activity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                activity.startActivity(bringBackIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not bring app to foreground", e)
            }
        }

        return@withContext when (result) {
            is CallbackResult.Success -> {
                // Exchange authorization code for tokens
                exchangeCodeForTokens(result.code, codeVerifier)
            }
            is CallbackResult.Error -> {
                SsoAuthResult(success = false, error = result.message)
            }
        }
    }

    /**
     * Exchanges the authorization code for ID/access/refresh tokens.
     */
    private fun exchangeCodeForTokens(code: String, codeVerifier: String): SsoAuthResult {
        Log.i(TAG, "Exchanging authorization code for tokens...")
        Log.d(TAG, "  code length=${code.length}, verifier length=${codeVerifier.length}")
        return try {
            val url = URL(OidcConfig.TOKEN_ENDPOINT)
            Log.d(TAG, "  token endpoint=${OidcConfig.TOKEN_ENDPOINT}")
            val postData = listOf(
                "client_id" to OidcConfig.CLIENT_ID,
                "code" to code,
                "redirect_uri" to OidcConfig.REDIRECT_URI,
                "grant_type" to "authorization_code",
                "code_verifier" to codeVerifier,
            ).joinToString("&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.write(postData.toByteArray())

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            }

            Log.d(TAG, "Token response: HTTP $responseCode")

            if (responseCode !in 200..299) {
                Log.e(TAG, "Token exchange failed: $responseBody")
                return SsoAuthResult(success = false, error = "Token exchange failed (HTTP $responseCode): $responseBody")
            }

            // Parse the JSON response to extract tokens
            val tokens = parseSimpleJson(responseBody)
            val idToken = tokens["id_token"]
            val accessToken = tokens["access_token"]
            val refreshToken = tokens["refresh_token"]

            if (idToken == null) {
                Log.e(TAG, "No id_token in response")
                return SsoAuthResult(success = false, error = "No ID token received from Azure AD")
            }

            // Decode the ID token to extract email
            val claims = decodeJwtPayload(idToken)
            val email = claims["preferred_username"]
                ?: claims["email"]
                ?: claims["upn"]
                ?: claims["sub"]

            Log.i(TAG, "✅ SSO authentication successful! email=$email")
            Log.d(TAG, "  idToken=${idToken.take(50)}... accessToken=${accessToken?.take(20)}...")

            SsoAuthResult(
                success = true,
                idToken = idToken,
                accessToken = accessToken,
                refreshToken = refreshToken,
                email = email,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            SsoAuthResult(success = false, error = "Token exchange error: ${e.message}")
        }
    }

    private sealed class CallbackResult {
        data class Success(val code: String) : CallbackResult()
        data class Error(val message: String) : CallbackResult()
    }
}


