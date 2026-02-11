package com.somnath.representative.duplicate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val DUPLICATE_PREFS = "somnath_rep_tiny_cache"
private const val KEY_FINGERPRINTS = "fingerprints"

data class TinyFingerprintEntry(
    val hash: String,
    val ts: Long,
    val type: String
)

class TinyFingerprintCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences(DUPLICATE_PREFS, Context.MODE_PRIVATE)

    fun getRecentFingerprints(): List<TinyFingerprintEntry> {
        val raw = prefs.getString(KEY_FINGERPRINTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val hash = obj.optString("hash")
                    if (hash.isBlank()) continue
                    add(
                        TinyFingerprintEntry(
                            hash = hash,
                            ts = obj.optLong("ts", 0L),
                            type = obj.optString("type", "unknown")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addFingerprint(entry: TinyFingerprintEntry) {
        val trimmed = (getRecentFingerprints() + entry)
            .sortedBy { it.ts }
            .takeLast(20)
        persist(trimmed)
    }

    fun clear() {
        prefs.edit().remove(KEY_FINGERPRINTS).apply()
    }

    private fun persist(entries: List<TinyFingerprintEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("hash", entry.hash)
                    .put("ts", entry.ts)
                    .put("type", entry.type)
            )
        }
        prefs.edit().putString(KEY_FINGERPRINTS, array.toString()).apply()
    }
}

