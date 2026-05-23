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
        // Ensure repository is initialised (this service may start before Application.onCreate)
        PasswordRepository.init(this)
        Log.i(TAG, "Autofill service connected")
    }

    private fun loadEntries(): List<PasswordEntry> = PasswordRepository.snapshot()

    // ── onFillRequest — called every time a text field is focused in Chrome ─

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure: AssistStructure = request.fillContexts.last().structure
        Log.d(TAG, "onFillRequest — package: ${structure.activityComponent?.packageName}")

        val parsed = ParsedLoginForm.from(structure)
        Log.d(TAG, "Parsed — user:${parsed.usernameId} pwd:${parsed.passwordId} domain:${parsed.webDomain}")

        // Nothing detected — return null (don't crash with empty FillResponse)
        if (parsed.usernameId == null && parsed.passwordId == null) {
            Log.d(TAG, "No credential fields found, skipping")
            callback.onSuccess(null)
            return
        }

        val url = parsed.webDomain ?: parsed.packageName ?: ""
        val matchingEntries = loadEntries().filter { entry ->
            url.isNotBlank() && domainsMatch(entry.loginUrl, url)
        }
        Log.d(TAG, "Matching entries for '$url': ${matchingEntries.size}")

        val responseBuilder = FillResponse.Builder()

        // ── Add one dataset chip per matching saved credential ─────────
        matchingEntries.forEach { entry ->
            val label = "🔑 PassKey: ${entry.siteName} · ${entry.username}"
            val datasetBuilder = Dataset.Builder()

            parsed.usernameId?.let { id ->
                datasetBuilder.setValue(
                    id,
                    AutofillValue.forText(entry.username),
                    chipView("🔑 ${entry.siteName} — ${entry.username}"),
                )
            }
            parsed.passwordId?.let { id ->
                datasetBuilder.setValue(
                    id,
                    AutofillValue.forText(entry.password),
                    chipView(label),
                )
            }
            responseBuilder.addDataset(datasetBuilder.build())
        }

        // ── SaveInfo — ALWAYS attached so save prompt fires on submit ──
        val saveIds = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
        if (saveIds.isNotEmpty()) {
            val saveBuilder = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                saveIds,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                saveBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            }
            responseBuilder.setSaveInfo(saveBuilder.build())
        } else {
            // No save ids — can't build a valid FillResponse without dataset or saveinfo
            callback.onSuccess(null)
            return
        }

        callback.onSuccess(responseBuilder.build())
    }

    // ── onSaveRequest — fired after form submit, saves to SharedPrefs ──────

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback
    ) {

        try {

            if (!PassKeyAccessibilityService.userApprovedPendingSave) {

                Log.i(TAG, "User did not approve save")

                callback.onSuccess()
                return
            }

            val structure =
                request.fillContexts.last().structure

            val parsed =
                ParsedLoginForm.from(structure)

            val username =
                parsed.usernameId?.let {
                    getNodeValue(structure, it)
                }?.trim().orEmpty()

            val password =
                parsed.passwordId?.let {
                    getNodeValue(structure, it)
                }?.trim().orEmpty()

            val domain =
                parsed.webDomain
                    ?: parsed.packageName
                    ?: "unknown"

            val resolvedUsername = username.ifBlank {
                PassKeyAccessibilityService.pendingSaveUsername.trim()
            }
            val resolvedPassword = password.ifBlank {
                PassKeyAccessibilityService.pendingSavePassword.trim()
            }
            val resolvedDomain = domain.takeIf { it.isNotBlank() && it != "unknown" }
                ?: PassKeyAccessibilityService.pendingSaveDomain.trim().ifBlank { "unknown" }

            Log.i(
                TAG,
                "Save candidate rawUser='${username.take(40)}' rawPwdLen=${password.length} rawDomain=$domain fallbackUser='${PassKeyAccessibilityService.pendingSaveUsername.take(40)}' fallbackPwdLen=${PassKeyAccessibilityService.pendingSavePassword.length} fallbackDomain=${PassKeyAccessibilityService.pendingSaveDomain}"
            )

            if (
                resolvedUsername.isBlank() ||
                resolvedPassword.isBlank()
            ) {

                Log.w(
                    TAG,
                    "Username/password blank after fallback resolvedUserBlank=${resolvedUsername.isBlank()} resolvedPwdBlank=${resolvedPassword.isBlank()}"
                )

                callback.onSuccess()
                return
            }

            val entry = PasswordEntry(
                id = System.currentTimeMillis().toString(),
                siteName = domainToSiteName(resolvedDomain),
                username = resolvedUsername,
                password = resolvedPassword,
                loginUrl = resolvedDomain,
                dateModified = System.currentTimeMillis(),
            )

            PasswordRepository.init(this)

            PasswordRepository.saveRaw(this, entry)

            PasswordRepository.refresh()

            Log.d(TAG, "Repository updated instantly")

            NotificationHelper.showSaved(
                this,
                entry.siteName,
                resolvedUsername
            )

            PassKeyAccessibilityService.userApprovedPendingSave =
                false

            PassKeyAccessibilityService.pendingSaveUsername =
                ""

            PassKeyAccessibilityService.pendingSaveDomain =
                ""

            PassKeyAccessibilityService.pendingSavePassword =
                ""

            Log.i(TAG, "Saved successfully entry=${entry.loginUrl} / ${entry.username}")

        } catch (e: Exception) {

            Log.e(TAG, "Save failed", e)
        }

        callback.onSuccess()
    }


    // ── View helpers ──────────────────────────────────────────────────────

    private fun chipView(text: String): RemoteViews =
        RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, text)
        }

    private fun getNodeValue(structure: AssistStructure, id: AutofillId): String? {
        for (i in 0 until structure.windowNodeCount) {
            val v = findNodeValue(structure.getWindowNodeAt(i).rootViewNode, id)
            if (v != null) return v
        }
        return null
    }

    private fun findNodeValue(node: AssistStructure.ViewNode, id: AutofillId): String? {
        if (node.autofillId == id) return node.autofillValue?.textValue?.toString()
        for (i in 0 until node.childCount) {
            val r = findNodeValue(node.getChildAt(i), id)
            if (r != null) return r
        }
        return null
    }

    // ── Domain helpers ─────────────────────────────────────────────────────

    private fun domainToSiteName(domain: String): String =
        domain.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain

    private fun domainsMatch(savedUrl: String, currentDomain: String): Boolean {
        if (currentDomain.isBlank()) return false
        val clean = { url: String ->
            url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                .trimEnd('/').lowercase().split("/").first()
        }
        val saved = clean(savedUrl)
        val current = clean(currentDomain)
        return saved == current || saved.endsWith(".$current") || current.endsWith(".$saved")
    }
}

// ── AssistStructure parser ─────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
data class ParsedLoginForm(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val webDomain: String?,
    val packageName: String?,
) {
    companion object {
        private const val TAG = "ParsedLoginForm"

        fun from(structure: AssistStructure): ParsedLoginForm {
            var usernameId: AutofillId? = null
            var passwordId: AutofillId? = null
            var webDomain: String? = null
            val pkg = structure.activityComponent?.packageName

            for (i in 0 until structure.windowNodeCount) {
                val result = traverseNode(structure.getWindowNodeAt(i).rootViewNode)
                if (usernameId == null) usernameId = result.usernameId
                if (passwordId == null) passwordId = result.passwordId
                if (webDomain == null) webDomain = result.webDomain
            }
            Log.d(TAG, "from() pkg=$pkg domain=$webDomain user=$usernameId pwd=$passwordId")
            return ParsedLoginForm(usernameId, passwordId, webDomain, pkg)
        }

        private data class R(
            val usernameId: AutofillId?,
            val passwordId: AutofillId?,
            val webDomain: String?,
        )

        private fun traverseNode(node: AssistStructure.ViewNode): R {
            var usernameId: AutofillId? = null
            var passwordId: AutofillId? = null
            var webDomain: String? = node.webDomain?.takeIf { it.isNotBlank() }

            val hints = (node.autofillHints ?: emptyArray()).map { it.lowercase() }
            val inputType = node.inputType
            val htmlAttrs = node.htmlInfo?.attributes
            val htmlTag = node.htmlInfo?.tag?.lowercase()

            // ── Password detection ──────────────────────────────────────
            val isPassword = hints.any { it.contains("password") } ||
                (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                htmlAttrs?.any { it.first == "type" && it.second == "password" } == true

            // ── Username/email detection ────────────────────────────────
            val autoComplete = htmlAttrs?.find { it.first == "autocomplete" }?.second ?: ""
            val htmlType = htmlAttrs?.find { it.first == "type" }?.second ?: ""
            val htmlName = htmlAttrs?.find { it.first == "name" }?.second?.lowercase() ?: ""
            val htmlId = htmlAttrs?.find { it.first == "id" }?.second?.lowercase() ?: ""

            val isUsername = !isPassword && (
                hints.any { it.contains("username") || it.contains("email") } ||
                    autoComplete in listOf("username", "email", "current-username") ||
                    htmlType in listOf("email", "tel") ||
                    (htmlType == "text" && (
                        htmlName.contains("user") || htmlName.contains("email") ||
                            htmlName.contains("login") || htmlName.contains("account") ||
                            htmlId.contains("user") || htmlId.contains("email") ||
                            htmlId.contains("login")
                        )) ||
                    (inputType and android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 ||
                    (inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) != 0 ||
                    (htmlTag == "input" && htmlType == "text" && usernameId == null && !isPassword)
                )

            node.autofillId?.let { id ->
                when {
                    isPassword -> passwordId = id
                    isUsername -> usernameId = id
                }
            }

            for (i in 0 until node.childCount) {
                val child = traverseNode(node.getChildAt(i))
                if (usernameId == null) usernameId = child.usernameId
                if (passwordId == null) passwordId = child.passwordId
                if (webDomain == null) webDomain = child.webDomain
            }

            return R(usernameId, passwordId, webDomain)
        }
    }
}
