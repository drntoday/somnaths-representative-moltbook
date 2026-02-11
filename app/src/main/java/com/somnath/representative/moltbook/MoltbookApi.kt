package com.somnath.representative.moltbook

data class PostSummary(
    val id: String,
    val title: String?,
    val body: String?,
    val author: String?,
    val createdAt: String?
)

interface MoltbookApi {
    suspend fun fetchFeed(submolts: List<String>, limit: Int): List<PostSummary>
    suspend fun postComment(postId: String, body: String): Result<Unit>
}
