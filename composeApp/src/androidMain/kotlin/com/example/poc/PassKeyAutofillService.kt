package com.example.poc

import android.annotation.SuppressLint
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

/**
 * Android Autofill Framework service — the PRIMARY save and fill path.
 *
 * Architecture (Bitwarden-style):
 *  1. [onFillRequest] → parses AssistStructure → returns FillResponse with:
 *     • Matching credential datasets (if any)
 *     • A placeholder dataset to force field tracking (Bitwarden pattern)
 *     • SaveInfo attached to username+password autofill IDs
 *  2. User fills in and submits the form
 *  3. Android framework detects field values changed → shows save prompt
 *  4. [onSaveRequest] → parses AssistStructure → extracts real credentials → saves
 *
 * The placeholder dataset is critical: without at least one dataset, many
 * Android OEMs and Chrome versions won't track the autofill IDs, so
 * [onSaveRequest] never fires.
 *
 * On Android 14+ Chrome may use the Credential Manager API instead.
 * Both paths converge on [PasswordRepository.saveFromAutofill].
 */
@RequiresApi(Build.VERSION_CODES.O)
class PassKeyAutofillService : AutofillService() {

    companion object {
        private const val TAG = "PassKeyAutofill"

        /**
         * HTML tags that represent actual input elements (can hold user-typed values).
         * Container tags like <form>, <div>, <fieldset>, <label> must NOT be
         * classified as username/password fields — their autofillIds don't track
         * typed text, so SaveInfo pointing at them means onSaveRequest never fires.
         */
        internal val INPUT_HTML_TAGS = setOf("input", "textarea", "select")

        /** HTML tags that are containers — never classify as credential fields. */
        private val CONTAINER_HTML_TAGS = setOf(
            "form", "div", "span", "label", "fieldset", "section", "article",
            "header", "footer", "nav", "main", "aside", "table", "tr", "td",
            "th", "tbody", "thead", "tfoot", "ul", "ol", "li", "dl", "dt", "dd",
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "a", "button", "img",
        )
    }

    override fun onConnected() {
        super.onConnected()
        PasswordRepository.init(this)
        Log.i(TAG, "✅ onConnected — autofill service bound. entries=${PasswordRepository.snapshot().size} SDK=${Build.VERSION.SDK_INT}")
        PassKeyTrace.i("Autofill", "onConnected entries=${PasswordRepository.snapshot().size} SDK=${Build.VERSION.SDK_INT}")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.d(TAG, "onDisconnected — autofill service unbound (normal lifecycle)")
        PassKeyTrace.d("Autofill", "onDisconnected — normal lifecycle unbind")
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
        if (parsed.usernameField != null) {
            Log.i(TAG, "  usernameField: tag=${parsed.usernameField.htmlTag} confidence=${parsed.usernameField.confidence}")
        }
        if (parsed.passwordField != null) {
            Log.i(TAG, "  passwordField: tag=${parsed.passwordField.htmlTag} confidence=${parsed.passwordField.confidence}")
        }
        PassKeyTrace.i(
            "Autofill",
            "onFillRequest pkg=${parsed.packageName} webDomain=${parsed.webDomain} origin=$requestedOrigin " +
                "hasUser=${parsed.usernameField != null} hasPass=${parsed.passwordField != null}"
        )

        val passwordId = parsed.passwordField?.autofillId
        val usernameId = parsed.usernameField?.autofillId

        // ── If no password field was found, we cannot fill or save ──────────
        if (passwordId == null) {
            Log.w(TAG, "⚠️ onFillRequest: no password field — returning null (page may still be loading)")
            PassKeyTrace.w("Autofill", "onFillRequest no password field — returning null so browser retries")
            callback.onSuccess(null)
            return
        }

        // ── Build saveIds — must include at least the password field ─────────
        val saveIds = listOfNotNull(usernameId, passwordId).toTypedArray()
        Log.d(TAG, "  saveIds.size=${saveIds.size}  usernameId=${usernameId}  passwordId=${passwordId}")

        val responseBuilder = FillResponse.Builder()

        // ── Existing credentials ─────────────────────────────────────────────
        val matches = if (requestedOrigin.isNotBlank()) PasswordRepository.findMatches(requestedOrigin) else emptyList()
        Log.i(TAG, "  findMatches('$requestedOrigin') → ${matches.size} entries")
        PassKeyTrace.i("Autofill", "onFillRequest matches=${matches.size} for origin='$requestedOrigin'")
        matches.forEach { entry ->
            Log.d(TAG, "    ↳ dataset: ${entry.username} @ ${entry.siteName}")
            responseBuilder.addDataset(buildDataset(parsed, entry))
        }

        // ── Placeholder dataset (Bitwarden pattern) ─────────────────────────
        // CRITICAL: Always add at least one dataset so the autofill framework
        // begins tracking the form's autofill IDs. Without this, onSaveRequest
        // may never fire on many devices and Chrome versions.
        // The placeholder uses null values — tapping it does nothing (no app
        // launch, no fill), but the framework starts tracking the fields.
        if (matches.isEmpty()) {
            Log.i(TAG, "  Adding placeholder dataset (vault empty for this origin)")
            PassKeyTrace.i("Autofill", "onFillRequest adding placeholder dataset — no matches for origin='$requestedOrigin'")
            val placeholderDataset = buildPlaceholderDataset(parsed)
            if (placeholderDataset != null) {
                responseBuilder.addDataset(placeholderDataset)
            }
        }

        // ── SaveInfo ─────────────────────────────────────────────────────────
        val saveType = if (usernameId != null)
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        else
            SaveInfo.SAVE_DATA_TYPE_PASSWORD

        val saveInfoBuilder = SaveInfo.Builder(saveType, saveIds)
        // FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE: triggers save when form fields disappear
        // (e.g. after the user submits the login form and the page navigates away).
        // NOTE: Do NOT add FLAG_DELAY_SAVE — it prevents save from triggering on
        //       Samsung and some other OEMs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            Log.d(TAG, "  SaveInfo flag: FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE")
        }
        val originLabel = if (requestedOrigin.isNotBlank()) requestedOrigin else (parsed.packageName ?: "this app")
        saveInfoBuilder.setDescription("Save your password for $originLabel?")
        responseBuilder.setSaveInfo(saveInfoBuilder.build())

        // ── Android 13+ (Tiramisu): header requires at least 1 dataset ─────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            responseBuilder.setHeader(
                RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, "PassKey")
                }
            )
        }

        val totalDatasets = matches.size + if (matches.isEmpty()) 1 else 0
        Log.i(TAG, "✅ onFillRequest → FillResponse  saveLabel='$originLabel'  datasets=$totalDatasets")
        PassKeyTrace.i("Autofill", "onFillRequest returning FillResponse saveLabel='$originLabel' datasets=$totalDatasets")
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
            Log.i(TAG, "  usernameTag=${parsed.usernameField?.htmlTag}  passwordTag=${parsed.passwordField?.htmlTag}")
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

            // ── Reject masked passwords (safety valve) ──────────────────────
            val maskChars = setOf('•', '●', '◦', '○', '*', '⬤', '⚫', '■', '█', '\u2022', '\u25CF')
            val isMasked = password.all { it in maskChars }
            if (isMasked) {
                Log.w(TAG, "❌ REJECTED save — password is all mask chars (${password.length} chars)")
                PassKeyTrace.w("Autofill", "REJECTED save — masked password detected")
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
            Log.i(TAG, "SAVE_DEBUG user=$username passwordLength=${password.length} origin=$origin")
            PasswordRepository.saveFromAutofill(entry)
            Log.i(TAG, "✅ PasswordRepository.save() — credential stored successfully")
            PassKeyTrace.i("Autofill", "save SUCCESS origin=${entry.loginUrl} user=${entry.username}")
        } catch (e: Exception) {
            Log.e(TAG, "onSaveRequest EXCEPTION", e)
            PassKeyTrace.e("Autofill", "onSaveRequest EXCEPTION", e)
        }
        callback.onSuccess()
    }

    // ── Dataset builders ─────────────────────────────────────────────────────

    private fun buildDataset(parsed: ParsedLoginForm, entry: PasswordEntry): Dataset {
        val label = " ${entry.siteName} — ${entry.username}"
        val builder = Dataset.Builder()
        parsed.usernameField?.autofillId?.let { builder.setValue(it, AutofillValue.forText(entry.username), chipView(label)) }
        parsed.passwordField?.autofillId?.let  { builder.setValue(it, AutofillValue.forText(entry.password), chipView(label)) }
        return builder.build()
    }

    /**
     * Builds a placeholder dataset with null values.
     * Following Bitwarden's pattern: this dataset doesn't fill anything,
     * but its presence forces the autofill framework to begin tracking the
     * form's autofill IDs, which is required for onSaveRequest to fire.
     *
     * Tapping this dataset does nothing visible (values are null, no
     * authentication intent) — it just dismisses the autofill dropdown.
     */
    private fun buildPlaceholderDataset(parsed: ParsedLoginForm): Dataset? {
        val passwordId = parsed.passwordField?.autofillId ?: return null
        val label = "PassKey"

        val builder = Dataset.Builder()
        // No setAuthentication — tapping just dismisses the dropdown.
        // Null values mean nothing is filled into the fields.
        parsed.usernameField?.autofillId?.let { builder.setValue(it, null, chipView(label)) }
        builder.setValue(passwordId, null, chipView(label))
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
            Log.d(TAG, "  ParsedLoginForm result: webDomain=$webDomain " +
                "hasUser=${bestUsername != null} userTag=${bestUsername?.htmlTag} " +
                "hasPass=${bestPassword != null} passTag=${bestPassword?.htmlTag}")
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

            // ── Determine if this node can be a credential field ────────────
            // Only actual input elements (<input>, <textarea>, <select>, or native
            // Android EditText) can hold user-typed values. Container elements
            // like <form>, <div>, <label> must be SKIPPED — their autofillIds
            // don't track typed text, and pointing SaveInfo at them means
            // onSaveRequest never fires (the framework never sees a value change).
            val isHtmlContainer = htmlTag.isNotBlank() &&
                htmlTag !in PassKeyAutofillService.INPUT_HTML_TAGS
            val canBeField = !isHtmlContainer

            if (isHtmlContainer && (webDomain != null || htmlTag.isNotBlank())) {
                val indent = "  ".repeat(depth)
                Log.v(TAG, "$indent SKIP container tag=$htmlTag name=${htmlAttrs["name"].orEmpty()} webDomain=$webDomain")
            }

            // Only classify if this is a real input element
            val isPassword: Boolean
            val isUsername: Boolean

            if (canBeField) {
                // Build semantic label from the input's own attributes only
                val semanticLabel = listOf(
                    hintText, idEntry, text,
                    htmlAttrs["name"].orEmpty(),
                    htmlAttrs["id"].orEmpty(),
                ).joinToString(" ")

                isPassword = hints.any { "password" in it } ||
                    autoComplete.contains("password") ||
                    htmlType == "password" ||
                    semanticLabel.contains("password") ||
                    node.isPasswordInputType()

                isUsername = !isPassword && (
                    hints.any { it.contains("username") || it.contains("email") || it.contains("login") } ||
                    autoComplete in setOf("username", "email", "current-username") ||
                    htmlType in setOf("email", "tel") ||
                    semanticLabel.contains("user") || semanticLabel.contains("email") ||
                    semanticLabel.contains("login") || semanticLabel.contains("account") ||
                    semanticLabel.contains("phone") ||
                    node.isEmailInputType() ||
                    (htmlTag == "input" && (htmlType == "text" || htmlType.isEmpty()))
                )

                if (isPassword || isUsername) {
                    val indent = "  ".repeat(depth)
                    Log.v(TAG, "$indent node tag=$htmlTag type=$htmlType ac=$autoComplete hint=$hintText id=$idEntry " +
                        "name=${htmlAttrs["name"].orEmpty()} isPass=$isPassword isUser=$isUsername webDomain=$webDomain")
                }

                node.autofillId?.let { id ->
                    when {
                        isPassword -> {
                            // <input type=password> gets highest confidence
                            val conf = if (htmlTag == "input" && htmlType == "password") 120
                                       else if (htmlTag == "input") 110
                                       else 100
                            passwordField = ParsedField(id, node.currentValue(), conf, htmlTag)
                            Log.i(TAG, "  ✅ PASSWORD field  tag=$htmlTag  id=$idEntry  hint=$hintText  " +
                                "value=[${node.currentValue()?.length ?: 0} chars]  confidence=$conf")
                            PassKeyTrace.i("Autofill", "PASSWORD field found tag=$htmlTag id=$idEntry hint=$hintText webDomain=$webDomain conf=$conf")
                        }
                        isUsername -> {
                            // <input type=text> or <input type=email> gets higher confidence
                            val conf = if (htmlTag == "input" && htmlType in setOf("text", "email", "tel")) 100
                                       else if (htmlTag == "input") 90
                                       else 70  // native Android EditText or unknown
                            usernameField = ParsedField(id, node.currentValue(), conf, htmlTag)
                            Log.i(TAG, "  ✅ USERNAME field  tag=$htmlTag  id=$idEntry  hint=$hintText  " +
                                "value='${node.currentValue()}'  confidence=$conf")
                            PassKeyTrace.i("Autofill", "USERNAME field found tag=$htmlTag id=$idEntry hint=$hintText value='${node.currentValue()}' conf=$conf")
                        }
                    }
                }
            } else {
                isPassword = false
                isUsername = false
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
data class ParsedField(
    val autofillId: AutofillId,
    val currentValue: String?,
    val confidence: Int,
    val htmlTag: String = "",
) {
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

@SuppressLint("NewApi")
private fun AssistStructure.ViewNode.isPasswordInputType(): Boolean {
    val cls  = inputType and InputType.TYPE_MASK_CLASS
    val mask = inputType and InputType.TYPE_MASK_VARIATION
    return (cls == InputType.TYPE_CLASS_TEXT && (
        mask == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        mask == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
        mask == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    )) || (cls == InputType.TYPE_CLASS_NUMBER &&
        mask == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
}

@SuppressLint("NewApi")
private fun AssistStructure.ViewNode.isEmailInputType(): Boolean {
    val cls  = inputType and InputType.TYPE_MASK_CLASS
    val mask = inputType and InputType.TYPE_MASK_VARIATION
    return cls == InputType.TYPE_CLASS_TEXT && (
        mask == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
        mask == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
}
