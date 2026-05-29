package com.example.poc

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
class PassKeyAutofillService : AutofillService() {

    companion object {
        private const val TAG = "PassKeyAutofill"
    }

    override fun onConnected() {
        super.onConnected()
        PasswordRepository.init(this)
        Log.i(TAG, "✅ onConnected — autofill service bound. entries=${PasswordRepository.snapshot().size}")
        PassKeyTrace.i("Autofill", "onConnected entries=${PasswordRepository.snapshot().size}")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.w(TAG, "⚠️ onDisconnected — autofill service unbound")
        PassKeyTrace.w("Autofill", "onDisconnected — service was unbound by OS")
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val contexts = request.fillContexts
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " onFillRequest  contexts=${contexts.size}  flags=${request.flags}")
        PassKeyTrace.i("Autofill", "onFillRequest contexts=${contexts.size} flags=${request.flags}")

        val structure = contexts.lastOrNull()?.structure
        if (structure == null) {
            Log.w(TAG, "❌ onFillRequest aborted: null AssistStructure")
            PassKeyTrace.w("Autofill", "onFillRequest aborted — null AssistStructure")
            callback.onSuccess(null)
            return
        }

        Log.d(TAG, "  structure.activity=${structure.activityComponent}  windows=${structure.windowNodeCount}")

        val parsed = ParsedLoginForm.from(structure)
        val requestedOrigin = parsed.origin

        Log.i(TAG, "  package=${parsed.packageName}  webDomain=${parsed.webDomain}  origin=$requestedOrigin")
        Log.i(TAG, "  hasUsername=${parsed.usernameField != null}  hasPassword=${parsed.passwordField != null}")
        PassKeyTrace.i(
            "Autofill",
            "onFillRequest pkg=${parsed.packageName} webDomain=${parsed.webDomain} origin=$requestedOrigin " +
                "hasUser=${parsed.usernameField != null} hasPass=${parsed.passwordField != null}"
        )

        val passwordId = parsed.passwordField?.autofillId
        val usernameId = parsed.usernameField?.autofillId

        // ── If no password field was found, we cannot fill or save ──────────
        // Exception: if we have a webDomain but no password field yet the page
        // may still be loading.  Return null so Chrome retries on next focus.
        if (passwordId == null) {
            Log.w(TAG, "⚠️ onFillRequest: no password field — returning null (page may still be loading)")
            PassKeyTrace.w("Autofill", "onFillRequest no password field — returning null so Chrome retries")
            callback.onSuccess(null)
            return
        }

        // ── Build saveIds — must include at least the password field ─────────
        val saveIds = listOfNotNull(usernameId, passwordId).toTypedArray()
        Log.d(TAG, "  saveIds.size=${saveIds.size}")

        val responseBuilder = FillResponse.Builder()

        // ── Existing credentials ─────────────────────────────────────────────
        val matches = if (requestedOrigin.isNotBlank()) PasswordRepository.findMatches(requestedOrigin) else emptyList()
        Log.i(TAG, "  findMatches('$requestedOrigin') → ${matches.size} entries")
        PassKeyTrace.i("Autofill", "onFillRequest matches=${matches.size} for origin='$requestedOrigin'")
        matches.forEach { entry ->
            Log.d(TAG, "    ↳ dataset: ${entry.username} @ ${entry.siteName}")
            responseBuilder.addDataset(buildDataset(parsed, entry))
        }

        // ── SaveInfo ─────────────────────────────────────────────────────────
        val saveType = if (usernameId != null)
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        else
            SaveInfo.SAVE_DATA_TYPE_PASSWORD

        val saveInfoBuilder = SaveInfo.Builder(saveType, saveIds)
        // FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE: triggers save when form fields disappear
        // (e.g. after the user submits the login form and the page navigates away).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            Log.d(TAG, "  SaveInfo FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE set")
        }
        val originLabel = if (requestedOrigin.isNotBlank()) requestedOrigin else (parsed.packageName ?: "this app")
        saveInfoBuilder.setDescription("Save your password for $originLabel?")
        responseBuilder.setSaveInfo(saveInfoBuilder.build())

        // ── Android 13+ (Tiramisu): header requires at least 1 dataset ─────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && matches.isNotEmpty()) {
            responseBuilder.setHeader(
                RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, "PassKey")
                }
            )
        }

        Log.i(TAG, "✅ onFillRequest → FillResponse  saveLabel='$originLabel'  datasets=${matches.size}")
        PassKeyTrace.i("Autofill", "onFillRequest returning FillResponse saveLabel='$originLabel' datasets=${matches.size}")
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, " onSaveRequest  contexts=${request.fillContexts.size}")
        PassKeyTrace.i("Autofill", "onSaveRequest contexts=${request.fillContexts.size}")
        try {
            PasswordRepository.init(this)
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                Log.w(TAG, "❌ onSaveRequest aborted: null structure")
                PassKeyTrace.w("Autofill", "onSaveRequest aborted — null structure")
                callback.onSuccess(); return
            }

            val parsed = ParsedLoginForm.from(structure)
            val username = parsed.usernameField?.currentValue.orEmpty().trim()
            val password = parsed.passwordField?.currentValue.orEmpty().trim()
            val origin   = normalizeCredentialOrigin(parsed.origin)

            Log.i(TAG, "  origin=$origin  user='$username'  passLen=${password.length}")
            PassKeyTrace.i("Autofill", "onSaveRequest origin=$origin user='$username' passLen=${password.length}")

            if (username.isBlank() || password.isBlank()) {
                Log.w(TAG, "❌ onSaveRequest skipped — blank credentials. user='$username' passLen=${password.length}")
                PassKeyTrace.w("Autofill", "onSaveRequest skipped blank credentials user='$username' passLen=${password.length}")
                callback.onSuccess(); return
            }
            if (origin.isBlank()) {
                Log.w(TAG, "❌ onSaveRequest skipped — blank origin")
                PassKeyTrace.w("Autofill", "onSaveRequest skipped blank origin")
                callback.onSuccess(); return
            }

            val entry = PasswordEntry(
                id           = System.currentTimeMillis().toString(),
                siteName     = originDisplayName(origin),
                username     = username,
                password     = password,
                loginUrl     = origin,
                dateModified = System.currentTimeMillis(),
            )
            Log.i(TAG, "✅ onSaveRequest saving site=${entry.siteName} user=${entry.username}")
            PasswordRepository.saveFromAutofill(entry)
            NotificationHelper.showSaved(this, entry.siteName, entry.username)
            PassKeyTrace.i("Autofill", "save SUCCESS origin=${entry.loginUrl} user=${entry.username}")
        } catch (e: Exception) {
            Log.e(TAG, "onSaveRequest EXCEPTION", e)
            PassKeyTrace.e("Autofill", "onSaveRequest EXCEPTION", e)
        }
        callback.onSuccess()
    }

    private fun buildDataset(parsed: ParsedLoginForm, entry: PasswordEntry): Dataset {
        val label = " ${entry.siteName} — ${entry.username}"
        val builder = Dataset.Builder()
        parsed.usernameField?.autofillId?.let { builder.setValue(it, AutofillValue.forText(entry.username), chipView(label)) }
        parsed.passwordField?.autofillId?.let  { builder.setValue(it, AutofillValue.forText(entry.password), chipView(label)) }
        return builder.build()
    }

    private fun chipView(text: String): RemoteViews =
        RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, text)
        }
}

// ─── ParsedLoginForm ────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
data class ParsedLoginForm(
    val usernameField: ParsedField?,
    val passwordField: ParsedField?,
    val webDomain: String?,
    val packageName: String?,
) {
    val origin: String get() = normalizeCredentialOrigin(webDomain ?: packageName)

    companion object {
        private const val TAG = "PassKeyAutofill"

        fun from(structure: AssistStructure): ParsedLoginForm {
            var bestUsername: ParsedField? = null
            var bestPassword: ParsedField? = null
            var webDomain: String? = null
            val packageName = structure.activityComponent?.packageName
            Log.d(TAG, "  ParsedLoginForm.from pkg=$packageName windows=${structure.windowNodeCount}")

            for (i in 0 until structure.windowNodeCount) {
                val win = structure.getWindowNodeAt(i)
                Log.d(TAG, "    window[$i] title=${win.title}")
                val result = traverse(win.rootViewNode, 0)
                bestUsername = ParsedField.bestOf(bestUsername, result.usernameField)
                bestPassword = ParsedField.bestOf(bestPassword, result.passwordField)
                if (webDomain.isNullOrBlank()) webDomain = result.webDomain
            }
            Log.d(TAG, "  ParsedLoginForm result: webDomain=$webDomain hasUser=${bestUsername != null} hasPass=${bestPassword != null}")
            return ParsedLoginForm(bestUsername, bestPassword, webDomain, packageName)
        }

        private fun traverse(node: AssistStructure.ViewNode, depth: Int): ParseResult {
            var usernameField: ParsedField? = null
            var passwordField: ParsedField? = null
            var webDomain = node.webDomain?.takeIf { it.isNotBlank() }

            val hints       = (node.autofillHints ?: emptyArray()).map { it.lowercase() }
            val hintText    = node.hint?.lowercase().orEmpty()
            val idEntry     = node.idEntry?.lowercase().orEmpty()
            val text        = node.text?.toString().orEmpty().lowercase()
            val htmlAttrs   = node.htmlInfo?.attributes.orEmpty().associate { it.first.lowercase() to it.second.lowercase() }
            val htmlTag     = node.htmlInfo?.tag?.lowercase().orEmpty()
            val autoComplete = htmlAttrs["autocomplete"].orEmpty()
            val htmlType    = htmlAttrs["type"].orEmpty()
            val semanticLabel = listOf(hintText, idEntry, text, htmlAttrs["name"].orEmpty(), htmlAttrs["id"].orEmpty()).joinToString(" ")

            val isPassword = hints.any { "password" in it } || autoComplete.contains("password") ||
                htmlType == "password" || semanticLabel.contains("password") || node.isPasswordInputType()

            val isUsername = !isPassword && (
                hints.any { it.contains("username") || it.contains("email") || it.contains("login") } ||
                autoComplete in setOf("username", "email", "current-username") ||
                htmlType in setOf("email", "tel") ||
                semanticLabel.contains("user") || semanticLabel.contains("email") ||
                semanticLabel.contains("login") || semanticLabel.contains("account") ||
                semanticLabel.contains("name") || semanticLabel.contains("phone") ||
                node.isEmailInputType() ||
                (htmlTag == "input" && (htmlType == "text" || htmlType.isEmpty()))
            )

            if (webDomain != null || htmlTag.isNotBlank() || isPassword || isUsername) {
                val indent = "  ".repeat(depth)
                Log.v(TAG, "$indent node tag=$htmlTag type=$htmlType ac=$autoComplete hint=$hintText id=$idEntry isPass=$isPassword isUser=$isUsername webDomain=$webDomain")
            }

            node.autofillId?.let { id ->
                when {
                    isPassword -> {
                        passwordField = ParsedField(id, node.currentValue(), 100)
                        Log.i(TAG, "  ✅ PASSWORD field  id=$idEntry  hint=$hintText  value=[${node.currentValue()?.length ?: 0} chars]")
                        PassKeyTrace.i("Autofill", "PASSWORD field found id=$idEntry hint=$hintText webDomain=$webDomain")
                    }
                    isUsername -> {
                        usernameField = ParsedField(id, node.currentValue(), 80)
                        Log.i(TAG, "  ✅ USERNAME field  id=$idEntry  hint=$hintText  value='${node.currentValue()}'")
                        PassKeyTrace.i("Autofill", "USERNAME field found id=$idEntry hint=$hintText value='${node.currentValue()}'")
                    }
                }
            }

            for (ci in 0 until node.childCount) {
                val child = traverse(node.getChildAt(ci), depth + 1)
                usernameField = ParsedField.bestOf(usernameField, child.usernameField)
                passwordField = ParsedField.bestOf(passwordField, child.passwordField)
                if (webDomain.isNullOrBlank()) webDomain = child.webDomain
            }
            return ParseResult(usernameField, passwordField, webDomain)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
data class ParsedField(val autofillId: AutofillId, val currentValue: String?, val confidence: Int) {
    companion object {
        fun bestOf(a: ParsedField?, b: ParsedField?): ParsedField? = when {
            a == null -> b; b == null -> a; b.confidence > a.confidence -> b; else -> a
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private data class ParseResult(val usernameField: ParsedField?, val passwordField: ParsedField?, val webDomain: String?)

@RequiresApi(Build.VERSION_CODES.O)
private fun AssistStructure.ViewNode.currentValue(): String? =
    autofillValue?.takeIf { it.isText }?.textValue?.toString() ?: text?.toString()

private fun AssistStructure.ViewNode.isPasswordInputType(): Boolean {
    val mask = inputType and InputType.TYPE_MASK_VARIATION
    return mask == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        mask == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
        mask == InputType.TYPE_NUMBER_VARIATION_PASSWORD
}

private fun AssistStructure.ViewNode.isEmailInputType(): Boolean {
    val mask = inputType and InputType.TYPE_MASK_VARIATION
    return mask == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
        mask == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
}
