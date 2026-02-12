package com.somnath.representative.data

import android.content.Context
import android.net.Uri

class ModelPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveModelFolderUri(uri: Uri) {
        prefs.edit().putString(KEY_MODEL_FOLDER_URI, uri.toString()).apply()
    }

    fun getModelFolderUriString(): String? {
        return prefs.getString(KEY_MODEL_FOLDER_URI, null)
    }

    fun clearModelFolder() {
        prefs.edit().remove(KEY_MODEL_FOLDER_URI).apply()
    }

    companion object {
        private const val PREFS_NAME = "model_prefs"
        private const val KEY_MODEL_FOLDER_URI = "model_folder_uri"
    }
}
