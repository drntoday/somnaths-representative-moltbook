package com.somnath.representative.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

private const val PHI_PREFS = "phi_model_prefs"
private const val KEY_AUTO_DOWNLOAD = "auto_download_on_first_open"
private const val KEY_WIFI_ONLY = "download_wifi_only"
private const val KEY_MODEL_READY = "model_ready"
private const val KEY_MODEL_PATH = "model_path"
private const val KEY_LAST_ERROR = "last_error"
private const val KEY_AUTO_DOWNLOAD_ATTEMPTED = "auto_download_attempted"

sealed class PhiDownloadStatus {
    data object NotDownloaded : PhiDownloadStatus()
    data class Downloading(
        val fileIndex: Int,
        val totalFiles: Int,
        val percent: Int?
    ) : PhiDownloadStatus()

    data object Ready : PhiDownloadStatus()
    data class Error(val message: String) : PhiDownloadStatus()
}

class PhiModelManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PHI_PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    fun isAutoDownloadEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false)

    fun setAutoDownloadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD, enabled).apply()
    }

    fun isWifiOnlyEnabled(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun isModelReady(): Boolean = prefs.getBoolean(KEY_MODEL_READY, false)

    fun getModelPath(): String? = prefs.getString(KEY_MODEL_PATH, null)

    fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    fun getInitialStatus(): PhiDownloadStatus {
        if (isModelReady()) return PhiDownloadStatus.Ready
        val error = getLastError()
        return if (error.isNullOrBlank()) PhiDownloadStatus.NotDownloaded else PhiDownloadStatus.Error(error)
    }

    suspend fun downloadModel(onStatus: (PhiDownloadStatus) -> Unit): PhiDownloadStatus = withContext(Dispatchers.IO) {
        try {
            if (isWifiOnlyEnabled() && !isOnWifi()) {
                val status = PhiDownloadStatus.Error("Wi-Fi required for model download.")
                saveError(status.message)
                return@withContext status
            }

            if (getAvailableBytes() < REQUIRED_FREE_BYTES) {
                val status = PhiDownloadStatus.Error("Insufficient storage. Needs ~4 GB free.")
                saveError(status.message)
                return@withContext status
            }

            val modelDir = getModelDir()
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                val status = PhiDownloadStatus.Error("Unable to create model directory.")
                saveError(status.message)
                return@withContext status
            }

            prefs.edit().putBoolean(KEY_MODEL_READY, false).remove(KEY_MODEL_PATH).remove(KEY_LAST_ERROR).apply()

            REQUIRED_FILES.forEachIndexed { index, fileName ->
                onStatus(PhiDownloadStatus.Downloading(index + 1, REQUIRED_FILES.size, null))
                val targetFile = File(modelDir, fileName)
                val request = Request.Builder().url(resolveUrl(fileName)).build()
                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("Failed to download $fileName (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: throw IOException("Empty response for $fileName")
                    val contentLength = body.contentLength()
                    body.byteStream().use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesCopied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                if (contentLength > 0L) {
                                    val pct = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                                    onStatus(PhiDownloadStatus.Downloading(index + 1, REQUIRED_FILES.size, pct))
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }

            val verificationError = verifyModelFiles()
            if (verificationError != null) {
                prefs.edit().putBoolean(KEY_MODEL_READY, false).remove(KEY_MODEL_PATH).putString(KEY_LAST_ERROR, verificationError).apply()
                return@withContext PhiDownloadStatus.Error(verificationError)
            }

            prefs.edit()
                .putBoolean(KEY_MODEL_READY, true)
                .putString(KEY_MODEL_PATH, getModelDir().absolutePath)
                .remove(KEY_LAST_ERROR)
                .apply()
            PhiDownloadStatus.Ready
        } catch (t: Throwable) {
            val message = t.message ?: "Download failed"
            prefs.edit().putBoolean(KEY_MODEL_READY, false).remove(KEY_MODEL_PATH).putString(KEY_LAST_ERROR, message).apply()
            PhiDownloadStatus.Error(message)
        }
    }

    fun deleteModel(): Boolean {
        val modelDir = getModelDir()
        val deleted = !modelDir.exists() || modelDir.deleteRecursively()
        prefs.edit().putBoolean(KEY_MODEL_READY, false).remove(KEY_MODEL_PATH).remove(KEY_LAST_ERROR).apply()
        return deleted
    }

    suspend fun maybeAutoDownloadOnFirstOpen() {
        withContext(Dispatchers.IO) {
            if (!isAutoDownloadEnabled() || isModelReady()) return@withContext
            val attempted = prefs.getBoolean(KEY_AUTO_DOWNLOAD_ATTEMPTED, false)
            if (attempted) return@withContext
            prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_ATTEMPTED, true).apply()
        }
        downloadModel(onStatus = {})
    }

    private fun verifyModelFiles(): String? {
        val modelDir = getModelDir()
        REQUIRED_FILES.forEach { name ->
            val file = File(modelDir, name)
            if (!file.exists()) return "Missing file: $name"
            if (file.length() <= 0L) return "Invalid file size: $name"
        }
        return null
    }

    private fun saveError(message: String) {
        prefs.edit().putBoolean(KEY_MODEL_READY, false).remove(KEY_MODEL_PATH).putString(KEY_LAST_ERROR, message).apply()
    }

    private fun getModelDir(): File = File(appContext.filesDir, MODEL_DIR)

    private fun getAvailableBytes(): Long {
        val statFs = StatFs(appContext.filesDir.absolutePath)
        return statFs.availableBytes
    }

    private fun isOnWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        private const val MODEL_DIR = "phi_model"
        private const val REQUIRED_FREE_BYTES = 4L * 1024L * 1024L * 1024L

        private const val BASE_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4"

        val REQUIRED_FILES = listOf(
            "genai_config.json",
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "added_tokens.json",
            "config.json",
            "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
            "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data"
        )

        private fun resolveUrl(fileName: String): String = "$BASE_URL/$fileName"
    }
}
