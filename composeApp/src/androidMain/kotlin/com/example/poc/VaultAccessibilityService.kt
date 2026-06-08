package com.example.poc

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Vault Accessibility Service — NON-credential responsibilities only.
 *
 * This service is ONLY used for:
 *  • Overlay UI (floating button)
 *  • Filling credentials into visible fields when the user selects an entry
 *    from the vault screen (ACTION_SET_TEXT into editable fields)
 *
 * This service does NOT:
 *  • Detect login forms for save purposes
 *  • Extract credentials (username / password) from form fields
 *  • Trigger save notifications or broadcasts
 *  • Participate in credential persistence or matching
 *
 * ALL credential saving is handled exclusively by:
 *  • [VaultAutofillService.onSaveRequest] (Android Autofill Framework)
 *  • [CredentialSaveActivity] (Credential Manager on Android 14+)
 */
@SuppressLint("AccessibilityPolicy")
class VaultAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VaultA11y"

        val BROWSER_PACKAGES = setOf(
            // Chrome variants
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.chromium.chrome",
            // Firefox variants
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "org.mozilla.klar",
            // Microsoft Edge
            "com.microsoft.emmx",
            // Brave variants
            "com.brave.browser",
            "com.brave.browser.beta",
            "com.brave.browser.nightly",
            // Opera variants
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.opera.touch",
            // Samsung Internet
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            // DuckDuckGo
            "com.duckduckgo.mobile.android",
            // Vivaldi
            "com.vivaldi.browser",
            // Kiwi
            "com.kiwibrowser.browser",
            // UC Browser
            "com.UCMobile.intl",
            "com.uc.browser.en",
            // Ecosia
            "com.ecosia.android",
            // Yandex
            "com.yandex.browser",
            // Mi/Mint (Xiaomi)
            "com.mi.globalbrowser",
            "com.mi.globalbrowser.mini",
            // Naver Whale
            "com.naver.whale",
            // Via Browser
            "mark.via.gp",
            // Tor
            "org.torproject.torbrowser",
            // Bromite
            "org.bromite.bromite",
            // Puffin
            "com.cloudmosa.puffinFree",
            "com.cloudmosa.puffin",
            // Dolphin
            "mobi.mgeek.TunnyBrowser",
            // Maxthon
            "com.mx.browser",
            // Amazon Silk
            "com.amazon.cloud9",
            // Huawei Browser
            "com.huawei.browser",
            // QQ Browser
            "com.tencent.mtt",
            // Baidu Browser
            "com.baidu.browser.apps",
        )

        /** Singleton reference so the vault screen can ask us to fill. */
        @Volatile
        private var instance: VaultAccessibilityService? = null

        /**
         * Fill the currently-visible login form with [entry]'s credentials.
         * Called from the vault UI when the user manually selects an entry to fill.
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "✅ Accessibility service connected — overlay/fill-assist only (no credential saving)")
        VaultTrace.i("A11y", "onServiceConnected — overlay/fill mode only")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No credential detection or extraction.
        // This handler intentionally does nothing for credential-related events.
        // Future: overlay UI logic can be added here.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    // ── Credential fill (ACTION_SET_TEXT into visible fields) ──────────────

    /**
     * Uses ACTION_SET_TEXT to populate the username and password fields in the
     * currently-visible login form.
     * Searches all accessible windows to find the browser window, so it works
     * even when the notification shade (systemui) is the active window.
     */
    private fun doFill(entry: PasswordEntry): Boolean {
        Log.i(TAG, "🖊️ doFill site=${entry.siteName} user=${entry.username}")
        VaultTrace.i("A11y", "doFill site=${entry.siteName} user=${entry.username}")

        val allWindows: List<AccessibilityWindowInfo> = windows ?: emptyList()
        val browserRoot: AccessibilityNodeInfo? = allWindows
            .mapNotNull { it.root }
            .firstOrNull { root -> root.packageName?.toString() in BROWSER_PACKAGES }

        if (browserRoot == null) {
            Log.w(TAG, "doFill: no browser window found among ${allWindows.size} windows — falling back to rootInActiveWindow")
            val fallbackRoot = rootInActiveWindow ?: run {
                Log.e(TAG, "doFill: rootInActiveWindow=null — cannot fill")
                VaultTrace.e("A11y", "doFill FAILED — rootInActiveWindow=null")
                return false
            }
            return fillFromRoot(fallbackRoot, entry)
        }

        Log.i(TAG, "doFill: using browser window package=${browserRoot.packageName}")
        return fillFromRoot(browserRoot, entry)
    }

    private fun fillFromRoot(root: AccessibilityNodeInfo, entry: PasswordEntry): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val editableNodes = nodes.filter { it.isEditable }
        Log.d(TAG, "fillFromRoot pkg=${root.packageName} totalNodes=${nodes.size} editableNodes=${editableNodes.size}")

        var filledAny = false

        // Fill username
        val usernameNode = nodes.firstOrNull { !it.isPassword && looksLikeUsernameField(it) && it.isEditable }
            ?: nodes.firstOrNull { !it.isPassword && it.isEditable }
        if (usernameNode != null) {
            val supportsSetText = usernameNode.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
            if (supportsSetText) {
                val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, entry.username) }
                val result = usernameNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                filledAny = true
                Log.i(TAG, "fillFromRoot: username filled result=$result")
            }
        }

        // Fill password
        val passwordNode = nodes.firstOrNull { looksLikePasswordField(it) && it.isEditable }
        if (passwordNode != null) {
            val supportsSetText = passwordNode.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
            if (supportsSetText) {
                val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, entry.password) }
                val result = passwordNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                filledAny = true
                Log.i(TAG, "fillFromRoot: password filled result=$result")
            }
        }

        Log.i(TAG, "🖊️ fillFromRoot END filledAny=$filledAny site=${entry.siteName}")
        return filledAny
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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
}
