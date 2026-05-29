package com.example.poc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Singleton password repository that:
 *  - Persists all entries to encrypted shared preferences
 *  - Exposes a [StateFlow] of [PasswordEntry] so all Compose screens react instantly
 *  - Accepts persistence only from [PassKeyAutofillService.onSaveRequest]
 *
 * Thread-safety: all mutations are @Synchronized. StateFlow updates are dispatched on the
 * thread that calls save/delete — callers on background threads must collect on Main.
 */
object PasswordRepository {

    private var listeners =
        mutableListOf<() -> Unit>()

    private const val TAG = "PasswordRepository"
    const val PREFS_NAME = "passkey_prefs"
    const val KEY_ENTRIES = "password_entries"
    private const val SECURE_PREFS_NAME = "passkey_secure_prefs"

    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    /** Reactive stream of all saved password entries. Collect in Compose with collectAsState(). */
    val entries: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    // Lazy — initialised the first time a Context is available (Application or Service)
    @Volatile
    private var prefs: SharedPreferences? = null

    /** Must be called once on app start (Application.onCreate or first service connect). */
    fun init(context: Context) {
        if (prefs != null) {
            PassKeyTrace.d(TAG, "init skipped prefsAlreadyInitialized entries=${_entries.value.size}")
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
        PassKeyTrace.i(TAG, "init completed entries=${_entries.value.size}")
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
        listeners.forEach {
            it.invoke()
        }
    }

    /** Upsert: replaces existing entry with same normalized origin+username, or appends new one. */
    @Synchronized
    fun saveFromAutofill(entry: PasswordEntry) {
        PassKeyTrace.i(TAG, "save start id=${entry.id} domain=${entry.loginUrl} user=${entry.username}")
        val current = _entries.value.toMutableList()
        current.removeAll {
            originsMatch(it.loginUrl, entry.loginUrl) && it.username.equals(entry.username, ignoreCase = true)
        }
        current.add(entry)
        persistAndEmit(current)
        Log.d(TAG, "Saved: ${entry.siteName} / ${entry.username}")
        PassKeyTrace.i(TAG, "save done total=${_entries.value.size}")
        notifyListeners()
    }

    /** Delete by ID. */
    @Synchronized
    fun delete(id: String) {
        val current = _entries.value.filter { it.id != id }
        persistAndEmit(current)
        Log.d(TAG, "Deleted id=$id")
        notifyListeners()
    }

    /** Force-reload from disk (e.g. after another process writes via a Service). */
    @Synchronized
    fun refresh() {
        PassKeyTrace.d(TAG, "refresh start")
        _entries.value = loadFromPrefs()
        Log.d(TAG, "Refreshed — ${_entries.value.size} entries loaded from SharedPreferences")
        PassKeyTrace.d(TAG, "refresh done entries=${_entries.value.size}")
        notifyListeners()
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun persistAndEmit(list: List<PasswordEntry>) {
        prefs?.edit()?.putString(KEY_ENTRIES, serialize(list))?.commit()
        _entries.value = list
        Log.d(TAG, "Persisted ${list.size} encrypted entries")
        PassKeyTrace.d(TAG, "persistAndEmit entries=${list.size} domains=${list.joinToString { it.loginUrl }}")
    }

    fun loadFromPrefs(): List<PasswordEntry> {
        val json = prefs?.getString(KEY_ENTRIES, null) ?: return emptyList()
        PassKeyTrace.d(TAG, "loadFromPrefs jsonLength=${json.length}")
        return try {
            val array = JSONArray(json)
            val entries = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PasswordEntry(
                    id = o.getString("id"),
                    siteName = o.getString("siteName"),
                    username = o.getString("username"),
                    password = o.getString("password"),
                    loginUrl = o.getString("loginUrl"),
                    dateModified = o.getLong("dateModified"),
                )
            }
            PassKeyTrace.d(TAG, "loadFromPrefs parsed=${entries.size}")
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Deserialise failed", e)
            PassKeyTrace.e(TAG, "loadFromPrefs deserialize failed", e)
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
                put("password", e.password)
                put("loginUrl", e.loginUrl)
                put("dateModified", e.dateModified)
            })
        }
        return array.toString()
    }

    private fun migrateLegacyPrefsIfNeeded(context: Context) {
        val encryptedPrefs = prefs ?: return
        if (encryptedPrefs.contains(KEY_ENTRIES)) return

        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyJson = legacyPrefs.getString(KEY_ENTRIES, null)
            ?: return

        encryptedPrefs.edit().putString(KEY_ENTRIES, legacyJson).commit()
        legacyPrefs.edit().remove(KEY_ENTRIES).apply()
        PassKeyTrace.i(TAG, "migrated legacy password storage")
    }
}

