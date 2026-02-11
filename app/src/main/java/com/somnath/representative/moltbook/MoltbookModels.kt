package com.somnath.representative.moltbook

data class MoltbookPost(
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val createdAt: String
)

data class MoltbookComment(
    val id: String,
    val body: String,
    val author: String,
    val createdAt: String
)

data class ThreadContext(
    val post: MoltbookPost,
    val comments: List<MoltbookComment>
)
