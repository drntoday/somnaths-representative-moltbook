package com.somnath.representative.search

class StubSearchVerifier : SearchVerifier {
    override fun search(query: String, limit: Int): Result<List<SearchResult>> {
        return Result.failure(
            IllegalStateException(
                "Search provider not configured. Add provider keys in search_provider.json to enable search verification."
            )
        )
    }
}
