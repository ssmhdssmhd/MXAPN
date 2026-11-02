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
 *
 * 安全原则：只新增 APN，不修改/删除已有 APN，不改变当前默认接入点，不影响手机正常使用。
 */
object ApnImporter {

    private const val TAG = "ApnImporter"

    private const val BASE_APN_URI = "content://telephony/carriers"

    /**
     * 导入结果
     */
    data class ImportResult(
        val success: Boolean,
        val message: String,
        val method: ImportMethod
    )

    enum class ImportMethod {
        AUTO,                // 自动导入：系统新增 → Root → 复制并打开设置
        CONTENT_RESOLVER,    // 通过 ContentResolver 直接写入
        ROOT,                // 通过 Root 权限写入
        COPY_TO_CLIPBOARD,   // 复制到剪贴板
        OPEN_SETTINGS        // 打开系统设置页
    }

    /**
     * 根据 subId 构造 APN 写入地址
     * @param subId 订阅 ID；-1 表示不指定卡槽
     */
    private fun apnUri(subId: Int): Uri {
        return if (subId >= 0) {
            Uri.parse("$BASE_APN_URI/subId/$subId")
        } else {
            Uri.parse(BASE_APN_URI)
        }
    }

    /**
     * 自动导入（推荐）
     * 1. 若系统中已存在相同 APN，不做任何改动
     * 2. 尝试系统新增（ContentResolver）
     * 3. 失败则尝试 Root 新增
     * 4. 仍失败则复制信息并打开系统设置页，引导手动新增
     *
     * 只新增，不修改，不影响现有网络使用。
     */
    fun autoImport(context: Context, apn: Apn, subId: Int = -1): ImportResult {
        // 1. 去重检查
        if (hasExistingApn(context, apn, subId)) {
            return ImportResult(
                true,
                "系统中已存在该接入点（${apn.name} / ${apn.apn}），未做任何修改",
                ImportMethod.AUTO
            )
        }

        // 2. 系统新增
        val systemResult = importViaContentResolver(context, apn, subId)
        if (systemResult.success) {
            return systemResult.copy(
                message = "已通过系统新增接入点 ${apn.name}，不影响现有配置",
                method = ImportMethod.AUTO
            )
        }

        // 3. Root 新增
        val rootResult = importViaRoot(apn, subId)
        if (rootResult.success) {
            return rootResult.copy(
                message = "已通过 Root 新增接入点 ${apn.name}，不影响现有配置",
                method = ImportMethod.AUTO
            )
        }

        // 4. 兜底：复制信息 + 打开设置页（只引导用户新增，不修改任何已有项）
        copyToClipboard(context, apn)
        openApnSettings(context)
        return ImportResult(
            false,
            "当前设备限制自动写入 APN。已将配置复制到剪贴板并打开设置页，请手动“新增 APN”（不会影响现有接入点）。",
            ImportMethod.AUTO
        )
    }

    /**
     * 尝试通过 ContentResolver 导入 APN
     * 注意：Android 4.0+ 此权限为 signature|system 级别，普通应用通常无法直接写入
     * 指定 subId 时优先写入对应卡槽的 APN 表
     */
    fun importViaContentResolver(context: Context, apn: Apn, subId: Int = -1): ImportResult {
        val targets = if (subId >= 0) listOf(apnUri(subId), Uri.parse(BASE_APN_URI))
        else listOf(Uri.parse(BASE_APN_URI))
        var lastError = "导入失败，可能缺少系统权限"

        for (uri in targets) {
            try {
                val values = buildContentValues(apn)
                val result = context.contentResolver.insert(uri, values)
                if (result != null) {
                    Log.i(TAG, "APN 导入成功: $result")
                    return ImportResult(
                        true,
                        "APN 已成功新增到系统（${slotLabel(subId)}）",
                        ImportMethod.CONTENT_RESOLVER
                    )
                }
                lastError = "插入返回空，可能缺少系统权限"
            } catch (e: SecurityException) {
                Log.e(TAG, "缺少 WRITE_APN_SETTINGS 权限", e)
                lastError = "缺少系统权限，无法直接新增"
            } catch (e: Exception) {
                Log.e(TAG, "导入 APN 失败: $uri", e)
                lastError = "导入异常: ${e.message}"
            }
        }
        return ImportResult(false, lastError, ImportMethod.CONTENT_RESOLVER)
    }

    /**
     * 尝试通过 Root 权限导入 APN
     */
    fun importViaRoot(apn: Apn, subId: Int = -1): ImportResult {
        return try {
            if (!isRootAvailable()) {
                return ImportResult(false, "设备未获取 Root 权限", ImportMethod.ROOT)
            }
            val command = buildRootCommand(apn, subId)
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                ImportResult(
                    true,
                    "已通过 Root 新增 APN（${slotLabel(subId)}）",
                    ImportMethod.ROOT
                )
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
            ImportResult(true, "APN 信息已复制到剪贴板，请到系统设置中手动新增", ImportMethod.COPY_TO_CLIPBOARD)
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
            ImportResult(true, "已打开 APN 设置页面，请手动新增", ImportMethod.OPEN_SETTINGS)
        } catch (e: Exception) {
            Log.e(TAG, "打开设置失败", e)
            ImportResult(false, "无法打开设置页面", ImportMethod.OPEN_SETTINGS)
        }
    }

    /**
     * 构建 ContentValues
     * 只填写新增 APN 所需字段，不设置 current/carrier_enabled 等默认项，避免影响现有使用。
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
    private fun buildRootCommand(apn: Apn, subId: Int = -1): String {
        val uri = if (subId >= 0) "$BASE_APN_URI/subId/$subId" else BASE_APN_URI
        val sb = StringBuilder()
        sb.append("content insert --uri $uri")
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
     * 检查系统中是否已存在相同 APN（同一卡槽）
     */
    fun hasExistingApn(context: Context, apn: Apn, subId: Int = -1): Boolean {
        val existing = getExistingApns(context, subId)
        return existing.any { item ->
            val sameApn = item["apn"].equals(apn.apn, ignoreCase = true)
            val sameMcc = item["mcc"] == apn.mcc
            val sameMnc = item["mnc"] == apn.mnc
            sameApn && sameMcc && sameMnc
        }
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
     * 获取系统中已有的 APN 列表（需要 READ_APN_SETTINGS 权限，失败时返回空）
     */
    fun getExistingApns(context: Context, subId: Int = -1): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val targets = if (subId >= 0) listOf(apnUri(subId), Uri.parse(BASE_APN_URI))
        else listOf(Uri.parse(BASE_APN_URI))
        for (uri in targets) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
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
                if (list.isNotEmpty()) break
            } catch (e: Exception) {
                Log.e(TAG, "读取已有 APN 失败: $uri", e)
            }
        }
        return list
    }

    private fun slotLabel(subId: Int): String =
        if (subId >= 0) "卡槽 subId=$subId" else "默认卡槽"
}
