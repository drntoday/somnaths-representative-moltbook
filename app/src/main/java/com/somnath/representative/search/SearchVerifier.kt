package com.somnath.representative.search

interface SearchVerifier {
    fun search(query: String, limit: Int = 5): Result<List<SearchResult>>
}

data class SearchResult(
    val title: String,
    val snippet: String,
    val date: String?
)
