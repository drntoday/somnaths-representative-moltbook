package com.somnath.representative.inference

interface PhiInferenceEngine {
    fun generate(prompt: String, maxTokens: Int = 80): Result<String>
}
