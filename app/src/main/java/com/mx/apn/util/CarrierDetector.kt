package com.mx.apn.util

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 运营商检测工具
 */
object CarrierDetector {

    private const val TAG = "CarrierDetector"

    data class SimInfo(
        val mcc: String,
        val mnc: String,
        val operatorName: String,
        val isAvailable: Boolean
    )

    /**
     * SIM 卡槽信息
     * @param slotIndex 卡槽序号（0 开始，界面显示为卡1/卡2）
     * @param subId 订阅 ID，用于多卡 APN 写入
     */
    data class SimSlot(
        val slotIndex: Int,
        val subId: Int,
        val displayName: String,
        val carrierName: String
    ) {
        fun label(): String = if (carrierName.isNotEmpty()) "$displayName · $carrierName" else displayName
    }

    /**
     * 获取当前插入的 SIM 卡槽列表
     * 无权限或异常时返回空列表
     */
    fun getSimSlots(context: Context): List<SimSlot> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return emptyList()
            val infos = sm.activeSubscriptionInfoList ?: return emptyList()
            infos.mapIndexed { index, info ->
                SimSlot(
                    slotIndex = index,
                    subId = info.subscriptionId,
                    displayName = "卡${index + 1}",
                    carrierName = info.carrierName?.toString() ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 SIM 卡槽失败", e)
            emptyList()
        }
    }

    /**
     * 获取当前 SIM 卡的运营商信息
     */
    fun getSimInfo(context: Context): SimInfo {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkOperator = tm.networkOperator ?: ""
            val mcc = if (networkOperator.length >= 3) networkOperator.substring(0, 3) else ""
            val mnc = if (networkOperator.length > 3) networkOperator.substring(3) else ""
            val operatorName = tm.networkOperatorName ?: ""
            val isAvailable = mcc.isNotEmpty() && mnc.isNotEmpty()
            Log.d(TAG, "SIM: mcc=$mcc, mnc=$mnc, name=$operatorName, available=$isAvailable")
            SimInfo(mcc, mnc, operatorName, isAvailable)
        } catch (e: SecurityException) {
            Log.e(TAG, "无读取电话状态权限", e)
            SimInfo("", "", "", false)
        } catch (e: Exception) {
            Log.e(TAG, "获取 SIM 信息失败", e)
            SimInfo("", "", "", false)
        }
    }

    /**
     * 判断是否为中国运营商
     */
    fun isChineseCarrier(mcc: String): Boolean = mcc == "460"

    /**
     * 根据 MCC/MNC 获取运营商中文名
     */
    fun getCarrierName(mcc: String, mnc: String): String {
        return when {
            mcc == "460" && mnc in listOf("00", "02", "04", "07", "08", "13") -> "中国移动"
            mcc == "460" && mnc in listOf("01", "06", "09") -> "中国联通"
            mcc == "460" && mnc in listOf("03", "05", "11") -> "中国电信"
            mcc == "460" && mnc in listOf("15", "60") -> "中国广电"
            else -> "未知运营商"
        }
    }

    /**
     * 获取网络类型描述
     */
    @Suppress("DEPRECATION")
    fun getNetworkType(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                else -> "未知"
            }
        } catch (e: Exception) {
            "未知"
        }
    }
}
