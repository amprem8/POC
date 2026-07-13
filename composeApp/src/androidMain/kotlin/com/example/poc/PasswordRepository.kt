package com.example.poc

import com.example.poc.vault.*

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Singleton password repository that:
 *  - Syncs credentials to/from Comcast xVault (remote source of truth)
 *  - Keeps a local encrypted cache for offline access and autofill performance
 *  - Stores notes and favicon metadata locally only (not in xVault)
 *  - Exposes a [StateFlow] of [PasswordEntry] so all Compose screens react instantly
 *
 * Architecture:
 *  - WRITE: save locally first (instant UI update), then push to xVault in background
 *  - READ: serve from local cache, sync from xVault on login/refresh
 *  - DELETE: delete locally and from xVault
 */
object PasswordRepository {

    private var listeners = mutableListOf<() -> Unit>()

    private const val TAG = "PasswordRepository"
    const val PREFS_NAME = "vault_prefs"
    const val KEY_ENTRIES = "password_entries"
    private const val KEY_LOCAL_NOTES = "local_notes"
    private const val SECURE_PREFS_NAME = "vault_secure_prefs"

    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    /** Reactive stream of all saved password entries. Collect in Compose with collectAsState(). */
    val entries: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    // Lazy — initialised the first time a Context is available (Application or Service)
    @Volatile
    private var prefs: SharedPreferences? = null

    /** The xVault user-id derived from SSO email (e.g. "pavudi605"). */
    @Volatile
    var xvaultUserId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Must be called once on app start (Application.onCreate or first service connect). */
    fun init(context: Context) {
        if (prefs != null) {
            VaultTrace.d(TAG, "init skipped prefsAlreadyInitialized entries=${_entries.value.size}")
            return
        }
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateLegacyPrefsIfNeeded(appContext)
        _entries.value = loadFromPrefs()
        Log.d(TAG, "Initialised encrypted storage — ${_entries.value.size} entries loaded")
        VaultTrace.i(TAG, "init completed entries=${_entries.value.size}")
    }

    /**
     * Set the xVault user-id from SSO email and trigger a background sync.
     */
    fun setUserFromEmail(email: String) {
        xvaultUserId = XVaultConfig.userIdFromEmail(email)
        Log.i(TAG, "xVault userId set to: $xvaultUserId")
        VaultTrace.i(TAG, "xVault userId=$xvaultUserId from email=$email")
    }

    /** Returns the current snapshot without requiring Flow collection. */
    fun snapshot(): List<PasswordEntry> = _entries.value

    fun getById(id: String): PasswordEntry? = _entries.value.firstOrNull { it.id == id }

    fun findMatches(origin: String): List<PasswordEntry> =
        _entries.value.filter { originsMatch(it.loginUrl, origin) }

    fun registerListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    /**
     * Upsert: replaces existing entry with same normalized origin+username, or appends new one.
     * The REAL password is kept in-memory (for autofill fill responses) and pushed to xVault.
     * Local disk only stores a masked placeholder — passwords are never persisted on-device.
     */
    @Synchronized
    fun saveFromAutofill(entry: PasswordEntry) {
        VaultTrace.i(TAG, "save start id=${entry.id} domain=${entry.loginUrl} user=${entry.username}")
        val current = _entries.value.toMutableList()
        current.removeAll {
            originsMatch(it.loginUrl, entry.loginUrl) && it.username.equals(entry.username, ignoreCase = true)
        }
        current.add(entry)
        persistAndEmit(current)
        Log.d(TAG, "Saved: ${entry.siteName} / ${entry.username}")
        VaultTrace.i(TAG, "save done total=${_entries.value.size}")
        notifyListeners()

        // Push to xVault in background
        pushToXVault(entry)
    }

    /** Delete by ID — removes locally and from xVault. */
    @Synchronized
    fun delete(id: String) {
        val current = _entries.value.filter { it.id != id }
        persistAndEmit(current)
        Log.d(TAG, "Deleted id=$id")
        notifyListeners()

        // Delete from xVault in background
        val userId = xvaultUserId
        if (userId != null) {
            scope.launch {
                try {
                    XVaultClient.deleteCredential(userId, id)
                } catch (e: Exception) {
                    Log.e(TAG, "xVault delete failed for id=$id", e)
                }
            }
        }
    }

    /** Update notes for a specific entry by ID. Notes are local-only (not synced to xVault). */
    @Synchronized
    fun updateNotes(id: String, notes: String) {
        val current = _entries.value.map {
            if (it.id == id) it.copy(notes = notes) else it
        }
        persistAndEmit(current)
        saveLocalNotes(id, notes)
        Log.d(TAG, "Updated notes for id=$id")
    }

    /** Force-reload from disk (e.g. after another process writes via a Service). */
    @Synchronized
    fun refresh() {
        VaultTrace.d(TAG, "refresh start")
        _entries.value = loadFromPrefs()
        Log.d(TAG, "Refreshed — ${_entries.value.size} entries loaded from SharedPreferences")
        VaultTrace.d(TAG, "refresh done entries=${_entries.value.size}")
        notifyListeners()
    }

    // ── xVault Sync (via Ktor server) ────────────────────────────────────

    /**
     * Sync credentials from xVault (via Ktor server) to local storage.
     * Called on login / app resume. Merges remote entries with local notes.
     * Also caches favicon images locally.
     */
    suspend fun syncFromXVault(): Boolean {
        val userId = xvaultUserId
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "syncFromXVault skipped — no userId")
            return false
        }

        return try {
            Log.i(TAG, "Syncing credentials from xVault (via server) for user=$userId...")
            VaultTrace.i(TAG, "syncFromXVault start userId=$userId")

            // Fetch all credentials + favicons from Ktor server
            val remoteResults = XVaultClient.fetchAllCredentials(userId)
            Log.i(TAG, "Fetched ${remoteResults.size} credentials from server")
            VaultTrace.i(TAG, "syncFromXVault fetched=${remoteResults.size}")

            // Merge with local notes and cache favicons
            val localNotesMap = loadLocalNotesMap()
            val mergedEntries = remoteResults.map { result ->
                val localNotes = localNotesMap[result.entry.id] ?: ""
                // Cache favicon locally if we got one from server
                result.faviconBytes?.let { bytes ->
                    saveFaviconToCache(result.entry.domain, bytes)
                }
                result.entry.copy(notes = localNotes)
            }

            // Also keep any local-only entries (saved offline, not yet pushed)
            val remoteIds = remoteResults.map { it.entry.id }.toSet()
            val localOnly = _entries.value.filter { it.id !in remoteIds }
            val allEntries = mergedEntries + localOnly

            // Push local-only entries to xVault via server
            localOnly.forEach { entry ->
                pushToXVault(entry)
            }

            synchronized(this) {
                persistAndEmit(allEntries)
            }
            Log.i(TAG, "✅ syncFromXVault complete: ${allEntries.size} total entries")
            VaultTrace.i(TAG, "syncFromXVault done total=${allEntries.size} remote=${remoteResults.size} localOnly=${localOnly.size}")
            notifyListeners()
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncFromXVault exception", e)
            VaultTrace.e(TAG, "syncFromXVault exception", e)
            false
        }
    }

    /**
     * Push ALL local credentials to xVault via server (initial upload).
     */
    suspend fun pushAllToXVault(): Boolean {
        val userId = xvaultUserId
        if (userId.isNullOrBlank()) return false

        val entries = _entries.value
        Log.i(TAG, "Pushing ${entries.size} entries to xVault for $userId...")

        var successCount = 0
        entries.forEach { entry ->
            if (XVaultClient.saveCredential(userId, entry)) {
                successCount++
            }
        }
        Log.i(TAG, "✅ Pushed $successCount/${entries.size} entries to xVault")
        VaultTrace.i(TAG, "pushAll done success=$successCount total=${entries.size}")
        return successCount == entries.size
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun pushToXVault(entry: PasswordEntry) {
        val userId = xvaultUserId ?: return
        scope.launch {
            try {
                XVaultClient.saveCredential(userId, entry)
            } catch (e: Exception) {
                Log.e(TAG, "xVault push failed for id=${entry.id}", e)
            }
        }
    }

    private fun persistAndEmit(list: List<PasswordEntry>) {
        prefs?.edit()?.putString(KEY_ENTRIES, serialize(list))?.commit()
        _entries.value = list
        Log.d(TAG, "Persisted ${list.size} encrypted entries")
        VaultTrace.d(TAG, "persistAndEmit entries=${list.size} domains=${list.joinToString { it.loginUrl }}")
    }

    fun loadFromPrefs(): List<PasswordEntry> {
        val json = prefs?.getString(KEY_ENTRIES, null) ?: return emptyList()
        VaultTrace.d(TAG, "loadFromPrefs jsonLength=${json.length}")
        return try {
            val array = JSONArray(json)
            val localNotesMap = loadLocalNotesMap()
            val entries = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val id = o.getString("id")
                PasswordEntry(
                    id = id,
                    siteName = o.getString("siteName"),
                    username = o.getString("username"),
                    password = o.getString("password"),
                    loginUrl = o.getString("loginUrl"),
                    dateModified = o.getLong("dateModified"),
                    notes = localNotesMap[id] ?: o.optString("notes", ""),
                )
            }
            VaultTrace.d(TAG, "loadFromPrefs parsed=${entries.size}")
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Deserialise failed", e)
            VaultTrace.e(TAG, "loadFromPrefs deserialize failed", e)
            emptyList()
        }
    }

    fun serialize(list: List<PasswordEntry>): String {
        val array = JSONArray()
        list.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("siteName", e.siteName)
                put("username", e.username)
                // Password is NOT stored locally — only in xVault.
                // We store a marker so we know this entry exists locally but
                // actual password is fetched from xVault on sync.
                put("password", "••••••••")
                put("loginUrl", e.loginUrl)
                put("dateModified", e.dateModified)
                put("notes", e.notes)
            })
        }
        return array.toString()
    }

    // ── Local notes (not synced to xVault) ────────────────────────────────

    private fun saveLocalNotes(id: String, notes: String) {
        val notesMap = loadLocalNotesMap().toMutableMap()
        if (notes.isBlank()) notesMap.remove(id) else notesMap[id] = notes
        val json = JSONObject(notesMap as Map<*, *>).toString()
        prefs?.edit()?.putString(KEY_LOCAL_NOTES, json)?.commit()
    }

    private fun loadLocalNotesMap(): Map<String, String> {
        val json = prefs?.getString(KEY_LOCAL_NOTES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Favicon cache (local-only, populated by server during sync) ──────

    private const val KEY_FAVICON_PREFIX = "favicon_"

    /**
     * Save favicon bytes to local cache (SharedPreferences, Base64 encoded).
     * Keyed by domain so all entries for the same domain share one icon.
     */
    fun saveFaviconToCache(domain: String, bytes: ByteArray) {
        if (domain.isBlank() || bytes.isEmpty()) return
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        prefs?.edit()?.putString("$KEY_FAVICON_PREFIX$domain", encoded)?.commit()
        Log.d(TAG, "Favicon cached for $domain (${bytes.size} bytes)")
    }

    /**
     * Load favicon bytes from local cache.
     * Returns null if no cached favicon exists.
     */
    fun loadFaviconFromCache(domain: String): ByteArray? {
        if (domain.isBlank()) return null
        val encoded = prefs?.getString("$KEY_FAVICON_PREFIX$domain", null) ?: return null
        return try {
            android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun migrateLegacyPrefsIfNeeded(context: Context) {
        val encryptedPrefs = prefs ?: return
        if (encryptedPrefs.contains(KEY_ENTRIES)) return

        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyJson = legacyPrefs.getString(KEY_ENTRIES, null)
            ?: return

        encryptedPrefs.edit().putString(KEY_ENTRIES, legacyJson).commit()
        legacyPrefs.edit().remove(KEY_ENTRIES).apply()
        VaultTrace.i(TAG, "migrated legacy password storage")
    }
}

