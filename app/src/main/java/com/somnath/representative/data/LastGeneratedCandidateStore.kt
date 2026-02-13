package com.somnath.representative.data

object LastGeneratedCandidateStore {
    data class CandidateSnapshot(
        val candidateText: String,
        val candidateTopic: String,
        val generatedAt: Long
    )

    @Volatile
    private var lastCandidate: CandidateSnapshot? = null

    fun set(candidateText: String, candidateTopic: String, generatedAt: Long = System.currentTimeMillis()) {
        if (candidateText.isBlank()) {
            lastCandidate = null
            return
        }
        lastCandidate = CandidateSnapshot(
            candidateText = candidateText,
            candidateTopic = candidateTopic,
            generatedAt = generatedAt
        )
    }

    fun clear() {
        lastCandidate = null
    }

    fun get(): CandidateSnapshot? = lastCandidate
}
