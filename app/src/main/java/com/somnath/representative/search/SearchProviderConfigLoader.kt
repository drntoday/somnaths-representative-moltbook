package com.somnath.representative.search

import android.content.Context
import org.json.JSONObject

data class SearchProviderConfig(
    val provider: String,
    val endpoint: String,
    val apiKey: String,
    val enabled: Boolean
)

class SearchProviderConfigLoader {
    fun load(context: Context): SearchProviderConfig {
        return try {
            val json = context.assets.open("search_provider.json").bufferedReader().use { it.readText() }
            val objectJson = JSONObject(json)
            SearchProviderConfig(
                provider = objectJson.optString("provider", "stub"),
                endpoint = objectJson.optString("endpoint", ""),
                apiKey = objectJson.optString("apiKey", ""),
                enabled = objectJson.optBoolean("enabled", false)
            )
        } catch (_: Exception) {
            SearchProviderConfig(provider = "stub", endpoint = "", apiKey = "", enabled = false)
        }
    }
}
