package com.somnath.representative.ai

import android.content.Context

class PhiInferenceEngine(context: Context) {
    private val phiRuntime = PhiRuntime(context)

    fun generate(prompt: String): String = phiRuntime.generate(prompt)
}
