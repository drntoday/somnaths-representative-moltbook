package com.somnath.representative.data

import android.content.Context

private const val PREFS_NAME = "somnath_rep_prefs"
private const val KEY_MODEL_URI = "local_gguf_model_uri"

object LocalModelPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getModelUri(context: Context): String? =
        prefs(context).getString(KEY_MODEL_URI, null)

    fun setModelUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_MODEL_URI, uri).apply()
    }

    fun clearModelUri(context: Context) {
        prefs(context).edit().remove(KEY_MODEL_URI).apply()
    }
}
