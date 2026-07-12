package com.magne.translator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Environment
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateManager(private val context: Context) {

    suspend fun checkUpdate(): UpdateResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/qutlawsoasis-debug/translator/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                var tagName = json.getString("tag_name")
                if (tagName.startsWith("v")) tagName = tagName.substring(1) // Убираем 'v' если есть
                
                val currentVersion = BuildConfig.VERSION_NAME

                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (tagName != currentVersion && downloadUrl != null) {
                    return@withContext UpdateResult(tagName, downloadUrl)
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking update", e)
        }
        return@withContext null
    }

    suspend fun downloadAndInstallSilent(updateResult: UpdateResult, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        try {
            var currentUrl = updateResult.downloadUrl
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                    if (redirects > 10) throw Exception("Too many redirects")
                } else {
                    break
                }
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream

            // Скачиваем во временный файл
            val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            val output = FileOutputStream(apkFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    onProgress(progress)
                }
            }
            output.flush()
            output.close()
            input.close()

            try {
                // Пытаемся установить тихо (если Android 12+ и есть права)
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                val out = session.openWrite("update", 0, apkFile.length())
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
                out.close()

                val intent = Intent(context, MainActivity::class.java)
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or flag)
                
                session.commit(pendingIntent.intentSender)
                session.close()
            } catch (e: SecurityException) {
                // Нет прав на тихую установку, откатываемся к обычному диалогу
                Log.e("UpdateManager", "Silent install forbidden, falling back to standard install", e)
                fallbackToStandardInstall(apkFile)
            }

        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to download/install", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Ошибка обновления", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fallbackToStandardInstall(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to start fallback install intent", e)
        }
    }
}

data class UpdateResult(val version: String, val downloadUrl: String)
