package com.magne.translator

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File

class ModelDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val modelsDir: File = context.getExternalFilesDir("onnx_models") ?: File(context.filesDir, "onnx_models")
    private val baseUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0/"

    private val nllbFiles = listOf(
        "NLLB_cache_initializer.onnx",
        "NLLB_decoder.onnx",
        "NLLB_embed_and_lm_head.onnx",
        "NLLB_encoder.onnx"
    )

    private val whisperFiles = listOf(
        "Whisper_cache_initializer.onnx",
        "Whisper_cache_initializer_batch.onnx",
        "Whisper_decoder.onnx",
        "Whisper_detokenizer.onnx",
        "Whisper_encoder.onnx",
        "Whisper_initializer.onnx"
    )

    private val allFiles = nllbFiles + whisperFiles
    private var downloadJob: Job? = null
    private val activeDownloads = mutableMapOf<String, Long>()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun isFileReady(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 1024
    }

    fun areAllModelsReady(): Boolean {
        return nllbFiles.all { isFileReady(it) } &&
               whisperFiles.all { isFileReady(it) } &&
               File(modelsDir, "models.ready").exists()
    }

    fun startDownload(
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (areAllModelsReady()) {
            onSuccess()
            return
        }

        val missingFiles = allFiles.filterNot { isFileReady(it) }

        if (missingFiles.isEmpty()) {
            File(modelsDir, "models.ready").createNewFile()
            onSuccess()
            return
        }

        activeDownloads.clear()

        missingFiles.forEach { fileName ->
            val file = File(modelsDir, fileName)
            if (file.exists()) file.delete()

            val request = DownloadManager.Request(Uri.parse(baseUrl + fileName))
                .setTitle("Загрузка модели: $fileName")
                .setDestinationInExternalFilesDir(context, "onnx_models", fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

            activeDownloads[fileName] = downloadManager.enqueue(request)
        }

        trackProgress(onProgress, onSuccess, onError)
    }

    private fun trackProgress(
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    var downloadedBytes = 0L
                    var hasError = false
                    var allActiveFinished = true

                    for ((fileName, downloadId) in activeDownloads) {
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

                            if (statusIndex >= 0) {
                                val status = cursor.getInt(statusIndex)
                                if (status == DownloadManager.STATUS_FAILED) {
                                    hasError = true
                                } else if (status != DownloadManager.STATUS_SUCCESSFUL) {
                                    allActiveFinished = false
                                }
                            }

                            if (downloadedIndex >= 0) {
                                downloadedBytes += cursor.getLong(downloadedIndex).coerceAtLeast(0)
                            }
                            cursor.close()
                        } else {
                            hasError = true
                        }
                    }

                    if (hasError) {
                        withContext(Dispatchers.Main) {
                            onError("Ошибка при загрузке файлов. Проверьте интернет и попробуйте снова.")
                        }
                        break
                    }

                    val readyCount = allFiles.count { isFileReady(it) }
                    
                    val alreadyReadyBytes = allFiles.filter { isFileReady(it) && !activeDownloads.containsKey(it) }
                        .sumOf { File(modelsDir, it).length() }

                    val totalDownloadedMB = (downloadedBytes + alreadyReadyBytes) / (1024 * 1024)

                    withContext(Dispatchers.Main) {
                        onProgress("Скачиваю модели... $readyCount/10 файлов (${totalDownloadedMB}MB / 1.3GB)")
                    }

                    if (allActiveFinished) {
                        if (activeDownloads.keys.all { isFileReady(it) }) {
                            File(modelsDir, "models.ready").createNewFile()
                            withContext(Dispatchers.Main) { onSuccess() }
                            break
                        }
                    }

                    delay(1000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Ошибка отслеживания загрузки: ${e.message}")
                }
            }
        }
    }

    fun cancelDownloads() {
        downloadJob?.cancel()
        activeDownloads.values.forEach { downloadManager.remove(it) }
        activeDownloads.clear()
    }
}
