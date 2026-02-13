package com.somnath.representative.ai

import android.content.Context

private const val MODEL_IDLE_TIMEOUT_MS = 20L * 60L * 1000L

object ModelSessionManager {
    private val lock = Any()

    @Volatile
    private var loadedModelPath: String? = null

    @Volatile
    private var lastUsedTimestamp: Long = 0L

    @Suppress("UNUSED_PARAMETER")
    fun generate(context: Context, prompt: String): String {
        val modelPath = ensureSession(context.applicationContext)
            ?: return "Model not downloaded. Go to Settings → Download."

        synchronized(lock) {
            lastUsedTimestamp = System.currentTimeMillis()
        }

        return "Phi runtime is configured to use model at: $modelPath"
    }

    fun ensureSession(context: Context): String? {
        synchronized(lock) {
            unloadIfIdleLocked()

            if (loadedModelPath != null) {
                return loadedModelPath
            }

            val phiModelManager = PhiModelManager(context)
            if (!phiModelManager.isModelReady()) {
                return null
            }

            loadedModelPath = phiModelManager.getModelPath().orEmpty()
            lastUsedTimestamp = System.currentTimeMillis()
            return loadedModelPath
        }
    }

    fun unloadNow() {
        synchronized(lock) {
            loadedModelPath = null
            lastUsedTimestamp = 0L
        }
    }

    private fun unloadIfIdleLocked() {
        val now = System.currentTimeMillis()
        if (loadedModelPath != null && lastUsedTimestamp > 0L && now - lastUsedTimestamp > MODEL_IDLE_TIMEOUT_MS) {
            loadedModelPath = null
            lastUsedTimestamp = 0L
        }
    }
}
