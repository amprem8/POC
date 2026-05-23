package com.example.poc

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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

class PassKeyAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "PassKeyA11y"

        var pendingSaveUsername = ""
        var pendingSaveDomain = ""
        var pendingSavePassword = ""
        var userApprovedPendingSave = false

        var loginDetected = false

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser"
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var capturedUsername = ""
    private var capturedPassword = ""
    private var passwordTyped = false

    private var lastShownKey = ""
    private var lastShownTime = 0L

    private var savePromptShowing = false

    private var lastPasswordTypedAt = 0L
    private var lastLoginClickAt = 0L
    private var lastDetectionAttemptAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager =
            getSystemService(Context.WINDOW_SERVICE) as WindowManager

        Log.i(
            TAG,
            "Accessibility connected overlayAllowed=${Settings.canDrawOverlays(this)}"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        event ?: return

        val pkg = event.packageName?.toString() ?: return

        if (pkg !in BROWSER_PACKAGES) return

        when (event.eventType) {

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {

                val src = event.source ?: return

                try {

                    val isPassword = src.isPassword

                    val text =
                        event.text.firstOrNull()?.toString() ?: ""

                    if (!isPassword) {

                        if (
                            text.isNotBlank() &&
                            (
                                    text.contains("@") ||
                                            text.length > 3
                                    )
                        ) {

                            capturedUsername = text

                            Log.d(
                                TAG,
                                "Captured username: $capturedUsername"
                            )
                        }

                    } else {

                        val rawPassword = extractPasswordText(event, src)
                        if (rawPassword.isNotBlank()) {
                            capturedPassword = rawPassword
                            Log.d(TAG, "Captured password candidate len=${capturedPassword.length}")
                        }

                        if (
                            event.addedCount > 0 ||
                            event.removedCount > 0
                        ) {

                            passwordTyped = true
                            lastPasswordTypedAt = System.currentTimeMillis()

                            Log.d(
                                TAG,
                                "Password typed added=${event.addedCount} removed=${event.removedCount} pwdLen=${capturedPassword.length}"
                            )
                        }
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "Text parse failed", e)
                }

                src.recycle()
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {

                val text =
                    event.text.joinToString(" ")
                        .lowercase()

                val content =
                    event.contentDescription
                        ?.toString()
                        ?.lowercase() ?: ""

                val combined = "$text $content"

                if (
                    combined.contains("login") ||
                    combined.contains("sign in") ||
                    combined.contains("continue") ||
                    combined.contains("submit") ||
                    combined.contains("next")
                ) {

                    Log.d(TAG, "Login button clicked")

                    loginDetected = true
                    lastLoginClickAt = System.currentTimeMillis()

                    // IMMEDIATE detection
                    handler.post {

                        try {

                            tryDetectLogin(pkg, "TYPE_VIEW_CLICKED:immediate")

                        } catch (e: Exception) {

                            Log.e(TAG, "Immediate login detection failed", e)
                        }
                    }

                    // BACKUP delayed detection
                    handler.postDelayed({

                        try {

                            tryDetectLogin(pkg, "TYPE_VIEW_CLICKED:delayed")

                        } catch (e: Exception) {

                            Log.e(TAG, "Delayed login detection failed", e)
                        }

                    }, 350)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {

                val now = System.currentTimeMillis()
                val recentPassword = now - lastPasswordTypedAt < 8_000
                val recentClick = now - lastLoginClickAt < 8_000

                if (loginDetected || recentPassword || recentClick) {
                    maybeAttemptLoginDetection(
                        pkg = pkg,
                        reason = eventTypeName(event.eventType),
                    )
                }
            }
        }
    }

    private fun maybeAttemptLoginDetection(pkg: String, reason: String) {

        val now = System.currentTimeMillis()
        if (now - lastDetectionAttemptAt < 300) return
        lastDetectionAttemptAt = now

        Log.d(
            TAG,
            "Attempting login detection reason=$reason userBlank=${capturedUsername.isBlank()} pwdTyped=$passwordTyped pwdLen=${capturedPassword.length} recentClickMs=${now - lastLoginClickAt} recentPwdMs=${now - lastPasswordTypedAt}"
        )

        handler.post {
            try {
                tryDetectLogin(pkg, reason)
            } catch (e: Exception) {
                Log.e(TAG, "Login detection failed for reason=$reason", e)
            }
        }
    }

    private fun tryDetectLogin(pkg: String, reason: String) {

        if (savePromptShowing) {
            Log.d(TAG, "Skip save bubble [$reason]: bubble already showing")
            return
        }

        if (!loginDetected && System.currentTimeMillis() - lastPasswordTypedAt > 8_000) {
            Log.d(TAG, "Skip save bubble [$reason]: no login signal")
            return
        }

        if (
            capturedUsername.isBlank() ||
            !passwordTyped
        ) {
            Log.d(
                TAG,
                "Skip save bubble [$reason]: usernameBlank=${capturedUsername.isBlank()} passwordTyped=$passwordTyped"
            )
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Skip save bubble [$reason]: overlay permission missing")
            return
        }

        val domain = extractDomain(pkg)

        val key = "$domain|$capturedUsername"

        val now = System.currentTimeMillis()

        if (
            key == lastShownKey &&
            now - lastShownTime < 15000
        ) {
            Log.d(TAG, "Skip save bubble [$reason]: duplicate key=$key")
            return
        }

        lastShownKey = key
        lastShownTime = now

        Log.i(
            TAG,
            "Showing save bubble [$reason] for domain=$domain username=$capturedUsername pwdLen=${capturedPassword.length}"
        )

        showPendingSaveBubble(
            username = capturedUsername,
            domain = domain
        )
    }

    private fun extractDomain(pkg: String): String {

        val root = rootInActiveWindow ?: return pkg

        val nodes = mutableListOf<AccessibilityNodeInfo>()

        collectNodes(root, nodes)

        var domain = pkg

        nodes.forEach {

            try {

                val id =
                    it.viewIdResourceName ?: return@forEach

                if (
                    id.contains("url_bar") ||
                    id.contains("search_box_text") ||
                    id.contains("omnibox")
                ) {

                    val url = it.text?.toString() ?: ""

                    if (url.isNotBlank()) {

                        domain =
                            url.removePrefix("https://")
                                .removePrefix("http://")
                                .removePrefix("www.")
                                .split("/")
                                .first()

                        return@forEach
                    }
                }

            } catch (_: Exception) {
            }
        }

        return domain
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        list: MutableList<AccessibilityNodeInfo>
    ) {

        list.add(node)

        for (i in 0 until node.childCount) {

            try {

                node.getChild(i)?.let {
                    collectNodes(it, list)
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun extractPasswordText(
        event: AccessibilityEvent,
        src: AccessibilityNodeInfo,
    ): String {
        val candidates = listOfNotNull(
            src.text?.toString(),
            event.text.joinToString(separator = ""),
        )

        return candidates.firstOrNull { candidate ->
            candidate.isNotBlank() && !looksMasked(candidate)
        }.orEmpty()
    }

    private fun looksMasked(value: String): Boolean {
        if (value.isBlank()) return true
        return value.all { it == '•' || it == '●' || it == '*' || it == '·' || it == '◦' }
    }

    private fun eventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
        else -> "type=$eventType"
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun showPendingSaveBubble(
        username: String,
        domain: String
    ) {

        if (floatingView != null) return

        savePromptShowing = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP
        params.horizontalMargin = 0.05f
        params.y = dp(40)

        val bg = GradientDrawable().apply {

            cornerRadius = dp(18).toFloat()

            setColor(Color.WHITE)

            setStroke(
                dp(1),
                Color.parseColor("#E5E7EB")
            )
        }

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            background = bg

            elevation = dp(12).toFloat()

            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
            )
        }

        val logo = ImageView(this).apply {

            setImageResource(R.drawable.passkey_logo)

            layoutParams = LinearLayout.LayoutParams(
                dp(52),
                dp(52)
            )
        }

        val content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setPadding(dp(12), 0, dp(12), 0)
        }

        val title = TextView(this).apply {

            text = "Save password to PassKey?"

            textSize = 16f

            setTypeface(null, Typeface.BOLD)

            setTextColor(Color.BLACK)
        }

        val sub = TextView(this).apply {

            text = "$domain • $username"

            textSize = 13f

            setTextColor(Color.GRAY)

            setPadding(0, dp(4), 0, dp(10))
        }

        val row = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
        }

        val notNow = Button(this).apply {
            text = "Not now"
        }

        val save = Button(this).apply {
            text = "Save"
        }

        row.addView(notNow)
        row.addView(save)

        content.addView(title)
        content.addView(sub)
        content.addView(row)

        root.addView(logo)
        root.addView(content)

        root.translationY = -200f
        root.alpha = 0f

        floatingView = root

        notNow.setOnClickListener {

            dismissBubble()
        }

        save.setOnClickListener {

            val directSaveWorked = savePendingCredentialNow(username, domain)

            if (directSaveWorked) {
                Log.i(TAG, "Saved directly from accessibility bubble")
                android.widget.Toast.makeText(
                    this,
                    "Saved to PassKey",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                dismissBubble()
                return@setOnClickListener
            }

            pendingSaveUsername = username
            pendingSaveDomain = domain
            pendingSavePassword = capturedPassword
            userApprovedPendingSave = true

            Log.i(
                TAG,
                "User approved pending save awaiting Autofill save callback domain=$domain username=$username fallbackPwdLen=${pendingSavePassword.length}"
            )

            android.widget.Toast.makeText(
                this,
                "Saving to PassKey...",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            dismissBubble()
        }

        try {

            windowManager?.addView(root, params)

            root.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()

        } catch (e: Exception) {

            Log.e(TAG, "Bubble failed", e)

            dismissBubble()
        }
    }

    private fun savePendingCredentialNow(username: String, domain: String): Boolean {
        val password = capturedPassword.trim()
        if (username.isBlank() || domain.isBlank() || password.isBlank() || looksMasked(password)) {
            Log.w(
                TAG,
                "Direct save unavailable usernameBlank=${username.isBlank()} domainBlank=${domain.isBlank()} pwdBlank=${password.isBlank()} masked=${looksMasked(password)}"
            )
            return false
        }

        val entry = PasswordEntry(
            id = System.currentTimeMillis().toString(),
            siteName = domainToSiteName(domain),
            username = username,
            password = password,
            loginUrl = domain,
            dateModified = System.currentTimeMillis(),
        )

        PasswordRepository.saveRaw(this, entry)
        clearPendingSaveState()
        return true
    }

    private fun domainToSiteName(domain: String): String =
        domain.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain

    private fun clearPendingSaveState() {
        pendingSaveUsername = ""
        pendingSaveDomain = ""
        pendingSavePassword = ""
        userApprovedPendingSave = false
    }

    private fun dismissBubble() {

        floatingView?.let {

            try {

                windowManager?.removeView(it)

            } catch (_: Exception) {
            }
        }

        floatingView = null

        savePromptShowing = false

        capturedUsername = ""
        capturedPassword = ""

        passwordTyped = false

        handler.postDelayed({

            loginDetected = false
            passwordTyped = false

        }, 1500)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {

        super.onDestroy()

        dismissBubble()
    }
}