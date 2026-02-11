package com.somnath.representative.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SubmoltConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadDefaultSubmolts(context: Context): List<String> {
        return runCatching {
            context.assets.open("config/submolts.json").use { stream ->
                val raw = stream.bufferedReader().use { it.readText() }
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            }
        }.getOrDefault(emptyList())
    }
}
