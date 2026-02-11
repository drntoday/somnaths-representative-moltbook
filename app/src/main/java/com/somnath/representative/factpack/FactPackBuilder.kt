package com.somnath.representative.factpack

import com.somnath.representative.rss.RssItem
import com.somnath.representative.search.SearchResult
import java.time.LocalDate
import java.time.ZoneOffset

data class FactPack(
    val bullets: List<String>,
    val asOf: String,
    val confidence: Int
)

class FactPackBuilder {
    fun build(
        topic: String,
        rssItems: List<RssItem> = emptyList(),
        searchResults: List<SearchResult> = emptyList()
    ): FactPack {
        val bullets = mutableListOf<String>()
        val cleanTopic = sanitizeForBullet(topic)

        if (cleanTopic.isNotBlank()) {
            bullets.add("Topic focus: $cleanTopic")
        }

        rssItems.take(3).forEach { item ->
            val headline = sanitizeForBullet(item.title)
            if (headline.isNotBlank()) {
                bullets.add("RSS signal: $headline")
            }
        }

        searchResults.take(2).forEach { result ->
            val summary = sanitizeForBullet(result.title.ifBlank { result.snippet })
            if (summary.isNotBlank()) {
                bullets.add("Search signal: $summary")
            }
        }

        if (bullets.size == 1) {
            bullets.add("Current verification is limited; handle claims with caution")
        }

        val confidence = computeConfidence(topic, rssItems, searchResults)

        return FactPack(
            bullets = bullets.take(5),
            asOf = LocalDate.now(ZoneOffset.UTC).toString(),
            confidence = confidence
        )
    }

    private fun computeConfidence(
        topic: String,
        rssItems: List<RssItem>,
        searchResults: List<SearchResult>
    ): Int {
        var score = 35

        if (rssItems.size >= 2 && searchResults.size >= 2) {
            score += 30
        }

        val hasRecentDate = rssItems.any { !it.publishedAt.isNullOrBlank() } || searchResults.any { !it.date.isNullOrBlank() }
        if (hasRecentDate) {
            score += 20
        } else {
            score -= 25
        }

        val freshnessRequired = FreshnessTriggerHelper.requiresFreshness(topic)
        if (!freshnessRequired) {
            score += 10
        }

        if (rssItems.isNotEmpty() && searchResults.isNotEmpty()) {
            score += 20
        }

        if (freshnessRequired && searchResults.isEmpty()) {
            score -= 30
        }

        return score.coerceIn(0, 100)
    }

    private fun sanitizeForBullet(text: String): String {
        return text
            .replace(Regex("https?://\\S+"), "")
            .replace("\"", "")
            .replace("'", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
    }
}
