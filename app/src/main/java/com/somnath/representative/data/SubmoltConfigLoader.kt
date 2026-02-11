package com.somnath.representative.data

import android.content.Context
import org.json.JSONArray

class SubmoltConfigLoader {
    fun load(context: Context): List<String> {
        return try {
            val json = context.assets.open("submolts.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index, "")
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
