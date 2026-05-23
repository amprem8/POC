package com.example.poc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Singleton password repository that:
 *  - Persists all entries to SharedPreferences("passkey_prefs", "password_entries")
 *  - Exposes a [StateFlow] of [PasswordEntry] so all Compose screens react instantly
 *  - Is written to from ANY save path: Autofill, CredentialProvider, Accessibility, Notification
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

    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())

    /** Reactive stream of all saved password entries. Collect in Compose with collectAsState(). */
    val entries: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()

    // Lazy — initialised the first time a Context is available (Application or Service)
    @Volatile
    private var prefs: SharedPreferences? = null

    /** Must be called once on app start (Application.onCreate or first service connect). */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _entries.value = loadFromPrefs()
        Log.d(TAG, "Initialised — ${_entries.value.size} entries loaded")
    }

    /** Returns the current snapshot without requiring Flow collection. */
    fun snapshot(): List<PasswordEntry> = _entries.value

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

    /** Upsert: replaces existing entry with same domain+username, or appends new one. */
    @Synchronized
    fun save(entry: PasswordEntry) {
        val current = _entries.value.toMutableList()
        current.removeAll { it.loginUrl == entry.loginUrl && it.username == entry.username }
        current.add(entry)
        persistAndEmit(current)
        Log.d(TAG, "Saved: ${entry.siteName} / ${entry.username}")
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
        _entries.value = loadFromPrefs()
        Log.d(TAG, "Refreshed — ${_entries.value.size} entries loaded from SharedPreferences")
        notifyListeners()
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun persistAndEmit(list: List<PasswordEntry>) {
        prefs?.edit()?.putString(KEY_ENTRIES, serialize(list))?.apply()
        _entries.value = list
        Log.d(TAG, "Persisted ${list.size} entries to SharedPreferences")
    }

    fun loadFromPrefs(): List<PasswordEntry> {
        val json = prefs?.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
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
        } catch (e: Exception) {
            Log.e(TAG, "Deserialise failed", e)
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

    /**
     * Convenience: save directly from raw prefs+write without requiring init().
     * Used by Services that run in isolated processes (Autofill / CredentialProvider).
     */
    fun saveRaw(context: Context, entry: PasswordEntry) {
        init(context)
        Log.d(TAG, "saveRaw called for ${entry.loginUrl} / ${entry.username}")
        save(entry)
    }
}

