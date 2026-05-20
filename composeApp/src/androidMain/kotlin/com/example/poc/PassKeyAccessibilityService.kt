package com.example.poc

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * PassKey Accessibility Service — Production Grade
 *
 * Strategy:
 *  1. Captures password characters keystroke-by-keystroke via TYPE_VIEW_TEXT_CHANGED
 *     BEFORE Chrome masks them.
 *  2. On form submit (TYPE_WINDOW_STATE_CHANGED = page navigation), triggers credential scan.
 *  3. Shows floating save banner over Chrome — ALWAYS, even if Chrome's own dialog appears.
 *  4. Actively dismisses Google Password Manager / Chrome's built-in save dialog by
 *     finding and clicking "Never" / "Not now" / dismiss buttons using accessibility actions.
 *  5. Saves silently if banner was already shown and user came back to app later.
 *
 * Key fix: We detect Chrome's infobar/bottom-sheet save dialog nodes and auto-dismiss them
 * so only our PassKey bubble is visible to the user.
 */
class PassKeyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PassKeyA11y"

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.UCMobile.intl",
            "com.duckduckgo.mobile.android",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var pendingCheck: Runnable? = null

    // ── Credential state ──────────────────────────────────────────────────
    private var lastSeenUsername = ""
    private var lastSeenPassword = ""
    private var lastSeenDomain   = ""
    private var savePromptShowing = false
    private var lastManualSavePromptKey = ""

    // Real-time password mirror (Strategy 1 + 2 + 3)
    private var capturedPasswordText = ""
    private val passwordMirror = StringBuilder()
    private var passwordLength = 0  // track via addedCount/removedCount

    // Track which package triggered last TEXT_CHANGED on a password field
    private var lastPasswordPkg = ""

    // Username captured at type-time (before user moves to password field)
    private var capturedUsername = ""

    // ── Google Password Manager dismiss keywords ──────────────────────────
    // These are the text labels on Chrome's "Save password?" infobar dismiss buttons.
    // We click "Never" or "Not now" so Google's dialog auto-dismisses.
    private val GOOGLE_PM_DISMISS_TEXTS = listOf(
        "never", "not now", "no thanks", "cancel", "dismiss",
        // Localised variants (Samsung/AOSP)
        "never save", "not for this site",
    )
    // Class names used by Chrome's password save bottom-sheet / infobar
    private val CHROME_SAVE_DIALOG_CLASSES = setOf(
        "com.google.android.gms.auth.api.credentials.ui.SaveCredentialActivity",
        "org.chromium.chrome.browser.infobar.InfoBarContainerLayout",
        "com.android.chrome",
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        PasswordRepository.init(this)
        Log.i(TAG, "✅ PassKey Accessibility Service CONNECTED — monitoring all apps")
        // Log the service info so we can verify flags are set correctly
        serviceInfo?.let {
            Log.i(TAG, "  eventTypes=0x${it.eventTypes.toString(16)}")
            Log.i(TAG, "  flags=0x${it.flags.toString(16)}")
            Log.i(TAG, "  packageNames=${it.packageNames?.joinToString() ?: "ALL (unrestricted)"}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // ── DIAGNOSTIC: log every unique package we see events from ──
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "PKG_SEEN: $pkg | class=${event.className}")
        }

        // Soft filter — log and skip non-browser packages, but DO NOT silently drop
        if (pkg !in BROWSER_PACKAGES) {
            if (pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
                pkg.contains("opera") || pkg.contains("brave") || pkg.contains("duckduck")) {
                Log.w(TAG, "⚠️ POSSIBLE BROWSER NOT IN LIST: $pkg — add to BROWSER_PACKAGES!")
            }
            return
        }

        Log.d(TAG, "EVENT from $pkg type=${AccessibilityEvent.eventTypeToString(event.eventType)}")

        // ── Actively dismiss Google Password Manager dialog ────────────────
        // Run on every event from browser packages to catch the dialog ASAP
        tryDismissGooglePasswordManager(pkg)

        when (event.eventType) {

            // ── Text field changes ─────────────────────────────────────────
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val src = event.source
                val isPasswordNode = src?.isPassword == true
                val srcClass = src?.className?.toString() ?: "null"

                if (!isPasswordNode) {
                    // ── Capture username at type-time ──
                    val typedText = event.text.firstOrNull()?.toString() ?: ""
                    val hints = (src?.viewIdResourceName ?: "").lowercase()
                    val contentDesc = (src?.contentDescription?.toString() ?: "").lowercase()
                    src?.recycle()

                    Log.d(TAG, "TEXT_CHANGED pkg=$pkg isPassword=false class=$srcClass text='$typedText'")

                    if (typedText.isNotBlank() && typedText.length in 3..80 &&
                        !isGooglePMText(typedText) && !typedText.contains(" ")) {
                        val isUserField = hints.contains("email") || hints.contains("user") ||
                            hints.contains("login") || hints.contains("account") ||
                            contentDesc.contains("email") || contentDesc.contains("user") ||
                            looksLikeEmail(typedText) || capturedUsername.isEmpty()
                        if (isUserField) {
                            capturedUsername = typedText
                            Log.i(TAG, "✏️ CapturedUsername: '$capturedUsername'")
                        }
                    }
                    return
                }

                src?.recycle()
                Log.d(TAG, "TEXT_CHANGED pkg=$pkg isPassword=true class=$srcClass addedCount=${event.addedCount} removedCount=${event.removedCount}")
                lastPasswordPkg = pkg
                lastManualSavePromptKey = ""

                // Strategy 1: event.text[0] has real text on some builds (Samsung)
                val eventText = event.text.firstOrNull()?.toString() ?: ""
                val isMasked = eventText.isNotEmpty() &&
                    eventText.all { it == '•' || it == '*' || it == '●' || it == '\u2022' }

                if (!isMasked && eventText.isNotBlank()) {
                    capturedPasswordText = eventText
                    passwordLength = eventText.length
                    Log.d(TAG, "Strategy1: real pwd len=${eventText.length}")
                    return
                }

                // Strategy 2: track password LENGTH via addedCount/removedCount
                // Chrome always sends these even when text[] is empty for password fields
                val added = event.addedCount.coerceAtLeast(0)
                val removed = event.removedCount.coerceAtLeast(0)
                passwordLength = (passwordLength - removed + added).coerceAtLeast(0)
                Log.d(TAG, "Strategy2 length-track: +$added -$removed → len=$passwordLength")

                // Strategy 3: beforeText may have real chars on Samsung Chrome builds
                val before = event.beforeText?.toString() ?: ""
                val beforeHasReal = before.isNotEmpty() &&
                    !before.all { it == '•' || it == '*' || it == '●' || it == '\u2022' }
                if (beforeHasReal && !isMasked && eventText.isNotBlank()) {
                    capturedPasswordText = eventText
                    passwordLength = eventText.length
                    Log.d(TAG, "Strategy3 Samsung: real pwd from beforeText len=${eventText.length}")
                }
            }

            // ── Form submit / page navigation ──────────────────────────────
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                scheduleCredentialCheck(pkg, delayMs = 400)
            }

            // ── Content changed — re-scan on DOM mutations ──────────────────
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                scheduleCredentialCheck(pkg, delayMs = 900)
            }

            // ── View clicked — re-attempt dismiss ──────────────────────────
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                lastDismissAttemptMs = 0L
            }
        }
    }

    // ── Dismiss Google Password Manager ───────────────────────────────────
    /**
     * Scans all accessible windows for Chrome's "Save password?" infobar or
     * Google Password Manager bottom-sheet and clicks the "Never" / "Not now" button.
     *
     * Chrome shows its infobar via a native View hierarchy that IS visible to
     * the accessibility service. We find a clickable node whose text matches
     * one of the known dismiss labels and perform ACTION_CLICK on it.
     *
     * Throttled to once per 1 s to avoid spamming the UI thread.
     */
    private var lastDismissAttemptMs = 0L

    private fun tryDismissGooglePasswordManager(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastDismissAttemptMs < 1_000) return
        lastDismissAttemptMs = now

        // Gather all windows (requires flagRetrieveInteractiveWindows)
        val windowRoots = try {
            windows?.mapNotNull { it.root } ?: listOf(rootInActiveWindow)
        } catch (e: Exception) {
            listOf(rootInActiveWindow)
        }

        for (root in windowRoots) {
            root ?: continue
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodes(root, nodes)

            var dismissed = false
            for (n in nodes) {
                if (!n.isClickable) continue
                val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
                    .lowercase().trim()
                if (GOOGLE_PM_DISMISS_TEXTS.any { text == it || text.startsWith(it) }) {
                    // Extra guard: make sure we're inside a "save password" context
                    // by checking if any sibling/parent contains "save password" text
                    if (isInsideSavePasswordContext(nodes)) {
                        Log.i(TAG, "🚫 Dismissing Google PM dialog — clicking '$text'")
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        dismissed = true
                        break
                    }
                }
            }
            nodes.forEach { it.recycle() }
            try { root.recycle() } catch (_: Exception) {}
            if (dismissed) break
        }
    }

    /**
     * Returns true if the node list contains text indicating a "Save password" prompt,
     * e.g. "Save password?", "Save to Google", "Would you like to save …" etc.
     */
    private fun isInsideSavePasswordContext(nodes: List<AccessibilityNodeInfo>): Boolean {
        val saveKeywords = listOf(
            "save password", "save to google", "would you like to save",
            "save your password", "use saved password", "update password",
        )
        return nodes.any { n ->
            val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "")
                .lowercase()
            saveKeywords.any { kw -> text.contains(kw) }
        }
    }

    private fun scheduleCredentialCheck(pkg: String, delayMs: Long) {        pendingCheck?.let { handler.removeCallbacks(it) }
        pendingCheck = Runnable { tryExtractCredentials(pkg) }
        handler.postDelayed(pendingCheck!!, delayMs)
    }

    // ── Credential extraction ─────────────────────────────────────────────

    private fun tryExtractCredentials(pkg: String) {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "tryExtract: rootInActiveWindow is NULL — overlay permission granted?")
            return
        }
        val result = scanNode(root, pkg)
        root.recycle()

        // Priority: capturedUsername (from type-time) > scanned username
        val username = capturedUsername.ifBlank { result.username }.trim()
        val rawPassword = result.password.trim()
        val isMasked = rawPassword.isNotEmpty() &&
            rawPassword.all { it == '•' || it == '*' || it == '●' }

        val password = when {
            capturedPasswordText.isNotBlank() -> capturedPasswordText
            // If passwordLength > 0, we know user typed *something* in the password field
            // even though Chrome masked it — we can't recover the chars, but for length-
            // based verification we use it as signal to wait for autofill onSaveRequest
            passwordLength > 0 && capturedPasswordText.isBlank() -> {
                Log.d(TAG, "  ↳ Password typed (len=$passwordLength) but masked — waiting for Autofill onSaveRequest")
                ""  // Can't show bubble without actual password chars
            }
            !isMasked && rawPassword.isNotBlank() -> rawPassword
            else -> ""
        }
        val domain = result.domain
        val hasPasswordField = result.hasPasswordField

        Log.i(TAG, "tryExtract → domain=$domain user='$username' capturedUser='$capturedUsername' " +
            "capturedPwd=${capturedPasswordText.length}chars pwdLen=$passwordLength " +
            "rawPwd=${rawPassword.length}chars isMasked=$isMasked finalPwd=${password.length}chars")

        if (username.isBlank()) {
            Log.w(TAG, "  ↳ SKIP: username blank")
            return
        }
        if (password.isBlank()) {
            val shouldOfferManualSave =
                username.isNotBlank() &&
                    passwordLength > 0 &&
                    !hasPasswordField &&
                    domain.isNotBlank() &&
                    domain != pkg &&
                    !savePromptShowing &&
                    lastManualSavePromptKey != "$domain|$username"

            if (shouldOfferManualSave) {
                lastManualSavePromptKey = "$domain|$username"
                Log.i(TAG, "🔐 Chrome masked password after submit — opening PassKey manual save")
                handler.post { launchManualSaveActivity(username, domain) }
            }
            Log.w(TAG, "  ↳ SKIP: password blank — Chrome fully masks password field text")
            return
        }
        if (username == lastSeenUsername && password == lastSeenPassword &&
            domain == lastSeenDomain) {
            Log.d(TAG, "  ↳ SKIP: same as last seen — already prompted")
            return
        }
        if (savePromptShowing) {
            Log.d(TAG, "  ↳ SKIP: save prompt already on screen")
            return
        }

        Log.i(TAG, "✅ Credentials ready — showing bubble for $domain / $username")

        lastSeenUsername = username
        lastSeenPassword = password
        lastSeenDomain = domain
        passwordMirror.clear()

        handler.post { showSaveFloatingBubble(username, password, domain) }
    }

    // ── Node scanner ──────────────────────────────────────────────────────

    private data class ScanResult(
        val username: String = "",
        val password: String = "",
        val domain: String = "",
        val hasPasswordField: Boolean = false,
    )

    private fun scanNode(node: AccessibilityNodeInfo, pkg: String): ScanResult {
        var username = ""
        var password = ""
        var hasPasswordField = false
        val domain = extractDomainFromBrowser(pkg)

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(node, nodes)

        for (n in nodes) {
            val isPasswordField = n.isPassword
            val text = n.text?.toString() ?: ""
            val hints = n.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = n.contentDescription?.toString()?.lowercase() ?: ""
            val className = n.className?.toString()?.lowercase() ?: ""

            // Skip non-input nodes that have no text and are not EditText
            if (text.isBlank() && !className.contains("edittext")) continue

            when {
                isPasswordField -> {
                    hasPasswordField = true
                    // Use captured real text; fall back to node text only if unmasked
                    val nodeText = text
                    val nodeMasked = nodeText.isNotEmpty() &&
                        nodeText.all { it == '•' || it == '*' || it == '●' }
                    if (!nodeMasked && nodeText.isNotBlank()) password = nodeText
                    // capturedPasswordText is merged in tryExtractCredentials
                }
                !isPasswordField && text.isNotBlank() -> {
                    // Skip Google PM dialog texts masquerading as username fields
                    if (isGooglePMText(text)) continue
                    // Username / email detection — broad heuristics
                    val isUserField =
                        hints.contains("email") || hints.contains("user") ||
                        hints.contains("login") || hints.contains("account") ||
                        hints.contains("name") || hints.contains("phone") ||
                        contentDesc.contains("email") || contentDesc.contains("username") ||
                        contentDesc.contains("user") || looksLikeEmail(text) ||
                        (username.isEmpty() && text.length in 3..80 && !text.contains(" ") &&
                            !text.contains("Search") && !text.contains("search") &&
                            !isGooglePMText(text))
                    if (isUserField) username = text
                }
            }
        }

        nodes.forEach { it.recycle() }
        return ScanResult(username, password, domain, hasPasswordField)
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        list.add(AccessibilityNodeInfo.obtain(node))
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectAllNodes(it, list) }
        }
    }

    private fun extractDomainFromBrowser(pkg: String): String {
        val root = rootInActiveWindow ?: return pkg
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, nodes)

        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.brave.browser:id/url_bar",
        )

        var domain = pkg
        for (n in nodes) {
            val id = n.viewIdResourceName ?: continue
            if (urlBarIds.any { it == id }) {
                val url = n.text?.toString() ?: continue
                domain = url.removePrefix("https://").removePrefix("http://")
                    .removePrefix("www.").split("/").first().split("?").first()
                break
            }
        }
        nodes.forEach { it.recycle() }
        root.recycle()
        return domain
    }

    // Must be exact email format with no spaces — prevents matching Google PM button text
    // like "To Google Password Manager for premcomcast@gmail.com"
    private fun looksLikeEmail(text: String): Boolean {
        if (text.contains(" ")) return false
        val atIdx = text.indexOf('@')
        if (atIdx < 1) return false
        val domain = text.substring(atIdx + 1)
        return domain.contains(".") && domain.length > 2
    }

    // Returns true if this text is Google Password Manager UI text (not a real username)
    private fun isGooglePMText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("google password") ||
            lower.contains("save to google") ||
            lower.contains("save password") ||
            lower.startsWith("to google") ||
            lower.contains("never save") ||
            lower.contains("update password") ||
            (lower.contains("for ") && lower.contains("@"))
    }

    // ── Floating save bubble ──────────────────────────────────────────────

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    private fun showSaveFloatingBubble(username: String, password: String, domain: String) {
        if (savePromptShowing) {
            Log.w(TAG, "showBubble: already showing — skip")
            return
        }
        val overlayGranted = android.provider.Settings.canDrawOverlays(this)
        if (!overlayGranted) {
            Log.e(TAG, "❌ showBubble BLOCKED: SYSTEM_ALERT_WINDOW not granted! " +
                "Saving silently to vault instead.")
            // Save silently even if we can't show the bubble
            val siteName = domain.split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain
            saveCredential(siteName, username, password, domain)
            return
        }
        Log.i(TAG, "showBubble: overlay OK — inflating card for $domain")
        savePromptShowing = true

        val ctx = this
        val siteName = domain.split(".").firstOrNull()
            ?.replaceFirstChar { it.uppercase() } ?: domain

        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenWidth - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        // ── Card ──────────────────────────────────────────────────────
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }

        // Top row: logo + title + close
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.passkey_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also { it.marginEnd = dp(10) }
        }
        val textGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val tvTitle = TextView(ctx).apply {
            text = "Save password to PassKey?"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
        }
        val tvSub = TextView(ctx).apply {
            text = "$siteName  ·  $username"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(2), 0, 0)
        }
        textGroup.addView(tvTitle)
        textGroup.addView(tvSub)

        val tvClose = TextView(ctx).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(8), 0, 0, 0)
        }
        topRow.addView(logo)
        topRow.addView(textGroup)
        topRow.addView(tvClose)

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(10); it.bottomMargin = dp(8) }
            setBackgroundColor(Color.parseColor("#F3F4F6"))
        }

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val btnNotNow = Button(ctx).apply {
            text = "Not now"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(4) }
        }
        val saveBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#374151"))
        }
        val btnSave = Button(ctx).apply {
            text = "Save"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = saveBg
            isAllCaps = false
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        btnRow.addView(btnNotNow)
        btnRow.addView(btnSave)

        card.addView(topRow)
        card.addView(divider)
        card.addView(btnRow)

        card.translationY = -dp(120).toFloat()
        card.alpha = 0f
        floatingView = card

        val dismiss = { animateOut(card) { removeFloatingView() } }

        btnSave.setOnClickListener {
            saveCredential(siteName, username, password, domain)
            dismiss()
        }
        btnNotNow.setOnClickListener { dismiss() }
        tvClose.setOnClickListener { dismiss() }

        try {
            windowManager?.addView(card, params)
            card.animate()
                .translationY(0f).alpha(1f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
            handler.postDelayed({ dismiss() }, 16_000)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show floating view", e)
            savePromptShowing = false
        }
    }

    private fun showManualSaveFloatingBubble(username: String, domain: String) {
        if (savePromptShowing) {
            Log.w(TAG, "showManualBubble: already showing — skip")
            return
        }

        val overlayGranted = android.provider.Settings.canDrawOverlays(this)
        if (!overlayGranted) {
            launchManualSaveActivity(username, domain)
            return
        }

        val siteName = domain.split(".").firstOrNull()
            ?.replaceFirstChar { it.uppercase() } ?: domain

        savePromptShowing = true
        val ctx = this
        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenWidth - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.passkey_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also { it.marginEnd = dp(10) }
        }
        val textGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(ctx).apply {
            text = "Finish saving to PassKey"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
        }
        val tvSub = TextView(ctx).apply {
            text = "$siteName  ·  $username"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(2), 0, 0)
        }
        val tvBody = TextView(ctx).apply {
            text = "Chrome hid the password. Continue once to store it in PassKey."
            textSize = 12f
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, dp(8), 0, 0)
        }
        textGroup.addView(tvTitle)
        textGroup.addView(tvSub)
        textGroup.addView(tvBody)

        val tvClose = TextView(ctx).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(8), 0, 0, 0)
        }
        topRow.addView(logo)
        topRow.addView(textGroup)
        topRow.addView(tvClose)

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(10); it.bottomMargin = dp(8) }
            setBackgroundColor(Color.parseColor("#F3F4F6"))
        }

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val btnNotNow = Button(ctx).apply {
            text = "Not now"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(4) }
        }
        val continueBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#374151"))
        }
        val btnContinue = Button(ctx).apply {
            text = "Continue"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = continueBg
            isAllCaps = false
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        btnRow.addView(btnNotNow)
        btnRow.addView(btnContinue)

        card.addView(topRow)
        card.addView(divider)
        card.addView(btnRow)

        card.translationY = -dp(120).toFloat()
        card.alpha = 0f
        floatingView = card

        val dismiss = { animateOut(card) { removeFloatingView() } }
        btnContinue.setOnClickListener {
            launchManualSaveActivity(username, domain)
            dismiss()
        }
        btnNotNow.setOnClickListener { dismiss() }
        tvClose.setOnClickListener { dismiss() }

        try {
            windowManager?.addView(card, params)
            card.animate()
                .translationY(0f).alpha(1f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
            handler.postDelayed({ dismiss() }, 16_000)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show manual save bubble", e)
            savePromptShowing = false
            launchManualSaveActivity(username, domain)
        }
    }

    private fun launchManualSaveActivity(username: String, domain: String) {
        try {
            startActivity(
                Intent(this, ManualSaveActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(ManualSaveActivity.EXTRA_USERNAME, username)
                    putExtra(ManualSaveActivity.EXTRA_DOMAIN, domain)
                }
            )
            Log.i(TAG, "Opened ManualSaveActivity for $domain / $username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ManualSaveActivity", e)
        }
    }

    private fun animateOut(view: View, onDone: () -> Unit) {
        view.animate()
            .translationY(-dp(120).toFloat())
            .alpha(0f)
            .setDuration(250)
            .withEndAction(onDone)
            .start()
    }

    private fun removeFloatingView() {
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            floatingView = null
        }
        savePromptShowing = false
    }

    // ── Save ──────────────────────────────────────────────────────────────

    private fun saveCredential(
        siteName: String, username: String, password: String, domain: String
    ) {
        val entry = PasswordEntry(
            id = System.currentTimeMillis().toString(),
            siteName = siteName,
            username = username,
            password = password,
            loginUrl = domain,
            dateModified = System.currentTimeMillis(),
        )
        PasswordRepository.saveRaw(this, entry)
        Log.d(TAG, "Saved: $siteName / $username")
        capturedPasswordText = ""
        capturedUsername = ""
        passwordLength = 0
        passwordMirror.clear()
        NotificationHelper.showSaved(this, siteName, username)
        handler.post { Toast.makeText(this, "✅ Saved to PassKey", Toast.LENGTH_SHORT).show() }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        removeFloatingView()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingView()
        pendingCheck?.let { handler.removeCallbacks(it) }
    }
}







