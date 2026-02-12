package com.somnath.representative.inference

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.somnath.representative.data.LocalModelPrefs
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import kotlin.math.min

class LocalReadyPhiInferenceEngine(
    private val context: Context
) : PhiInferenceEngine {

    private val llamaAndroid: LlamaAndroid by lazy { LlamaAndroid(context.contentResolver) }
    private var activeContextId: Int? = null
    private var activeModelUri: String? = null

    override fun generate(prompt: String, maxTokens: Int): Result<String> {
        val modelUriString = LocalModelPrefs.getModelUri(context)
            ?: return Result.failure(IllegalStateException("Select a GGUF model in Settings first."))

        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter a prompt first."))
        }

        return try {
            ensureContext(modelUriString)
            val contextId = activeContextId
                ?: return Result.failure(IllegalStateException("Failed to initialize local model."))

            val completion = llamaAndroid.launchCompletion(
                id = contextId,
                params = mapOf(
                    "prompt" to cleanPrompt,
                    "n_predict" to maxTokens.coerceIn(1, 160),
                    "temperature" to 0.7,
                    "top_p" to 0.9,
                    "top_k" to 40,
                    "n_threads" to min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
                )
            ) ?: return Result.failure(IllegalStateException("Local generation failed. Try reselecting the model."))

            val text = extractGeneratedText(completion)
            if (text.isBlank()) {
                Result.failure(IllegalStateException("Model returned no text."))
            } else {
                Result.success(text)
            }
        } catch (_: OutOfMemoryError) {
            Result.failure(IllegalStateException("Model is too large for this device memory. Try a smaller GGUF."))
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(t.message ?: "Failed to load or run GGUF model."))
        }
    }

    private fun ensureContext(modelUriString: String) {
        if (activeContextId != null && activeModelUri == modelUriString) {
            return
        }

        activeContextId?.let { llamaAndroid.releaseContext(it) }
        activeContextId = null

        val modelUri = Uri.parse(modelUriString)
        val cachedModelFile = copyModelToCache(modelUri)
        val pfd = ParcelFileDescriptor.open(cachedModelFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val modelFd = pfd.detachFd()
        pfd.close()

        val result = llamaAndroid.startEngine(
            params = mapOf(
                "model" to modelUriString,
                "model_fd" to modelFd,
                "use_mmap" to false,
                "use_mlock" to false,
                "n_ctx" to 2048,
                "n_threads" to min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            ),
            tokenCallback = {}
        ) ?: throw IllegalStateException("Unable to open GGUF model.")

        val id = (result["contextId"] as? Number)?.toInt()
            ?: throw IllegalStateException("Missing model context identifier.")

        activeContextId = id
        activeModelUri = modelUriString
    }

    private fun copyModelToCache(uri: Uri): File {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val target = File(modelDir, "selected_model.gguf")
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IllegalStateException("Unable to read selected model file.")
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    private fun extractGeneratedText(result: Map<String, Any>): String {
        val directText = result["text"] as? String
        if (!directText.isNullOrBlank()) return directText.trim()

        val content = result["content"] as? String
        if (!content.isNullOrBlank()) return content.trim()

        val output = result["output"] as? String
        if (!output.isNullOrBlank()) return output.trim()

        return ""
    }
}
