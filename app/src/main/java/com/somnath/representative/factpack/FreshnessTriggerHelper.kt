package com.somnath.representative.factpack

object FreshnessTriggerHelper {
    private val keywordTriggers = listOf(
        "today", "yesterday", "breaking", "law", "election", "war", "ceo", "release", "policy",
        "announced", "launch", "incident", "price", "stock", "rate"
    )

    private val numericPattern = Regex("\\b(\\$?\\d+[\\d,.]*%?)\\b")

    fun requiresFreshness(text: String): Boolean {
        val normalized = text.lowercase()
        val hasKeyword = keywordTriggers.any { normalized.contains(it) }
        val hasNumericSignal = numericPattern.containsMatchIn(normalized)
        return hasKeyword || hasNumericSignal
    }
}
