package com.somnath.representative.data

object TopicGuardrails {
    private val blockedKeywords = listOf(
        "politics",
        "political",
        "war",
        "hate",
        "sex",
        "sexual",
        "breaking",
        "today",
        "price",
        "election",
        "ceo",
        "lawsuit"
    )

    val evergreenTopics: List<String> = listOf(
        "android development best practices",
        "kotlin language tips",
        "productivity systems for engineers",
        "reading habits for developers",
        "learning new programming concepts",
        "debugging techniques on android",
        "writing clear technical documentation",
        "code review and collaboration",
        "mobile app performance optimization",
        "healthy habits for focused work"
    )

    fun normalizeTopic(rawTopic: String): String {
        return rawTopic
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(80)
    }

    fun isBlocked(topic: String): Boolean {
        val normalized = normalizeTopic(topic)
        if (normalized.isBlank()) return false
        return blockedKeywords.any { blocked -> normalized.contains(blocked) }
    }

    fun clampScore(topic: String, score: Int): Int {
        return if (isBlocked(topic)) score.coerceAtMost(0) else score
    }
}
