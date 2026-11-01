package com.mx.apn.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mx.apn.data.Apn

/**
 * APN 导入工具
 * 支持多种方式导入 APN 设置
 */
object ApnImporter {

    private const val TAG = "ApnImporter"

    /**
     * 导入结果
     */
    data class ImportResult(
        val success: Boolean,
        val message: String,
        val method: ImportMethod
    )

    enum class ImportMethod {
        CONTENT_RESOLVER,    // 通过 ContentResolver 直接写入
        ROOT,                // 通过 Root 权限写入
        COPY_TO_CLIPBOARD,   // 复制到剪贴板
        OPEN_SETTINGS        // 打开系统设置页
    }

    private val APN_URI: Uri = Uri.parse("content://telephony/carriers")

    /**
     * 尝试通过 ContentResolver 导入 APN
     * 注意：Android 4.0+ 此权限为 signature|system 级别，普通应用通常无法直接写入
     * 但部分设备（已 root 或特殊 ROM）可能可以
     */
    fun importViaContentResolver(context: Context, apn: Apn): ImportResult {
        return try {
            val values = buildContentValues(apn)
            val uri = context.contentResolver.insert(APN_URI, values)
            if (uri != null) {
                Log.i(TAG, "APN 导入成功: $uri")
                ImportResult(true, "APN 已成功导入系统", ImportMethod.CONTENT_RESOLVER)
            } else {
                ImportResult(false, "导入失败，可能缺少系统权限", ImportMethod.CONTENT_RESOLVER)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少 WRITE_APN_SETTINGS 权限", e)
            ImportResult(false, "缺少系统权限，无法直接导入", ImportMethod.CONTENT_RESOLVER)
        } catch (e: Exception) {
            Log.e(TAG, "导入 APN 失败", e)
            ImportResult(false, "导入异常: ${e.message}", ImportMethod.CONTENT_RESOLVER)
        }
    }

    /**
     * 尝试通过 Root 权限导入 APN
     */
    fun importViaRoot(apn: Apn): ImportResult {
        return try {
            if (!isRootAvailable()) {
                return ImportResult(false, "设备未获取 Root 权限", ImportMethod.ROOT)
            }
            // 通过 su 执行 content insert 命令
            val command = buildRootCommand(apn)
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                ImportResult(true, "已通过 Root 权限导入 APN", ImportMethod.ROOT)
            } else {
                val error = process.errorStream.bufferedReader().readText()
                ImportResult(false, "Root 导入失败: $error", ImportMethod.ROOT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root 导入失败", e)
            ImportResult(false, "Root 导入异常: ${e.message}", ImportMethod.ROOT)
        }
    }

    /**
     * 复制 APN 信息到剪贴板
     */
    fun copyToClipboard(context: Context, apn: Apn): ImportResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("APN", apn.toDetailText())
            clipboard.setPrimaryClip(clip)
            ImportResult(true, "APN 信息已复制到剪贴板，请到系统设置中手动添加", ImportMethod.COPY_TO_CLIPBOARD)
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
            ImportResult(false, "复制失败: ${e.message}", ImportMethod.COPY_TO_CLIPBOARD)
        }
    }

    /**
     * 打开系统 APN 设置页面
     */
    fun openApnSettings(context: Context): ImportResult {
        return try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APN_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ImportResult(true, "已打开 APN 设置页面，请手动添加", ImportMethod.OPEN_SETTINGS)
        } catch (e: Exception) {
            Log.e(TAG, "打开设置失败", e)
            ImportResult(false, "无法打开设置页面", ImportMethod.OPEN_SETTINGS)
        }
    }

    /**
     * 构建 ContentValues
     */
    private fun buildContentValues(apn: Apn): ContentValues {
        val values = ContentValues()
        values.put("name", apn.name)
        values.put("apn", apn.apn)
        values.put("proxy", apn.proxy)
        values.put("port", apn.port)
        values.put("user", apn.username)
        values.put("password", apn.password)
        values.put("server", apn.server)
        values.put("mmsc", apn.mmsc)
        values.put("mms_proxy", apn.mmsProxy)
        values.put("mms_port", apn.mmsPort)
        values.put("mcc", apn.mcc)
        values.put("mnc", apn.mnc)
        values.put("authtype", apn.authType)
        values.put("type", apn.type)
        values.put("protocol", apn.protocol)
        values.put("roaming_protocol", apn.roamingProtocol)
        values.put("bearer", apn.bearer)
        values.put("mvno_type", apn.mvnoType)
        values.put("mvno_match_data", apn.mvnoValue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put("user_visible", 1)
            values.put("user_editable", 1)
        }
        return values
    }

    /**
     * 构建 Root 命令
     */
    private fun buildRootCommand(apn: Apn): String {
        val sb = StringBuilder()
        sb.append("content insert --uri content://telephony/carriers")
        sb.append(" --bind name:s:${apn.name}")
        sb.append(" --bind apn:s:${apn.apn}")
        if (apn.proxy.isNotEmpty()) sb.append(" --bind proxy:s:${apn.proxy}")
        if (apn.port.isNotEmpty()) sb.append(" --bind port:s:${apn.port}")
        if (apn.username.isNotEmpty()) sb.append(" --bind user:s:${apn.username}")
        if (apn.password.isNotEmpty()) sb.append(" --bind password:s:${apn.password}")
        if (apn.mmsc.isNotEmpty()) sb.append(" --bind mmsc:s:${apn.mmsc}")
        if (apn.mmsProxy.isNotEmpty()) sb.append(" --bind mms_proxy:s:${apn.mmsProxy}")
        if (apn.mmsPort.isNotEmpty()) sb.append(" --bind mms_port:s:${apn.mmsPort}")
        sb.append(" --bind mcc:s:${apn.mcc}")
        sb.append(" --bind mnc:s:${apn.mnc}")
        sb.append(" --bind type:s:${apn.type}")
        sb.append(" --bind protocol:s:${apn.protocol}")
        sb.append(" --bind roaming_protocol:s:${apn.roamingProtocol}")
        return sb.toString()
    }

    /**
     * 检查 Root 权限是否可用
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            os.write("exit\n".toByteArray())
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取系统中已有的 APN 列表（需要 READ_APN_SETTINGS 权限）
     */
    fun getExistingApns(context: Context): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val cursor = context.contentResolver.query(
                APN_URI,
                arrayOf("_id", "name", "apn", "mcc", "mnc", "type"),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val map = mutableMapOf<String, String>()
                    map["_id"] = it.getString(it.getColumnIndexOrThrow("_id"))
                    map["name"] = it.getString(it.getColumnIndexOrThrow("name"))
                    map["apn"] = it.getString(it.getColumnIndexOrThrow("apn"))
                    map["mcc"] = it.getString(it.getColumnIndexOrThrow("mcc"))
                    map["mnc"] = it.getString(it.getColumnIndexOrThrow("mnc"))
                    map["type"] = it.getString(it.getColumnIndexOrThrow("type"))
                    list.add(map)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取已有 APN 失败", e)
        }
        return list
    }
}
