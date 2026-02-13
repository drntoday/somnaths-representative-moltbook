package com.somnath.representative.data

import android.content.Context
import com.somnath.representative.scheduler.PromptStyle
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class PromptStyleStats(
    val style: PromptStyle,
    val score: Int,
    val timesUsed: Int,
    val lastUsedAt: Long
)

private const val PREFS_NAME = "somnath_rep_prefs"
private const val KEY_PROMPT_STYLE_STATS = "promptStyleStats"

object PromptStyleStatsStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<PromptStyleStats> {
        val raw = prefs(context).getString(KEY_PROMPT_STYLE_STATS, null) ?: return emptyList()
        val parsed = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        val stats = mutableListOf<PromptStyleStats>()
        for (index in 0 until parsed.length()) {
            val item = parsed.optJSONObject(index) ?: continue
            val style = runCatching { PromptStyle.valueOf(item.optString("style")) }.getOrNull() ?: continue
            stats += PromptStyleStats(
                style = style,
                score = item.optInt("score", 0).coerceIn(-10, 10),
                timesUsed = item.optInt("timesUsed", 0).coerceAtLeast(0),
                lastUsedAt = item.optLong("lastUsedAt", 0L)
            )
        }
        return stats.distinctBy { it.style }.take(PromptStyle.entries.size)
    }

    fun getScoreMap(context: Context): Map<PromptStyle, Int> {
        val stored = getAll(context).associateBy { it.style }
        return PromptStyle.entries.associateWith { style -> stored[style]?.score ?: 0 }
    }

    fun getTopStyle(context: Context): PromptStyleStats? {
        val normalized = normalizedStats(context)
        if (normalized.none { it.timesUsed > 0 }) return null
        return normalized.maxWithOrNull(compareBy<PromptStyleStats> { it.score }.thenByDescending { it.lastUsedAt })
    }

    fun selectStyle(context: Context): PromptStyle {
        val normalized = normalizedStats(context)
        if (normalized.none { it.timesUsed > 0 }) {
            return PromptStyle.FRIENDLY
        }

        val sortedByScore = normalized.sortedByDescending { it.score }
        val highest = sortedByScore.first().style
        val neutral = normalized.minWithOrNull(compareBy<PromptStyleStats> { kotlin.math.abs(it.score) }.thenByDescending { it.lastUsedAt })?.style
            ?: PromptStyle.FRIENDLY
        val exploration = PromptStyle.entries.random()

        val roll = Random.nextInt(100)
        val picked = when {
            roll < 70 -> highest
            roll < 90 -> neutral
            else -> exploration
        }

        val lastUsedStyle = normalized.maxByOrNull { it.lastUsedAt }?.style
        if (lastUsedStyle != null && picked == lastUsedStyle) {
            val pickedScore = normalized.firstOrNull { it.style == picked }?.score ?: 0
            val alternative = normalized
                .filter { it.style != picked && it.score >= pickedScore }
                .maxWithOrNull(compareBy<PromptStyleStats> { it.score }.thenByDescending { it.lastUsedAt })
            if (alternative != null) {
                return alternative.style
            }
        }

        return picked
    }

    fun recordStyleUsed(context: Context, style: PromptStyle, timestamp: Long = System.currentTimeMillis()) {
        update(context, style) { current ->
            current.copy(
                timesUsed = current.timesUsed + 1,
                lastUsedAt = timestamp
            )
        }
    }

    fun applyScoreDelta(context: Context, style: PromptStyle, delta: Int) {
        update(context, style) { current ->
            current.copy(score = (current.score + delta).coerceIn(-10, 10))
        }
    }

    private fun normalizedStats(context: Context): List<PromptStyleStats> {
        val stored = getAll(context).associateBy { it.style }
        return PromptStyle.entries.map { style ->
            stored[style] ?: PromptStyleStats(style = style, score = 0, timesUsed = 0, lastUsedAt = 0L)
        }
    }

    private fun update(
        context: Context,
        style: PromptStyle,
        mutate: (PromptStyleStats) -> PromptStyleStats
    ) {
        val mutable = normalizedStats(context).toMutableList()
        val index = mutable.indexOfFirst { it.style == style }
        val existing = if (index >= 0) mutable[index] else PromptStyleStats(style, 0, 0, 0L)
        val mutated = mutate(existing)
        val updated = mutated.copy(score = mutated.score.coerceIn(-10, 10))
        if (index >= 0) {
            mutable[index] = updated
        } else {
            mutable += updated
        }
        persist(context, mutable.take(PromptStyle.entries.size))
    }

    private fun persist(context: Context, stats: List<PromptStyleStats>) {
        val json = JSONArray()
        stats.take(PromptStyle.entries.size).forEach { item ->
            json.put(
                JSONObject()
                    .put("style", item.style.name)
                    .put("score", item.score.coerceIn(-10, 10))
                    .put("timesUsed", item.timesUsed.coerceAtLeast(0))
                    .put("lastUsedAt", item.lastUsedAt)
            )
        }
        prefs(context).edit().putString(KEY_PROMPT_STYLE_STATS, json.toString()).apply()
    }
}
