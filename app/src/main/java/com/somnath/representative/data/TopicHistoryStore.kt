package com.somnath.representative.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val PREFS_NAME = "somnath_rep_prefs"
private const val KEY_TOPIC_HISTORY_JSON = "topicHistoryJson"
private const val MAX_TOPIC_HISTORY = 20
private const val MIN_TOPIC_SCORE = -10
private const val MAX_TOPIC_SCORE = 10

data class TopicHistoryEntry(
    val topic: String,
    val score: Int,
    val lastUsedAt: Long,
    val timesUsed: Int
)

data class AdaptiveTopicStats(
    val topTopic: TopicHistoryEntry?,
    val totalTrackedTopics: Int,
    val lastTopicUsed: TopicHistoryEntry?
)

object TopicHistoryStore {
    fun recordTopicUsed(context: Context, topic: String, usedAt: Long = System.currentTimeMillis()) {
        if (topic.isBlank()) return
        updateEntry(context, topic) { existing ->
            if (existing == null) {
                TopicHistoryEntry(topic = topic, score = 0, lastUsedAt = usedAt, timesUsed = 1)
            } else {
                existing.copy(lastUsedAt = usedAt, timesUsed = existing.timesUsed + 1)
            }
        }
    }

    fun applyScoreDelta(context: Context, topic: String, delta: Int, usedAt: Long = System.currentTimeMillis()) {
        if (topic.isBlank()) return
        updateEntry(context, topic) { existing ->
            val currentScore = existing?.score ?: 0
            TopicHistoryEntry(
                topic = topic,
                score = (currentScore + delta).coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE),
                lastUsedAt = usedAt,
                timesUsed = existing?.timesUsed ?: 1
            )
        }
    }

    fun selectAdaptiveTopic(
        context: Context,
        defaultTopic: String,
        explorationPool: List<String>,
        now: Long = System.currentTimeMillis()
    ): String {
        val history = getEntries(context)
        if (history.isEmpty()) return defaultTopic

        val sortedByScore = history.sortedWith(compareByDescending<TopicHistoryEntry> { it.score }.thenByDescending { it.lastUsedAt })
        val topTopic = sortedByScore.firstOrNull()?.topic ?: defaultTopic
        val neutralTopic = history.minByOrNull { abs(it.score) }?.topic ?: topTopic
        val explorationTopic = if (explorationPool.isNotEmpty()) {
            val index = (now % explorationPool.size).toInt()
            explorationPool[index].replace("_", " ")
        } else {
            "$defaultTopic update"
        }

        val roll = (now % 100).toInt()
        return when {
            roll < 60 -> topTopic
            roll < 90 -> neutralTopic
            else -> explorationTopic
        }
    }

    fun getAdaptiveStats(context: Context): AdaptiveTopicStats {
        val history = getEntries(context)
        val top = history.maxWithOrNull(compareBy<TopicHistoryEntry> { it.score }.thenBy { it.lastUsedAt })
        val last = history.maxByOrNull { it.lastUsedAt }
        return AdaptiveTopicStats(
            topTopic = top,
            totalTrackedTopics = history.size,
            lastTopicUsed = last
        )
    }

    fun getEntries(context: Context): List<TopicHistoryEntry> {
        val raw = prefs(context).getString(KEY_TOPIC_HISTORY_JSON, null)
        val jsonArray = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val topic = item.optString("topic", "").trim()
                if (topic.isBlank()) continue
                add(
                    TopicHistoryEntry(
                        topic = topic,
                        score = item.optInt("score", 0).coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE),
                        lastUsedAt = item.optLong("lastUsedAt", 0L),
                        timesUsed = item.optInt("timesUsed", 1).coerceAtLeast(1)
                    )
                )
            }
        }
    }

    private fun updateEntry(
        context: Context,
        topic: String,
        update: (TopicHistoryEntry?) -> TopicHistoryEntry
    ) {
        val normalizedTopic = topic.trim()
        val existing = getEntries(context).toMutableList()
        val existingIndex = existing.indexOfFirst { it.topic.equals(normalizedTopic, ignoreCase = true) }
        val existingEntry = existing.getOrNull(existingIndex)
        val updatedEntry = update(existingEntry).copy(topic = normalizedTopic)

        if (existingIndex >= 0) {
            existing[existingIndex] = updatedEntry
        } else {
            existing.add(updatedEntry)
        }

        val trimmed = existing
            .sortedByDescending { it.lastUsedAt }
            .take(MAX_TOPIC_HISTORY)
        persist(context, trimmed)
    }

    private fun persist(context: Context, entries: List<TopicHistoryEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(
                JSONObject()
                    .put("topic", entry.topic)
                    .put("score", entry.score.coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE))
                    .put("lastUsedAt", entry.lastUsedAt)
                    .put("timesUsed", entry.timesUsed.coerceAtLeast(1))
            )
        }
        prefs(context).edit().putString(KEY_TOPIC_HISTORY_JSON, jsonArray.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
