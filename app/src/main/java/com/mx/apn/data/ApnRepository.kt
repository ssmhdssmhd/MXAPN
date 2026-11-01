package com.mx.apn.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mx.apn.util.CarrierDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * APN 数据仓库
 * 从网络获取最新 APN 数据
 */
class ApnRepository(private val context: Context) {

    companion object {
        private const val TAG = "ApnRepository"
        // APN 数据远程地址（GitHub 仓库）
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/ssmhdssmhd/MXAPN/main/apn_data.json"
        private const val LOCAL_ASSET = "apn_data.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 从网络获取最新 APN 数据
     */
    suspend fun fetchRemote(): Result<ApnResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
                val data = gson.fromJson(body, ApnResponse::class.java)
                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取远程 APN 数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从本地 assets 加载备用 APN 数据
     */
    suspend fun loadLocal(): Result<ApnResponse> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(LOCAL_ASSET).bufferedReader().use { it.readText() }
            val data = gson.fromJson(json, ApnResponse::class.java)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "加载本地 APN 数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取 APN 数据：优先网络，失败回退本地
     */
    suspend fun getApnData(): Result<ApnResponse> {
        val remote = fetchRemote()
        if (remote.isSuccess) {
            return remote
        }
        Log.w(TAG, "远程获取失败，使用本地数据")
        return loadLocal()
    }

    /**
     * 根据当前 SIM 卡推荐运营商
     */
    fun detectCarrier(carriers: List<Carrier>): Carrier? {
        val simInfo = CarrierDetector.getSimInfo(context)
        return carriers.firstOrNull { carrier ->
            carrier.mcc == simInfo.mcc && carrier.mnc.any { it == simInfo.mnc }
        }
    }

    /**
     * 获取推荐的 APN（优先默认互联网类型）
     */
    fun getRecommendedApn(carrier: Carrier?): Apn? {
        if (carrier == null) return null
        // 优先推荐标记为 recommended 的
        carrier.apns.firstOrNull { it.recommended }?.let { return it }
        // 其次找 default 类型
        return carrier.apns.firstOrNull { it.type.contains("default") }
    }
}
