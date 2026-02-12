package com.somnath.representative.safety

import com.somnath.representative.factpack.FactPack
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class SafetyDecision {
    ALLOW,
    REWRITE,
    ASK_QUESTION,
    SKIP
}

enum class SensitiveLevel {
    LOW,
    MED,
    HIGH
}

data class SafetyGuardResult(
    val decision: SafetyDecision,
    val reason: String,
    val confidence: Int,
    val sensitiveLevel: SensitiveLevel,
    val finalText: String,
    val injectionDetected: Boolean
)

class SafetyGuard(
    private val confidenceScorer: ConfidenceScorer = ConfidenceScorer()
) {

    fun evaluate(
        threadText: String,
        draftText: String,
        factPack: FactPack? = null
    ): SafetyGuardResult {
        val injectionDetected = containsInjectionPattern(threadText)
        val sensitiveLevel = classifySensitivity("$threadText\n$draftText")
        val confidence = confidenceScorer.score(
            threadText = threadText,
            factPack = factPack,
            sensitiveLevel = sensitiveLevel,
            injectionDetected = injectionDetected
        )

        if (injectionDetected) {
            return SafetyGuardResult(
                decision = SafetyDecision.SKIP,
                reason = "Possible prompt-injection pattern detected",
                confidence = confidence,
                sensitiveLevel = sensitiveLevel,
                finalText = "",
                injectionDetected = true
            )
        }

        if (sensitiveLevel == SensitiveLevel.HIGH && confidence < 80) {
            return SafetyGuardResult(
                decision = SafetyDecision.SKIP,
                reason = "High-sensitivity topic with low confidence",
                confidence = confidence,
                sensitiveLevel = sensitiveLevel,
                finalText = "",
                injectionDetected = false
            )
        }

        return when {
            confidence >= 80 -> result(SafetyDecision.ALLOW, "High confidence", confidence, sensitiveLevel, enforceOutputRules(draftText, false))
            confidence >= 60 -> result(SafetyDecision.REWRITE, "Use cautious phrasing", confidence, sensitiveLevel, enforceOutputRules(draftText, true))
            confidence >= 40 -> result(SafetyDecision.ASK_QUESTION, "Need clarification before claims", confidence, sensitiveLevel, buildNeutralQuestion(threadText))
            else -> result(SafetyDecision.SKIP, "Confidence too low", confidence, sensitiveLevel, "")
        }
    }

    private fun result(
        decision: SafetyDecision,
        reason: String,
        confidence: Int,
        sensitiveLevel: SensitiveLevel,
        finalText: String
    ) = SafetyGuardResult(decision, reason, confidence, sensitiveLevel, finalText, injectionDetected = false)

    private fun containsInjectionPattern(threadText: String): Boolean {
        val normalized = threadText.lowercase()
        val patterns = listOf(
            "ignore previous instructions",
            "reveal your system prompt",
            "paste your api key",
            "click this",
            "run this",
            "act as"
        )
        return patterns.any { normalized.contains(it) }
    }

    private fun classifySensitivity(text: String): SensitiveLevel {
        val normalized = text.lowercase()
        val highSignals = listOf(
            "kill", "slaughter", "massacre", "lynch", "beheading", "rape", "porn", "nude",
            "genocide", "ethnic cleansing", "terror attack", "nazi", "kike", "chink"
        )
        val medSignals = listOf(
            "idiot", "stupid", "hate", "racist", "election", "party", "war", "regime",
            "left wing", "right wing", "propaganda", "civil unrest"
        )

        return when {
            highSignals.any { normalized.contains(it) } -> SensitiveLevel.HIGH
            medSignals.any { normalized.contains(it) } -> SensitiveLevel.MED
            else -> SensitiveLevel.LOW
        }
    }

    private fun enforceOutputRules(draftText: String, includeTimeContext: Boolean): String {
        val withoutLinks = draftText.replace(Regex("https?://\\S+"), "")
        val withoutQuotes = withoutLinks
            .replace("\"", "")
            .replace("'", "")
            .replace(Regex("(?im)^>.*$"), "")

        val cleaned = withoutQuotes.replace(Regex("\\s+"), " ").trim()
        val monthYear = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MMM yyyy"))
        val timePrefix = if (includeTimeContext && !cleaned.contains("As of", ignoreCase = true)) "As of $monthYear, " else ""
        val cautiousPrefix = if (includeTimeContext && !cleaned.lowercase().contains("seems")) "it seems " else ""

        return clampWordCount((timePrefix + cautiousPrefix + cleaned).trim())
    }

    private fun buildNeutralQuestion(threadText: String): String {
        val topicHint = threadText.replace(Regex("\\s+"), " ").trim().take(70).ifBlank { "this topic" }
        val question = "Could you share reliable and recent context for $topicHint so we can keep the reply accurate and practical?"
        return clampWordCount(question)
    }

    private fun clampWordCount(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        val paddedWords = words.toMutableList()
        val padding = listOf(
            "I", "want", "to", "stay", "calm", "and", "helpful", "while", "avoiding", "overconfident", "claims", "until", "details", "are", "clear", "for", "everyone", "involved."
        )

        while (paddedWords.size < 40) {
            paddedWords.addAll(padding)
        }

        return paddedWords.take(120).joinToString(" ")
    }
}
