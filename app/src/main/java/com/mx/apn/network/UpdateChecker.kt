package com.mx.apn.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * App 版本更新检测
 */
class UpdateChecker {

    companion object {
        private const val TAG = "UpdateChecker"

        // GitHub Releases 最新版本
        private const val RELEASES_API =
            "https://api.github.com/repos/ssmhdssmhd/MXAPN/releases/latest"

        // 备用版本信息文件
        private const val VERSION_FILE =
            "https://raw.githubusercontent.com/ssmhdssmhd/MXAPN/main/app_version.json"
    }

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val changelog: String,
        val publishedAt: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 检查更新
     * @return 有新版本时返回 UpdateInfo，已是最新时返回 null，失败返回 Result.failure
     */
    suspend fun check(currentVersion: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        // 优先使用 GitHub Releases
        val fromRelease = fetchFromReleases(currentVersion)
        if (fromRelease.isSuccess) {
            return@withContext fromRelease
        }

        Log.w(TAG, "Releases 检测失败，回退版本文件: ${fromRelease.exceptionOrNull()?.message}")
        fetchFromVersionFile(currentVersion)
    }

    private fun fetchFromReleases(currentVersion: String): Result<UpdateInfo?> {
        return try {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MXAPN-Android")
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 ->
                        return Result.failure(Exception("暂无发布版本"))
                    !response.isSuccessful ->
                        return Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string()
                    ?: return Result.failure(Exception("空响应"))
                val json = JsonParser.parseString(body).asJsonObject

                val tagName = json.get("tag_name")?.asString ?: return Result.failure(Exception("缺少 tag_name"))
                val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val changelog = json.get("body")?.asString ?: ""
                val publishedAt = json.get("published_at")?.asString ?: ""

                // 查找 APK 下载地址
                var apkUrl = ""
                json.getAsJsonArray("assets")?.forEach { element ->
                    val asset = element.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString ?: ""
                    }
                }

                if (apkUrl.isEmpty()) {
                    return Result.failure(Exception("发布中未找到 APK"))
                }

                if (isNewer(remoteVersion, currentVersion)) {
                    Result.success(
                        UpdateInfo(
                            versionName = remoteVersion,
                            versionCode = versionToCode(remoteVersion),
                            apkUrl = apkUrl,
                            changelog = changelog,
                            publishedAt = publishedAt
                        )
                    )
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Releases 检测异常", e)
            Result.failure(e)
        }
    }

    private fun fetchFromVersionFile(currentVersion: String): Result<UpdateInfo?> {
        return try {
            val request = Request.Builder()
                .url(VERSION_FILE)
                .header("User-Agent", "MXAPN-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string()
                    ?: return Result.failure(Exception("空响应"))
                val json = JsonParser.parseString(body).asJsonObject

                val remoteVersion = json.get("versionName")?.asString
                    ?: return Result.failure(Exception("缺少 versionName"))
                val apkUrl = json.get("apkUrl")?.asString ?: ""
                val changelog = json.get("changelog")?.asString ?: ""

                if (apkUrl.isEmpty()) {
                    return Result.failure(Exception("缺少 apkUrl"))
                }

                if (isNewer(remoteVersion, currentVersion)) {
                    Result.success(
                        UpdateInfo(
                            versionName = remoteVersion,
                            versionCode = versionToCode(remoteVersion),
                            apkUrl = apkUrl,
                            changelog = changelog,
                            publishedAt = ""
                        )
                    )
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "版本文件检测异常", e)
            Result.failure(e)
        }
    }

    /**
     * 逐段比较版本号，remote 是否比 current 新
     */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parseVersion(remote)
        val c = parseVersion(current)
        val size = maxOf(r.size, c.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version.trim()
            .split('.')
            .mapNotNull { it.toIntOrNull() }
    }

    private fun versionToCode(version: String): Int {
        val parts = parseVersion(version)
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }
}
