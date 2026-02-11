package com.somnath.representative.inference

class LocalReadyPhiInferenceEngine : PhiInferenceEngine {
    override fun generate(prompt: String, maxTokens: Int): Result<String> {
        val sanitizedPrompt = prompt.trim().ifBlank { "(empty prompt)" }

        // TODO(M4->M5): Load Phi model files from app-specific storage (e.g.,
        // context.filesDir/models/phi/ or context.getExternalFilesDir(null)/models/phi/) once
        // a runtime backend is selected (ONNX Runtime GenAI or llama.cpp).
        // TODO(M4->M5): Validate model/tokenizer presence before inference and surface a clear
        // setup error to the UI if files are missing.
        // TODO(M4->M5): Add actual token generation pipeline using maxTokens.

        return Result.success(
            "Phi engine not configured (no model on device). Prompt was: $sanitizedPrompt"
        )
    }
}
