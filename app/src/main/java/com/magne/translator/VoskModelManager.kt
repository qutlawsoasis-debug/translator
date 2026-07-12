package com.magne.translator

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class VoskModelManager(private val context: Context) {

    val modelUrls = mapOf(
        TranslateLanguage.RUSSIAN to "https://alphacephei.com/vosk/models/vosk-model-ru-0.42.zip",
        TranslateLanguage.ENGLISH to "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
        TranslateLanguage.GERMAN to "https://alphacephei.com/vosk/models/vosk-model-de-0.21.zip",
        TranslateLanguage.FRENCH to "https://alphacephei.com/vosk/models/vosk-model-fr-0.22.zip",
        TranslateLanguage.SPANISH to "https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip",
        TranslateLanguage.ITALIAN to "https://alphacephei.com/vosk/models/vosk-model-it-0.22.zip",
        TranslateLanguage.CHINESE to "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip",
        TranslateLanguage.KOREAN to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        TranslateLanguage.JAPANESE to "https://alphacephei.com/vosk/models/vosk-model-ja-0.22.zip",
        TranslateLanguage.PORTUGUESE to "https://alphacephei.com/vosk/models/vosk-model-pt-fb-v0.1.1-20220516_2113.zip"
    )

    val modelSizesMB = mapOf(
        TranslateLanguage.RUSSIAN to 1800,
        TranslateLanguage.ENGLISH to 1800,
        TranslateLanguage.GERMAN to 289,
        TranslateLanguage.FRENCH to 1400,
        TranslateLanguage.SPANISH to 1400,
        TranslateLanguage.ITALIAN to 1200,
        TranslateLanguage.CHINESE to 1300,
        TranslateLanguage.KOREAN to 82,
        TranslateLanguage.JAPANESE to 1000,
        TranslateLanguage.PORTUGUESE to 1500
    )

    fun getBaseDir(langCode: String): File {
        return File(context.getExternalFilesDir(null), "vosk_models/$langCode")
    }

    fun isModelReady(langCode: String): Boolean {
        return File(getBaseDir(langCode), "model.ready").exists()
    }

    fun getModelPath(langCode: String): String {
        val baseDir = getBaseDir(langCode)
        val subDirs = baseDir.listFiles { file -> file.isDirectory }
        return subDirs?.firstOrNull()?.absolutePath ?: baseDir.absolutePath
    }

    fun startDownload(langCode: String): Long {
        val urlString = modelUrls[langCode] ?: throw IllegalArgumentException("No URL for language: $langCode")
        val baseDir = getBaseDir(langCode)
        if (baseDir.exists()) baseDir.deleteRecursively()
        baseDir.mkdirs()

        val zipFile = File(baseDir, "model.zip")
        val request = DownloadManager.Request(Uri.parse(urlString))
            .setDestinationUri(Uri.fromFile(zipFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setTitle("TalkSync: загрузка модели $langCode")
            .setAllowedOverMetered(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): Pair<Int, Int>? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor != null && cursor.moveToFirst()) {
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            
            if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                val downloaded = cursor.getLong(bytesDownloadedIndex)
                var total = cursor.getLong(bytesTotalIndex)
                if (total <= 0) total = 100 * 1024 * 1024 // fallback
                cursor.close()
                return Pair((downloaded / (1024 * 1024)).toInt(), (total / (1024 * 1024)).toInt())
            }
            cursor.close()
        }
        return null
    }

    fun getDownloadStatus(downloadId: Long): Int {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex != -1) {
                val status = cursor.getInt(statusIndex)
                cursor.close()
                return status
            }
            cursor.close()
        }
        return DownloadManager.STATUS_FAILED
    }

    fun cancelDownload(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
    }

    suspend fun extractModel(langCode: String) = withContext(Dispatchers.IO) {
        val baseDir = getBaseDir(langCode)
        val zipFile = File(baseDir, "model.zip")
        if (!zipFile.exists()) throw Exception("Downloaded file missing")
        
        unzipFile(zipFile, baseDir)
        zipFile.delete()
        File(baseDir, "model.ready").createNewFile()
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
