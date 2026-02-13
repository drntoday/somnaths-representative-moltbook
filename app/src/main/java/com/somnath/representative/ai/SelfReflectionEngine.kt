package com.somnath.representative.ai

import com.somnath.representative.factpack.FactPack

data class CritiqueResult(
    val okToPost: Boolean,
    val needsRewrite: Boolean,
    val issues: List<String>,
    val suggestedRewritePrompt: String?
)

object SelfReflectionEngine {
    private val aggressiveWords = listOf("idiot", "stupid", "moron", "shut up", "dumb")

    fun critique(
        topic: String,
        draft: String,
        factPack: FactPack?,
        safetyDecision: String,
        confidence: Int
    ): CritiqueResult {
        val issues = mutableListOf<String>()
        val words = draft.split(Regex("\\s+")).filter { it.isNotBlank() }
        val normalized = draft.lowercase()

        if (words.size !in 40..120) {
            issues += "Word count must be 40-120"
        }
        if (Regex("(?i)(https?://|www\\.)").containsMatchIn(draft)) {
            issues += "Contains a link"
        }
        if (draft.count { it == '!' } > 2) {
            issues += "Too many exclamation marks"
        }
        if (normalized.contains("as an ai")) {
            issues += "Mentions AI identity"
        }
        if (aggressiveWords.any { normalized.contains(it) }) {
            issues += "Contains aggressive wording"
        }
        val hasAbsoluteClaim = Regex("\b(always|never)\b", RegexOption.IGNORE_CASE).containsMatchIn(draft)
        if (confidence < 80 && hasAbsoluteClaim) {
            issues += "Absolute claim with low confidence"
        }
        if (factPack != null && !hasTimeContext(draft, factPack)) {
            issues += "Missing time context"
        }
        if (safetyDecision == "REWRITE") {
            issues += "Safety layer requested cautious rewrite"
        }

        val needsRewrite = issues.isNotEmpty()
        return CritiqueResult(
            okToPost = !needsRewrite,
            needsRewrite = needsRewrite,
            issues = issues,
            suggestedRewritePrompt = if (needsRewrite) buildRewritePrompt(topic, draft, issues, factPack) else null
        )
    }

    fun buildRewritePrompt(topic: String, draft: String, issues: List<String>, factPack: FactPack?): String {
        val issueBullets = issues.take(5).joinToString("\n") { "- $it" }
        val factBullets = factPack?.bullets.orEmpty().take(3).joinToString("\n") { "- $it" }

        return buildString {
            appendLine("Rewrite this draft to fix the listed issues.")
            appendLine("Topic: $topic")
            appendLine("Issues:")
            appendLine(issueBullets)
            if (factBullets.isNotBlank()) {
                appendLine("FactPack:")
                appendLine(factBullets)
                appendLine("As-of: ${factPack?.asOf.orEmpty()}")
            }
            appendLine("Rules: calm respectful tone, no links, no quotes, no 'as an AI', avoid absolute claims, 40-120 words.")
            appendLine("Draft:")
            appendLine(draft)
        }
    }

    private fun hasTimeContext(draft: String, factPack: FactPack): Boolean {
        if (factPack.asOf.isBlank()) return true
        return draft.contains("as of", ignoreCase = true) || draft.contains(factPack.asOf)
    }
}

