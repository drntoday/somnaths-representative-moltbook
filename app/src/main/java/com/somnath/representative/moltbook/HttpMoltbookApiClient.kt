package com.somnath.representative.moltbook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpMoltbookApiClient(
    private val apiKeyProvider: () -> String?
) : MoltbookApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchFeed(submolts: List<String>, limit: Int): List<MoltbookPost> {
        val token = requireToken()
        val submoltsQuery = submolts.joinToString(",")
        val url = "${MoltbookApiEndpoints.BASE_URL}${MoltbookApiEndpoints.FEED_PATH}?submolts=$submoltsQuery&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Feed request failed with HTTP ${response.code}")
            }
            val raw = response.body?.string() ?: "[]"
            return parseFeed(raw)
        }
    }

    override suspend fun fetchThreadContext(postId: String, topN: Int, newestN: Int): ThreadContext {
        val token = requireToken()
        val path = MoltbookApiEndpoints.THREAD_PATH_TEMPLATE.replace("{postId}", postId)
        val url = "${MoltbookApiEndpoints.BASE_URL}$path?topN=$topN&newestN=$newestN"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Thread request failed with HTTP ${response.code}")
            }
            val raw = response.body?.string() ?: "{}"
            return parseThreadContext(raw, postId)
        }
    }

    override suspend fun postComment(postId: String, body: String): Boolean {
        val token = requireToken()
        val path = MoltbookApiEndpoints.COMMENT_PATH_TEMPLATE.replace("{postId}", postId)
        val url = "${MoltbookApiEndpoints.BASE_URL}$path"
        val requestBody = json.encodeToString(CommentRequest.serializer(), CommentRequest(body))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    private fun requireToken(): String = apiKeyProvider()?.trim().orEmpty().ifBlank {
        throw IllegalStateException("API key is missing")
    }

    private fun parseFeed(raw: String): List<MoltbookPost> {
        return try {
            val element = json.parseToJsonElement(raw)
            val postsArray = when (element) {
                is JsonArray -> element
                is JsonObject -> element["posts"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            postsArray.mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    MoltbookPost(
                        id = obj.stringOrEmpty("id"),
                        title = obj.stringOrEmpty("title"),
                        body = obj.stringOrEmpty("body"),
                        author = obj.stringOrEmpty("author"),
                        createdAt = obj.stringOrEmpty("createdAt")
                    )
                }.getOrNull()?.takeIf { it.id.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseThreadContext(raw: String, postId: String): ThreadContext {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val postObj = obj["post"]?.jsonObject
            val commentsArray = obj["comments"]?.jsonArray ?: JsonArray(emptyList())
            val post = MoltbookPost(
                id = postObj?.stringOrEmpty("id").orEmpty().ifBlank { postId },
                title = postObj?.stringOrEmpty("title").orEmpty(),
                body = postObj?.stringOrEmpty("body").orEmpty(),
                author = postObj?.stringOrEmpty("author").orEmpty(),
                createdAt = postObj?.stringOrEmpty("createdAt").orEmpty()
            )
            val comments = commentsArray.mapNotNull { item ->
                runCatching {
                    val commentObj = item.jsonObject
                    MoltbookComment(
                        id = commentObj.stringOrEmpty("id"),
                        body = commentObj.stringOrEmpty("body"),
                        author = commentObj.stringOrEmpty("author"),
                        createdAt = commentObj.stringOrEmpty("createdAt")
                    )
                }.getOrNull()?.takeIf { it.id.isNotBlank() }
            }
            ThreadContext(post = post, comments = comments)
        } catch (_: Exception) {
            ThreadContext(
                post = MoltbookPost(postId, "", "", "", ""),
                comments = emptyList()
            )
        }
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    @Serializable
    private data class CommentRequest(
        @SerialName("body") val body: String
    )
}
