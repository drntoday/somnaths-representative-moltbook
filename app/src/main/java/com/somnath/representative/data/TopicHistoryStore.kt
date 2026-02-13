package com.somnath.representative.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "somnath_rep_prefs"
private const val KEY_TOPIC_HISTORY_JSON = "topicHistoryJson"
private const val MAX_TOPIC_HISTORY = 20
private const val MIN_TOPIC_SCORE = -10
private const val MAX_TOPIC_SCORE = 10
private const val TOPIC_POST_COOLDOWN_MS = 6 * 60 * 60 * 1000L

data class TopicHistoryEntry(
    val topic: String,
    val score: Int,
    val lastUsedAt: Long,
    val timesUsed: Int,
    val lastPostedAt: Long
)

data class AdaptiveTopicStats(
    val topTopic: TopicHistoryEntry?,
    val totalTrackedTopics: Int,
    val lastTopicUsed: TopicHistoryEntry?
)

object TopicHistoryStore {
    fun recordTopicUsed(context: Context, topic: String, usedAt: Long = System.currentTimeMillis()) {
        val normalizedTopic = TopicGuardrails.normalizeTopic(topic)
        if (normalizedTopic.isBlank()) return
        updateEntry(context, topic) { existing ->
            if (existing == null) {
                TopicHistoryEntry(topic = normalizedTopic, score = 0, lastUsedAt = usedAt, timesUsed = 1, lastPostedAt = 0L)
            } else {
                existing.copy(lastUsedAt = usedAt, timesUsed = existing.timesUsed + 1)
            }
        }
    }

    fun applyScoreDelta(context: Context, topic: String, delta: Int, usedAt: Long = System.currentTimeMillis()) {
        val normalizedTopic = TopicGuardrails.normalizeTopic(topic)
        if (normalizedTopic.isBlank()) return
        updateEntry(context, topic) { existing ->
            val currentScore = existing?.score ?: 0
            TopicHistoryEntry(
                topic = normalizedTopic,
                score = TopicGuardrails.clampScore(
                    normalizedTopic,
                    (currentScore + delta).coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE)
                ),
                lastUsedAt = usedAt,
                timesUsed = existing?.timesUsed ?: 1,
                lastPostedAt = existing?.lastPostedAt ?: 0L
            )
        }
    }

    fun recordTopicPosted(context: Context, topic: String, postedAt: Long = System.currentTimeMillis()) {
        val normalizedTopic = TopicGuardrails.normalizeTopic(topic)
        if (normalizedTopic.isBlank()) return
        updateEntry(context, topic) { existing ->
            if (existing == null) {
                TopicHistoryEntry(
                    topic = normalizedTopic,
                    score = 0,
                    lastUsedAt = postedAt,
                    timesUsed = 1,
                    lastPostedAt = postedAt
                )
            } else {
                existing.copy(lastUsedAt = postedAt, lastPostedAt = postedAt)
            }
        }
    }

    fun isTopicPostCooldownActive(context: Context, topic: String, now: Long = System.currentTimeMillis()): Boolean {
        val normalizedTopic = TopicGuardrails.normalizeTopic(topic)
        if (normalizedTopic.isBlank()) return false
        val entry = getEntries(context).firstOrNull { it.topic == normalizedTopic } ?: return false
        return entry.lastPostedAt > 0L && (now - entry.lastPostedAt) < TOPIC_POST_COOLDOWN_MS
    }

    fun selectAdaptiveTopic(
        context: Context,
        defaultTopic: String,
        explorationPool: List<String>,
        now: Long = System.currentTimeMillis()
    ): String {
        val history = getEntries(context)
        if (history.isEmpty()) return TopicGuardrails.normalizeTopic(defaultTopic)

        val safeHistory = history.filterNot { TopicGuardrails.isBlocked(it.topic) }
        val prioritizedHistory = if (safeHistory.isNotEmpty()) safeHistory else history

        val sortedByScore = prioritizedHistory.sortedWith(
            compareByDescending<TopicHistoryEntry> { TopicGuardrails.clampScore(it.topic, it.score) }
                .thenByDescending { it.lastUsedAt }
        )
        val topTopic = sortedByScore.firstOrNull()?.topic ?: TopicGuardrails.normalizeTopic(defaultTopic)
        val neutralTopic = TopicGuardrails.evergreenTopics[(now % TopicGuardrails.evergreenTopics.size).toInt()]
        val explorationTopic = if (explorationPool.isNotEmpty()) {
            val index = (now % explorationPool.size).toInt()
            TopicGuardrails.normalizeTopic(explorationPool[index].replace("_", " "))
        } else {
            TopicGuardrails.normalizeTopic("$defaultTopic update")
        }

        val roll = (now % 100).toInt()
        return when {
            roll < 60 -> topTopic
            roll < 90 -> neutralTopic
            else -> explorationTopic
        }
    }

    fun choosePostableTopic(context: Context, preferredTopic: String, now: Long = System.currentTimeMillis()): String? {
        val normalizedPreferred = TopicGuardrails.normalizeTopic(preferredTopic)
        val history = getEntries(context)
        val preferredEntry = history.firstOrNull { it.topic == normalizedPreferred }
        if (preferredEntry != null && !isTopicPostCooldownActive(context, preferredEntry.topic, now)) {
            return preferredEntry.topic
        }

        return history
            .asSequence()
            .filterNot { TopicGuardrails.isBlocked(it.topic) }
            .filterNot { isTopicPostCooldownActive(context, it.topic, now) }
            .maxWithOrNull(compareBy<TopicHistoryEntry> { it.score }.thenBy { it.lastUsedAt })
            ?.topic
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
                val topic = TopicGuardrails.normalizeTopic(item.optString("topic", ""))
                if (topic.isBlank()) continue
                add(
                    TopicHistoryEntry(
                        topic = topic,
                        score = TopicGuardrails.clampScore(
                            topic,
                            item.optInt("score", 0).coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE)
                        ),
                        lastUsedAt = item.optLong("lastUsedAt", 0L),
                        timesUsed = item.optInt("timesUsed", 1).coerceAtLeast(1),
                        lastPostedAt = item.optLong("lastPostedAt", 0L)
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
        val normalizedTopic = TopicGuardrails.normalizeTopic(topic)
        if (normalizedTopic.isBlank()) return
        val existing = getEntries(context).toMutableList()
        val existingIndex = existing.indexOfFirst { it.topic.equals(normalizedTopic, ignoreCase = true) }
        val existingEntry = existing.getOrNull(existingIndex)
        val rawUpdated = update(existingEntry)
        val updatedEntry = rawUpdated.copy(
            topic = normalizedTopic,
            score = TopicGuardrails.clampScore(normalizedTopic, rawUpdated.score)
        )

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
                    .put("score", TopicGuardrails.clampScore(entry.topic, entry.score.coerceIn(MIN_TOPIC_SCORE, MAX_TOPIC_SCORE)))
                    .put("lastUsedAt", entry.lastUsedAt)
                    .put("timesUsed", entry.timesUsed.coerceAtLeast(1))
                    .put("lastPostedAt", entry.lastPostedAt)
            )
        }
        prefs(context).edit().putString(KEY_TOPIC_HISTORY_JSON, jsonArray.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
