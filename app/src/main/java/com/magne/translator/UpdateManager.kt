package com.magne.translator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
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

    suspend fun downloadAndInstallSilent(updateResult: UpdateResult) = withContext(Dispatchers.IO) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Установка обновления ${updateResult.version}...", Toast.LENGTH_LONG).show()
            }

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

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val out = session.openWrite("update", 0, -1)
            connection.inputStream.use { input ->
                out.use { output ->
                    input.copyTo(output)
                }
            }
            session.fsync(out)

            val intent = Intent(context, MainActivity::class.java)
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or flag)
            
            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to start silent install", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Ошибка обновления", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class UpdateResult(val version: String, val downloadUrl: String)
