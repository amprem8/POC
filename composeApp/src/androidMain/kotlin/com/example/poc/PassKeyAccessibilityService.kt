package com.example.poc

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import androidx.core.graphics.toColorInt
import kotlin.math.min

@SuppressLint("AccessibilityPolicy")
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
            getSystemService(WINDOW_SERVICE) as WindowManager

        Log.i(
            TAG,
            "Accessibility connected overlayAllowed=${Settings.canDrawOverlays(this)}"
        )
        PassKeyTrace.i(
            "A11y",
            "onServiceConnected overlayAllowed=${Settings.canDrawOverlays(this)} overlaySession=${OverlaySessionManager.isEnabled(this)}"
        )
    }

    @SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        event ?: return

        val pkg = event.packageName?.toString() ?: return

        if (pkg !in BROWSER_PACKAGES) return

        PassKeyTrace.d(
            "A11y",
            "event type=${eventTypeName(event.eventType)} pkg=$pkg loginDetected=$loginDetected passwordTyped=$passwordTyped bubbleShowing=$savePromptShowing overlaySession=${OverlaySessionManager.isEnabled(this)}"
        )

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
                            PassKeyTrace.d("A11y", "captured username=$capturedUsername")
                        }

                    }  else {

                    passwordTyped = true
                    lastPasswordTypedAt = System.currentTimeMillis()

                    if (text.isNotBlank()) {
                        pendingSavePassword = text
                    }

                    Log.d(
                        TAG,
                        "Password captured length=${pendingSavePassword.length}"
                    )

                    PassKeyTrace.d(
                        "A11y",
                        "password captured len=${pendingSavePassword.length}"
                    )
                }

                } catch (e: Exception) {

                    Log.e(TAG, "Text parse failed", e)
                }
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
                    PassKeyTrace.i("A11y", "login button clicked pkg=$pkg combined='$combined'")

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
                    PassKeyTrace.d(
                        "A11y",
                        "window change reason=${eventTypeName(event.eventType)} recentPassword=$recentPassword recentClick=$recentClick"
                    )
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
        if (now - lastDetectionAttemptAt < 300) {
            PassKeyTrace.d("A11y", "skip detection throttle delta=${now - lastDetectionAttemptAt} reason=$reason")
            return
        }
        lastDetectionAttemptAt = now

        Log.d(
            TAG,
            "Attempting login detection reason=$reason userBlank=${capturedUsername.isBlank()} pwdTyped=$passwordTyped recentClickMs=${now - lastLoginClickAt} recentPwdMs=${now - lastPasswordTypedAt}"
        )
        PassKeyTrace.d(
            "A11y",
            "attempt detection reason=$reason userBlank=${capturedUsername.isBlank()} pwdTyped=$passwordTyped recentClickMs=${now - lastLoginClickAt} recentPwdMs=${now - lastPasswordTypedAt}"
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
            PassKeyTrace.d("A11y", "skip bubble reason=$reason cause=bubbleAlreadyShowing")
            return
        }

        if (!loginDetected && System.currentTimeMillis() - lastPasswordTypedAt > 8_000) {
            Log.d(TAG, "Skip save bubble [$reason]: no login signal")
            PassKeyTrace.d("A11y", "skip bubble reason=$reason cause=noLoginSignal")
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
            PassKeyTrace.d(
                "A11y",
                "skip bubble reason=$reason cause=missingFields usernameBlank=${capturedUsername.isBlank()} passwordTyped=$passwordTyped"
            )
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Skip save bubble [$reason]: overlay permission missing")
            PassKeyTrace.w("A11y", "skip bubble reason=$reason cause=overlayPermissionMissing")
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
            PassKeyTrace.d("A11y", "skip bubble reason=$reason cause=duplicate key=$key")
            return
        }

        lastShownKey = key
        lastShownTime = now

        Log.i(
            TAG,
            "Showing save bubble [$reason] for domain=$domain username=$capturedUsername "
        )
        PassKeyTrace.i(
            "A11y",
            "show bubble reason=$reason domain=$domain user=$capturedUsername widthHint=${resources.displayMetrics.widthPixels}"
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

        PassKeyTrace.d("A11y", "extractDomain pkg=$pkg resolved=$domain")
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

    @SuppressLint("SetTextI18n", "InlinedApi")
    private fun showPendingSaveBubble(
        username: String,
        domain: String
    ) {

        if (floatingView != null) return

        savePromptShowing = true

        val metrics = resources.displayMetrics

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val isTablet =
            resources.configuration.smallestScreenWidthDp >= 600

        val isLandscape =
            resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE

        val bubbleWidth = when {

            isTablet -> (screenWidth * 0.42f).toInt()

            isLandscape -> (screenWidth * 0.60f).toInt()

            else -> (screenWidth * 0.92f).toInt()
        }

        val params = WindowManager.LayoutParams(
            bubbleWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (screenHeight * 0.06f).toInt()

        val bg = GradientDrawable().apply {

            cornerRadius = dp(22).toFloat()

            setColor(Color.WHITE)

            setStroke(
                dp(1),
                "#E5E7EB".toColorInt()
            )
        }

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            background = bg

            elevation = dp(16).toFloat()

            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
            )
        }

        val logoSize = min(
            dp(58),
            (bubbleWidth * 0.14f).toInt()
        )

        val logo = ImageView(this).apply {

            setImageResource(R.drawable.passkey_logo)

            layoutParams = LinearLayout.LayoutParams(
                logoSize,
                logoSize
            )
        }

        val content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setPadding(dp(14), 0, dp(10), 0)
        }

        val title = TextView(this).apply {

            text = "Save password to PassKey?"

            textSize = if (isTablet) 18f else 16f

            setTypeface(null, Typeface.BOLD)

            setTextColor(Color.BLACK)
        }

        val sub = TextView(this).apply {

            text = "$domain • $username"

            textSize = if (isTablet) 14f else 13f

            maxLines = 2

            setTextColor(Color.GRAY)

            setPadding(0, dp(5), 0, dp(12))
        }

        val row = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.END
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

        root.translationY = -120f
        root.alpha = 0f

        floatingView = root

        notNow.setOnClickListener {

            dismissBubble()
        }

        save.setOnClickListener {

            userApprovedPendingSave = true

            pendingSaveUsername = username.trim()

            pendingSaveDomain = domain.trim()

            pendingSavePassword = pendingSavePassword.trim()

            if (
                pendingSaveUsername.isBlank() ||
                pendingSavePassword.isBlank()
            ) {

                android.widget.Toast.makeText(
                    this,
                    "Unable to capture credentials",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val entry = PasswordEntry(
                id = System.currentTimeMillis().toString(),
                siteName = domain
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .split(".")
                    .firstOrNull()
                    ?.replaceFirstChar { it.uppercase() }
                    ?: domain,
                username = pendingSaveUsername,
                password = pendingSavePassword,
                loginUrl = pendingSaveDomain,
                dateModified = System.currentTimeMillis()
            )

            PasswordRepository.init(this)

            PasswordRepository.save(entry)

            android.widget.Toast.makeText(
                this,
                "Password saved to PassKey",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            Log.i(
                TAG,
                "Accessibility saved credential domain=$pendingSaveDomain user=$pendingSaveUsername"
            )

            pendingSaveUsername = ""
            pendingSaveDomain = ""
            pendingSavePassword = ""

            dismissBubble()
        }

        try {

            windowManager?.addView(root, params)

            root.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()

        } catch (e: Exception) {

            Log.e(TAG, "Bubble failed", e)

            dismissBubble()
        }
    }

    private fun dismissBubble() {
        PassKeyTrace.d(
            "A11y",
            "dismissBubble floatingViewPresent=${floatingView != null} capturedUser=$capturedUsername"
        )

        floatingView?.let {

            try {

                windowManager?.removeView(it)

            } catch (_: Exception) {
            }
        }

        floatingView = null

        savePromptShowing = false

        capturedUsername = ""

        passwordTyped = false

        handler.postDelayed({

            loginDetected = false
            passwordTyped = false

        }, 1500)
    }

    override fun onInterrupt() {

        Log.w(TAG, "Accessibility interrupted")

        dismissBubble()
    }
}