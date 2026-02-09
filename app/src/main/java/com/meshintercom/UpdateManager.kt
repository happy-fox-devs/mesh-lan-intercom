package com.meshintercom

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object UpdateManager {

    private const val GITHUB_OWNER = "happy-fox-devs"
    private const val GITHUB_REPO = "mesh-lan-intercom"
    private const val GITHUB_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    data class UpdateInfo(val version: String, val downloadUrl: String, val releaseNotes: String)

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? =
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(GITHUB_API_URL).build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null

                        val json = JSONObject(response.body?.string() ?: return@withContext null)
                        val tagName = json.getString("tag_name") // e.g., "v1.0.1"
                        val assets = json.getJSONArray("assets")
                        val body = json.optString("body", "No release notes")

                        // Simple version comparison (assumes format vX.Y.Z)
                        /*
                         * NOTE: Ideally use SemVer. For now, we check if tagName != currentVersion.
                         * User should ensure tags match BuildConfig.VERSION_NAME or are lexicographically higher.
                         */
                        val versionClean = tagName.removePrefix("v")

                        if (versionClean != currentVersion) {
                            // Find the .apk asset
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.getString("name").endsWith(".apk")) {
                                    return@withContext UpdateInfo(
                                            version = tagName,
                                            downloadUrl = asset.getString("browser_download_url"),
                                            releaseNotes = body
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return@withContext null
            }

    suspend fun downloadApk(context: Context, url: String): File? =
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null

                        // Save to cache directory (provider_paths.xml points here)
                        val updateDir = File(context.cacheDir, "updates")
                        if (!updateDir.exists()) updateDir.mkdirs()

                        val file = File(updateDir, "update.apk")
                        val fos = FileOutputStream(file)
                        fos.write(response.body?.bytes() ?: return@withContext null)
                        fos.close()
                        return@withContext file
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext null
                }
            }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

        context.startActivity(intent)
    }
}
