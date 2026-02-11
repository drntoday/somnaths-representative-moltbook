package com.somnath.representative.duplicate

import java.security.MessageDigest
import java.util.Locale

data class Fingerprint(
    val fingerprintString: String,
    val hash: String,
    val keywords: Set<String>
)

object FingerprintGenerator {
    private val stopwords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "has",
        "have", "i", "if", "in", "is", "it", "its", "of", "on", "or", "that", "the",
        "this", "to", "was", "were", "will", "with", "you", "your", "about", "into", "than"
    )

    fun generate(text: String, keywordLimit: Int = 6): Fingerprint {
        val normalized = normalize(text)
        val words = normalized.split(" ").filter { it.isNotBlank() }

        val keywords = words
            .filterNot { stopwords.contains(it) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(keywordLimit)
            .map { it.key }

        val stancePhrase = words.take(10).joinToString(" ").ifBlank { "no stance" }
        val keywordPart = keywords.joinToString(" ").ifBlank { "no keywords" }
        val fingerprintString = "$keywordPart | $stancePhrase"

        return Fingerprint(
            fingerprintString = fingerprintString,
            hash = sha256(fingerprintString),
            keywords = keywords.toSet()
        )
    }

    fun normalize(text: String): String {
        val lowered = text.lowercase(Locale.ROOT)
        val withoutPunctuation = lowered.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        return withoutPunctuation.replace(Regex("\\s+"), " ").trim()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

