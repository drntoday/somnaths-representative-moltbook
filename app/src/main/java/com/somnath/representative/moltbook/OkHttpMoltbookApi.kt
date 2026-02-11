package com.somnath.representative.moltbook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OkHttpMoltbookApi(
    private val apiKeyProvider: () -> String?
) : MoltbookApi {

    private val client = OkHttpClient()

    override suspend fun fetchFeed(submolts: List<String>, limit: Int): List<PostSummary> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().orEmpty()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API key is missing. Save Moltbook API Key in Settings first.")
        }
        if (submolts.isEmpty()) {
            throw IllegalStateException("No submolts configured.")
        }

        val url = buildString {
            append(MoltbookApiEndpoints.BASE_URL)
            append(MoltbookApiEndpoints.FEED_PATH)
            append("?limit=")
            append(limit.coerceAtLeast(1))
            append("&submolts=")
            append(submolts.joinToString(","))
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Feed request failed (${response.code}). Check MoltbookApiEndpoints.kt placeholders or API credentials."
                )
            }
            parseFeed(rawBody)
        }
    }

    override suspend fun postComment(postId: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = apiKeyProvider().orEmpty()
            if (apiKey.isBlank()) {
                error("API key is missing. Save Moltbook API Key in Settings first.")
            }
            if (postId.isBlank()) {
                error("Cannot post comment: postId is empty.")
            }

            val url = MoltbookApiEndpoints.BASE_URL + MoltbookApiEndpoints.COMMENT_PATH.format(postId)
            val payload = JSONObject().put("body", body)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(
                        "Post comment failed (${response.code}). Check MoltbookApiEndpoints.kt placeholders or API credentials."
                    )
                }
            }
        }
    }

    private fun parseFeed(rawBody: String): List<PostSummary> {
        if (rawBody.isBlank()) return emptyList()

        return try {
            val root = JSONObject(rawBody)
            parsePostsArray(root.optJSONArray("items") ?: root.optJSONArray("posts"))
        } catch (_: Exception) {
            try {
                parsePostsArray(JSONArray(rawBody))
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parsePostsArray(array: JSONArray?): List<PostSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id", "").ifBlank {
                    item.optString("postId", "")
                }
                if (id.isBlank()) continue
                add(
                    PostSummary(
                        id = id,
                        title = item.optNullableString("title"),
                        body = item.optNullableString("body") ?: item.optNullableString("content"),
                        author = item.optNullableString("author") ?: item.optNullableString("username"),
                        createdAt = item.optNullableString("createdAt") ?: item.optNullableString("created_at")
                    )
                )
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return optString(key, "").ifBlank { null }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
