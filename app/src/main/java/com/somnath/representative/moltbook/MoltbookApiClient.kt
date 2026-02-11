package com.somnath.representative.moltbook

interface MoltbookApiClient {
    suspend fun fetchFeed(submolts: List<String>, limit: Int): List<MoltbookPost>
    suspend fun fetchThreadContext(postId: String, topN: Int, newestN: Int): ThreadContext
    suspend fun postComment(postId: String, body: String): Boolean
}
