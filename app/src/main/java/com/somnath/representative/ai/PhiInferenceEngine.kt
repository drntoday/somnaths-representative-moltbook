package com.somnath.representative.ai

import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.SimpleGenAI
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.somnath.representative.data.ModelPrefs
import java.io.File

class PhiInferenceEngine(private val context: Context) {
    private val modelPrefs = ModelPrefs(context)

    fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String {
        val uriString = modelPrefs.getModelFolderUriString()
            ?: return "Phi model folder is not configured. Open Settings and select a model folder first."

        val modelUri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return "Saved model folder is invalid. Please clear and re-select the model folder in Settings."

        val localModelDir = runCatching { ensureLocalModelDirectory(modelUri) }.getOrElse {
            return "Unable to read selected model folder. Please re-select the folder and try again."
        }

        return runCatching {
            SimpleGenAI(localModelDir.absolutePath).use { genAI ->
                val params: GeneratorParams = genAI.createGeneratorParams()
                params.use {
                    it.setSearchOption("max_length", maxTokens.toDouble())
                    genAI.generate(it, prompt, null).trim()
                }
            }
        }.getOrElse { throwable ->
            throwable.message ?: "Phi generation failed. Verify the selected model folder contains a valid ONNX Runtime GenAI model."
        }
    }

    private fun ensureLocalModelDirectory(modelTreeUri: Uri): File {
        val root = DocumentFile.fromTreeUri(context, modelTreeUri)
            ?: error("Model folder is not accessible")
        if (!root.isDirectory) {
            error("Model URI is not a folder")
        }

        val outputDir = File(context.cacheDir, MODEL_CACHE_DIR)
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()

        copyTree(root, outputDir)
        return outputDir
    }

    private fun copyTree(source: DocumentFile, targetDir: File) {
        source.listFiles().forEach { child ->
            if (child.isDirectory) {
                val childDir = File(targetDir, child.name ?: "folder")
                childDir.mkdirs()
                copyTree(child, childDir)
            } else if (child.isFile) {
                val targetFile = File(targetDir, child.name ?: "file")
                context.contentResolver.openInputStream(child.uri).use { input ->
                    targetFile.outputStream().use { output ->
                        requireNotNull(input) { "Cannot open model file" }
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_MAX_TOKENS = 128
        private const val MODEL_CACHE_DIR = "phi-model"
    }
}

class PhiRuntime(private val context: Context) {
    private val engine = PhiInferenceEngine(context)

    fun generate(prompt: String, maxTokens: Int = 128): String {
        return engine.generate(prompt = prompt, maxTokens = maxTokens)
    }
}
