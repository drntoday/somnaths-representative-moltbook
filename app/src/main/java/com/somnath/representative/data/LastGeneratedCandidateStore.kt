package com.somnath.representative.data

import com.somnath.representative.scheduler.PromptStyle

object LastGeneratedCandidateStore {
    data class CandidateSnapshot(
        val candidateText: String,
        val candidateTopic: String,
        val candidateStyle: PromptStyle,
        val generatedAt: Long
    )

    @Volatile
    private var lastCandidate: CandidateSnapshot? = null

    fun set(
        candidateText: String,
        candidateTopic: String,
        candidateStyle: PromptStyle,
        generatedAt: Long = System.currentTimeMillis()
    ) {
        if (candidateText.isBlank()) {
            lastCandidate = null
            return
        }
        lastCandidate = CandidateSnapshot(
            candidateText = candidateText,
            candidateTopic = candidateTopic,
            candidateStyle = candidateStyle,
            generatedAt = generatedAt
        )
    }

    fun clear() {
        lastCandidate = null
    }

    fun get(): CandidateSnapshot? = lastCandidate
}
