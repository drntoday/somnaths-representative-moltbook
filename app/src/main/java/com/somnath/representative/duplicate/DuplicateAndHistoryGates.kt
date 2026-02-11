package com.somnath.representative.duplicate

import com.somnath.representative.inference.PhiInferenceEngine

enum class GateStatus {
    ALLOW,
    SKIP,
    UNKNOWN
}

data class GateDecision(
    val status: GateStatus,
    val message: String
)

interface SelfHistoryGate {
    fun check(draftText: String): Result<GateDecision>
}

interface ThreadDuplicationGate {
    fun check(draftText: String, threadComments: List<String>): Result<GateDecision>
}

class StubSelfHistoryGate : SelfHistoryGate {
    override fun check(draftText: String): Result<GateDecision> = Result.success(
        GateDecision(
            status = GateStatus.UNKNOWN,
            message = "Self-history gate is not connected yet; allowing by default."
        )
    )
}

class StubThreadDuplicationGate : ThreadDuplicationGate {
    override fun check(draftText: String, threadComments: List<String>): Result<GateDecision> = Result.success(
        GateDecision(
            status = GateStatus.UNKNOWN,
            message = "Thread-duplication gate is not connected yet; allowing by default."
        )
    )
}

data class LocalGateEvaluation(
    val decision: GateDecision,
    val finalDraftText: String,
    val finalFingerprint: Fingerprint
)

class LocalTinyCacheGate(
    private val cacheStore: TinyFingerprintCacheStore,
    private val phiInferenceEngine: PhiInferenceEngine
) {
    private val keywordIndex = mutableMapOf<String, Set<String>>()

    fun evaluateCommentDraft(draftText: String): LocalGateEvaluation {
        val firstFingerprint = FingerprintGenerator.generate(draftText)
        val entries = cacheStore.getRecentFingerprints()

        if (entries.any { it.hash == firstFingerprint.hash }) {
            return LocalGateEvaluation(
                decision = GateDecision(GateStatus.SKIP, "Skipped due to duplicate"),
                finalDraftText = draftText,
                finalFingerprint = firstFingerprint
            )
        }

        if (isNearDuplicate(firstFingerprint.keywords)) {
            val rewritten = rewriteOnce(draftText)
            val rewrittenFingerprint = FingerprintGenerator.generate(rewritten)
            if (entries.any { it.hash == rewrittenFingerprint.hash } || isNearDuplicate(rewrittenFingerprint.keywords)) {
                return LocalGateEvaluation(
                    decision = GateDecision(
                        GateStatus.SKIP,
                        "Skipped due to duplicate"
                    ),
                    finalDraftText = rewritten,
                    finalFingerprint = rewrittenFingerprint
                )
            }

            return LocalGateEvaluation(
                decision = GateDecision(
                    GateStatus.ALLOW,
                    "Local cache gate allowed after one rewrite attempt."
                ),
                finalDraftText = rewritten,
                finalFingerprint = rewrittenFingerprint
            )
        }

        return LocalGateEvaluation(
            decision = GateDecision(GateStatus.ALLOW, "Local cache gate allowed."),
            finalDraftText = draftText,
            finalFingerprint = firstFingerprint
        )
    }

    fun registerPostedFingerprint(fingerprint: Fingerprint, type: String) {
        cacheStore.addFingerprint(
            TinyFingerprintEntry(
                hash = fingerprint.hash,
                ts = System.currentTimeMillis(),
                type = type
            )
        )
        keywordIndex[fingerprint.hash] = fingerprint.keywords
    }

    private fun rewriteOnce(draftText: String): String {
        val prompt = "Rewrite this text to be clearly different in wording and angle while keeping calm tone: $draftText"
        val rewritten = phiInferenceEngine.generate(prompt = prompt).getOrNull()?.trim().orEmpty()
        return rewritten.ifBlank { draftText }
    }

    private fun isNearDuplicate(newKeywords: Set<String>): Boolean {
        if (newKeywords.isEmpty()) return false
        return keywordIndex.values.any { existingKeywords ->
            if (existingKeywords.isEmpty()) {
                false
            } else {
                val overlap = newKeywords.intersect(existingKeywords).size.toDouble() / newKeywords.size.toDouble()
                overlap >= 0.70
            }
        }
    }
}

