package com.somnath.representative.ai

import android.content.Context

class PhiRuntime(context: Context) {
    private val phiModelManager = PhiModelManager(context)

    fun generate(prompt: String): String {
        if (!phiModelManager.isModelReady()) {
            return "Model not downloaded. Go to Settings → Download."
        }

        val modelPath = phiModelManager.getModelPath().orEmpty()
        return "Phi runtime is configured to use model at: $modelPath"
    }
}
