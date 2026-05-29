package com.example.poc

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

@SuppressLint("AccessibilityPolicy")
class PassKeyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PassKeyA11y"
        // Credentials typed more than 60 s ago are discarded (stale session)
        private const val CRED_TTL_MS = 60_000L

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
        )

        /** Singleton reference so the BroadcastReceiver can ask us to fill. */
        @Volatile
        private var instance: PassKeyAccessibilityService? = null

        /**
         * Fill the currently-visible login form with [entry]'s credentials.
         * Must be called on any thread; field interaction happens on main thread.
         * Returns true if at least one field was filled.
         */
        fun fillCredentials(entry: PasswordEntry): Boolean {
            val svc = instance ?: run {
                Log.w(TAG, "fillCredentials: service not running")
                return false
            }
            return svc.doFill(entry)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSuggestionKey = ""
    private var lastSuggestionAt  = 0L

    // ── Credential tracking for the accessibility-fallback save path ──────────
    /** Origin of the login form where credentials were typed. */
    private var trackedOrigin    = ""
    /** Last value typed into what looks like a username field. */
    private var trackedUsername  = ""
    /** Last value typed into what looks like a password field. */
    private var trackedPassword  = ""
    /** Whether we've already typed into a password field in this session. */
    private var sawPasswordInput = false
    /** Timestamp of the last credential capture. */
    private var trackedAt        = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PasswordRepository.init(this)
        Log.i(TAG, "✅ Accessibility service connected — passive login-form detection active")
        PassKeyTrace.i("A11y", "onServiceConnected entries=${PasswordRepository.snapshot().size}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        val typeStr = AccessibilityEvent.eventTypeToString(event.eventType)
        PassKeyTrace.v("A11y", "event type=$typeStr pkg=$packageName")

        when (event.eventType) {
            // ── Track credentials typed in browser text fields ──────────────
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handler.post {
                runCatching { handleTextChanged(event, packageName) }
                    .onFailure { Log.e(TAG, "handleTextChanged failed", it) }
            }

            // ── Detect page navigation (URL bar change = new page loaded) ───
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handler.post {
                runCatching {
                    maybeOfferSave(packageName)
                    detectLoginForm(packageName)
                }.onFailure { Log.e(TAG, "WINDOW_STATE_CHANGED handler failed", it) }
            }

            // ── On a click in a browser, schedule a delayed save-check ──────
            // The Login button click fires TYPE_VIEW_CLICKED; the page then
            // navigates asynchronously. We wait 1.5 s for the DOM to settle.
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (packageName in BROWSER_PACKAGES && sawPasswordInput) {
                    handler.postDelayed({
                        runCatching { maybeOfferSave(packageName) }
                            .onFailure { Log.e(TAG, "delayed maybeOfferSave failed", it) }
                    }, 1500)
                }
                handler.post {
                    runCatching { detectLoginForm(packageName) }
                        .onFailure { Log.e(TAG, "detectLoginForm failed for $packageName", it) }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> handler.post {
                runCatching { detectLoginForm(packageName) }
                    .onFailure { Log.e(TAG, "detectLoginForm failed for $packageName", it) }
            }
        }
    }

    // ── Credential capture ────────────────────────────────────────────────────

    private fun handleTextChanged(event: AccessibilityEvent, packageName: String) {
        if (packageName !in BROWSER_PACKAGES) return
        val source = event.source ?: return
        val newText = event.text?.firstOrNull()?.toString() ?: return

        val isPassword = source.isPassword
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            source.hintText?.toString().orEmpty().lowercase()
        } else {
            ""
        }
        val isUsernameHint = hint.contains("user") || hint.contains("email") ||
            hint.contains("login") || hint.contains("account") || hint.contains("name")

        if (isPassword) {
            trackedPassword  = newText
            sawPasswordInput = true
            trackedAt        = System.currentTimeMillis()
            Log.d(TAG, "🔑 password typed passLen=${newText.length} origin=$trackedOrigin")
            PassKeyTrace.d("A11y", "password typed passLen=${newText.length} origin=$trackedOrigin")
        } else if (isUsernameHint || (!sawPasswordInput)) {
            // Capture the most recently typed non-password field as the username
            // (before any password has been entered, most fields are username candidates)
            if (newText.isNotBlank()) {
                trackedUsername = newText
                trackedAt = System.currentTimeMillis()
                Log.d(TAG, "👤 username typed value='$newText' origin=$trackedOrigin")
                PassKeyTrace.d("A11y", "username typed value='$newText' origin=$trackedOrigin")
            }
        }

        // Keep origin updated while we are on the page
        val root = rootInActiveWindow
        if (root != null) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectNodes(root, nodes)
            val origin = extractOrigin(packageName, nodes)
            if (origin.isNotBlank()) trackedOrigin = origin
        }
    }

    // ── Save-prompt offer ─────────────────────────────────────────────────────

    /**
     * Called on WINDOW_STATE_CHANGED.
     * If we have captured credentials and the page has transitioned away from
     * a login form (no password field visible anymore), offer to save.
     *
     * We use "no password field + window-state change" as the submit signal because
     * normalizeCredentialOrigin strips paths — /login and /secure both collapse to
     * the same host so origin comparison cannot distinguish them.
     */
    private fun maybeOfferSave(packageName: String) {
        if (packageName !in BROWSER_PACKAGES) return
        if (!sawPasswordInput) return
        if (trackedPassword.isBlank()) return

        val age = System.currentTimeMillis() - trackedAt
        if (age > CRED_TTL_MS) {
            Log.d(TAG, "maybeOfferSave: credentials stale (age=${age}ms) — discarding")
            resetTracking()
            return
        }

        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        // The login was submitted → the page navigated away → no more password field visible
        val stillHasPassword = nodes.any(::looksLikePasswordField)
        if (stillHasPassword) return   // still on a login/signup form

        // Don't fire if we already have a matching saved entry
        val alreadySaved = PasswordRepository.findMatches(trackedOrigin).any {
            it.username.equals(trackedUsername, ignoreCase = true)
        }
        if (alreadySaved) {
            Log.d(TAG, "maybeOfferSave: credentials already saved for $trackedOrigin — skipping prompt")
            resetTracking()
            return
        }

        Log.i(TAG, "✅ maybeOfferSave: password field gone after submit — offering save for origin='$trackedOrigin'")
        PassKeyTrace.i("A11y", "maybeOfferSave offering save user='$trackedUsername' origin='$trackedOrigin'")

        val site = originDisplayName(trackedOrigin)
        NotificationHelper.showSavePrompt(
            context  = this,
            siteName = site,
            username = trackedUsername,
            password = trackedPassword,
            origin   = trackedOrigin,
        )

        resetTracking()
    }

    private fun resetTracking() {
        trackedOrigin    = ""
        trackedUsername  = ""
        trackedPassword  = ""
        sawPasswordInput = false
        trackedAt        = 0L
    }

    // ── Login form detection (fill suggestion) ────────────────────────────────

    private fun detectLoginForm(packageName: String) {
        val root = rootInActiveWindow ?: run {
            PassKeyTrace.v("A11y", "detectLoginForm: rootInActiveWindow=null pkg=$packageName")
            return
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        val hasPasswordField = nodes.any(::looksLikePasswordField)
        if (!hasPasswordField) return

        val hasUsernameField = nodes.any(::looksLikeUsernameField)
        val origin = extractOrigin(packageName, nodes)

        Log.d(TAG, "detectLoginForm pkg=$packageName origin='$origin' hasUser=$hasUsernameField hasPass=$hasPasswordField nodes=${nodes.size}")
        PassKeyTrace.d("A11y", "detectLoginForm package=$packageName origin=$origin hasUsername=$hasUsernameField hasPassword=$hasPasswordField")

        if (origin.isBlank()) {
            Log.w(TAG, "  detectLoginForm: blank origin for $packageName — cannot match entries")
            PassKeyTrace.w("A11y", "detectLoginForm blank origin for pkg=$packageName")
            return
        }

        // Keep trackedOrigin current whenever we see a login form
        if (trackedOrigin.isBlank()) trackedOrigin = origin

        val matchingEntries = PasswordRepository.findMatches(origin)
        PassKeyTrace.d(
            "A11y",
            "detectLoginForm package=$packageName origin=$origin hasUsername=$hasUsernameField hasPassword=$hasPasswordField matches=${matchingEntries.size}"
        )

        if (matchingEntries.isEmpty()) {
            PassKeyTrace.v("A11y", "detectLoginForm no matches for origin=$origin — no notification shown")
            return
        }

        val key = "$origin|${matchingEntries.first().id}"
        val now = System.currentTimeMillis()
        val age = now - lastSuggestionAt
        Log.d(TAG, "detectLoginForm: throttle check key='$key' lastKey='$lastSuggestionKey' age=${age}ms")
        PassKeyTrace.d("A11y", "detectLoginForm throttleCheck key='$key' sameKey=${key == lastSuggestionKey} ageMs=$age")
        if (key == lastSuggestionKey && age < 3_000) {
            PassKeyTrace.v("A11y", "detectLoginForm throttled key=$key age=${age}ms")
            return
        }

        lastSuggestionKey = key
        lastSuggestionAt  = now
        Log.i(TAG, "✅ detectLoginForm → showing fill notification for ${matchingEntries.first().siteName}")
        PassKeyTrace.i("A11y", "detectLoginForm showing notification for ${matchingEntries.first().siteName} origin=$origin")
        NotificationHelper.showFillAvailable(this, matchingEntries.first().siteName, matchingEntries.first().id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractOrigin(packageName: String, nodes: List<AccessibilityNodeInfo>): String {
        if (packageName in BROWSER_PACKAGES) {
            nodes.firstNotNullOfOrNull { node ->
                val viewId = node.viewIdResourceName.orEmpty()
                if (
                    viewId.contains("url_bar") ||
                    viewId.contains("search_box_text") ||
                    viewId.contains("omnibox")
                ) {
                    normalizeCredentialOrigin(node.text?.toString())
                } else {
                    null
                }
            }?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return normalizeCredentialOrigin(packageName)
    }

    private fun looksLikePasswordField(node: AccessibilityNodeInfo): Boolean {
        val text = buildSemanticText(node)
        return node.isPassword || text.contains("password")
    }

    private fun looksLikeUsernameField(node: AccessibilityNodeInfo): Boolean {
        val text = buildSemanticText(node)
        return text.contains("username") || text.contains("email") || text.contains("login") || text.contains("account")
    }

    private fun buildSemanticText(node: AccessibilityNodeInfo): String = listOfNotNull(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString() else null,
        node.viewIdResourceName,
        node.text?.toString(),
        node.contentDescription?.toString(),
        node.className?.toString(),
    ).joinToString(" ").lowercase()

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        out += node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectNodes(it, out) }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
        resetTracking()
    }

    // ── Credential fill ───────────────────────────────────────────────────────

    /**
     * Uses ACTION_SET_TEXT to populate the username and password fields in the
     * currently-visible login form.
     * Searches all accessible windows to find the browser window, so it works
     * even when the notification shade (systemui) is the active window.
     */
    private fun doFill(entry: PasswordEntry): Boolean {
        Log.i(TAG, "🖊️ doFill site=${entry.siteName} user=${entry.username}")
        PassKeyTrace.i("A11y", "doFill site=${entry.siteName} user=${entry.username}")

        // rootInActiveWindow returns the notification shade when the user taps Fill
        // from the notification drawer. Scan all windows instead to find the browser.
        val allWindows: List<AccessibilityWindowInfo> = windows ?: emptyList()
        val browserRoot: AccessibilityNodeInfo? = allWindows
            .mapNotNull { it.root }
            .firstOrNull { root -> root.packageName?.toString() in BROWSER_PACKAGES }

        if (browserRoot == null) {
            Log.w(TAG, "doFill: no browser window found among ${allWindows.size} windows — falling back to rootInActiveWindow")
            PassKeyTrace.w("A11y", "doFill no browser window found — falling back to rootInActiveWindow")
            val fallbackRoot = rootInActiveWindow ?: run {
                Log.e(TAG, "doFill: rootInActiveWindow=null — cannot fill")
                PassKeyTrace.e("A11y", "doFill FAILED — rootInActiveWindow=null")
                return false
            }
            return fillFromRoot(fallbackRoot, entry)
        }

        Log.i(TAG, "doFill: using browser window package=${browserRoot.packageName}")
        PassKeyTrace.i("A11y", "doFill using browser window package=${browserRoot.packageName}")
        return fillFromRoot(browserRoot, entry)
    }

    private fun fillFromRoot(root: AccessibilityNodeInfo, entry: PasswordEntry): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val editableNodes = nodes.filter { it.isEditable }
        Log.d(TAG, "fillFromRoot pkg=${root.packageName} totalNodes=${nodes.size} editableNodes=${editableNodes.size}")
        PassKeyTrace.d("A11y", "fillFromRoot pkg=${root.packageName} totalNodes=${nodes.size} editableNodes=${editableNodes.size}")

        var filledAny = false

        // Fill username
        val usernameNode = nodes.firstOrNull { !it.isPassword && looksLikeUsernameField(it) && it.isEditable }
            ?: nodes.firstOrNull { !it.isPassword && it.isEditable }
        if (usernameNode != null) {
            val supportsSetText = usernameNode.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
            PassKeyTrace.i("A11y", "fillFromRoot usernameNode found supportsSetText=$supportsSetText")
            if (supportsSetText) {
                val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, entry.username) }
                val result = usernameNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                filledAny = true
                Log.i(TAG, "fillFromRoot: username filled result=$result")
                PassKeyTrace.i("A11y", "fillFromRoot username filled result=$result")
            } else {
                Log.w(TAG, "fillFromRoot: username node does not support ACTION_SET_TEXT")
                PassKeyTrace.w("A11y", "fillFromRoot username supportsSetText=false")
            }
        } else {
            Log.w(TAG, "fillFromRoot: no username node found (editableNodes=${editableNodes.size})")
            PassKeyTrace.w("A11y", "fillFromRoot no username node found editableNodes=${editableNodes.size}")
        }

        // Fill password
        val passwordNode = nodes.firstOrNull { looksLikePasswordField(it) && it.isEditable }
        if (passwordNode != null) {
            val supportsSetText = passwordNode.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
            PassKeyTrace.i("A11y", "fillFromRoot passwordNode found supportsSetText=$supportsSetText")
            if (supportsSetText) {
                val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, entry.password) }
                val result = passwordNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                filledAny = true
                Log.i(TAG, "fillFromRoot: password filled result=$result")
                PassKeyTrace.i("A11y", "fillFromRoot password filled result=$result")
            } else {
                Log.w(TAG, "fillFromRoot: password node does not support ACTION_SET_TEXT")
                PassKeyTrace.w("A11y", "fillFromRoot password supportsSetText=false")
            }
        } else {
            Log.w(TAG, "fillFromRoot: no password node found")
            PassKeyTrace.w("A11y", "fillFromRoot no password node found")
        }

        Log.i(TAG, "🖊️ fillFromRoot END filledAny=$filledAny site=${entry.siteName}")
        PassKeyTrace.i("A11y", "fillFromRoot END filledAny=$filledAny site=${entry.siteName}")
        return filledAny
    }
}

