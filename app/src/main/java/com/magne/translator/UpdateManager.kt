package com.magne.translator

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
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
                val tagName = json.getString("tag_name")
                // Для простоты MVP хардкодим текущую версию, либо читаем из манифеста
                val currentVersion = "1.0" 

                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                // Сравниваем просто по несовпадению тега, чтобы любой релиз отличный от 1.0 считался обновой
                if (tagName != currentVersion && downloadUrl != null) {
                    return@withContext UpdateResult(tagName, downloadUrl)
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking update", e)
        }
        return@withContext null
    }

    fun downloadAndInstall(updateResult: UpdateResult) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateResult.downloadUrl)

        // Удаляем старый файл если есть
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-update.apk")
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(uri)
            .setTitle("Обновление Переводчика")
            .setDescription("Загрузка версии ${updateResult.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "app-update.apk")

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    installApk(file)
                    context.unregisterReceiver(this)
                }
            }
        }
        // В Android 13+ BroadcastReceiver должен быть зарегистрирован с флагом, 
        // но DownloadManager использует системные бродкасты.
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to start install intent", e)
        }
    }
}

data class UpdateResult(val version: String, val downloadUrl: String)
