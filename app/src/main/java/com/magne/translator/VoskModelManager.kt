package com.magne.translator

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class VoskModelManager(private val context: Context) {

    private val modelUrls = mapOf(
        TranslateLanguage.RUSSIAN to "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
        TranslateLanguage.ENGLISH to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        TranslateLanguage.GERMAN to "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
        TranslateLanguage.FRENCH to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        TranslateLanguage.SPANISH to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
        TranslateLanguage.ITALIAN to "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        TranslateLanguage.CHINESE to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        TranslateLanguage.KOREAN to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        TranslateLanguage.JAPANESE to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        TranslateLanguage.PORTUGUESE to "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    )

    fun isModelReady(langCode: String): Boolean {
        val modelDir = File(context.getExternalFilesDir(null), "vosk_models/$langCode")
        return File(modelDir, "model.ready").exists()
    }

    fun getModelPath(langCode: String): String {
        val baseDir = File(context.getExternalFilesDir(null), "vosk_models/$langCode")
        // Vosk models usually unzip into a single directory, we need to find it
        val subDirs = baseDir.listFiles { file -> file.isDirectory }
        return subDirs?.firstOrNull()?.absolutePath ?: baseDir.absolutePath
    }

    suspend fun downloadAndExtractModel(langCode: String, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val urlString = modelUrls[langCode] ?: throw IllegalArgumentException("No URL for language: $langCode")
        val baseDir = File(context.getExternalFilesDir(null), "vosk_models/$langCode")
        
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
        baseDir.mkdirs()

        val zipFile = File(baseDir, "update.zip")

        try {
            // Download
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(zipFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    // Cap downloading progress at 80% to leave room for unzipping progress
                    withContext(Dispatchers.Main) { onProgress((progress * 0.8).toInt()) }
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()

            // Extract
            withContext(Dispatchers.Main) { onProgress(85) }
            unzipFile(zipFile, baseDir)
            withContext(Dispatchers.Main) { onProgress(95) }
            
            // Delete zip
            zipFile.delete()

            // Create ready marker
            File(baseDir, "model.ready").createNewFile()
            withContext(Dispatchers.Main) { onProgress(100) }

        } catch (e: Exception) {
            Log.e("VoskModelManager", "Error downloading model for $langCode", e)
            if (baseDir.exists()) {
                baseDir.deleteRecursively()
            }
            throw e
        }
    }

    private fun unzipFile(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                val canonicalPath = file.canonicalPath
                if (!canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                    throw SecurityException("Zip traversal vulnerability: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
