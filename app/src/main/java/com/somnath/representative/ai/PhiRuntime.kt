package com.somnath.representative.ai

import android.content.Context

class PhiRuntime(context: Context) {
    private val appContext = context.applicationContext

    fun generate(prompt: String): String {
        return ModelSessionManager.generate(appContext, prompt)
    }
}
