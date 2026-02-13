package com.somnath.representative.ai

import android.content.Context

class PhiRuntime(context: Context) {
    private val appContext = context.applicationContext

    fun generate(prompt: String): String {
        return ModelSessionManager.generate(appContext, prompt)
    }

    fun generate(prompt: String, @Suppress("UNUSED_PARAMETER") maxTokens: Int): String {
        return ModelSessionManager.generate(appContext, prompt)
    }
}
