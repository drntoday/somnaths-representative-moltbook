package com.somnath.representative.data

object LastGeneratedCandidateStore {
    @Volatile
    private var lastCandidate: String? = null

    fun set(candidate: String) {
        lastCandidate = candidate
    }

    fun get(): String? = lastCandidate
}
