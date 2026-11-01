package com.mx.apn.data

import com.google.gson.annotations.SerializedName

/**
 * APN 数据模型
 */
data class ApnResponse(
    @SerializedName("version") val version: String,
    @SerializedName("updated") val updated: String,
    @SerializedName("description") val description: String,
    @SerializedName("carriers") val carriers: List<Carrier>
)

/**
 * 运营商信息
 */
data class Carrier(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("name_en") val nameEn: String,
    @SerializedName("mcc") val mcc: String,
    @SerializedName("mnc") val mnc: List<String>,
    @SerializedName("color") val color: String,
    @SerializedName("apns") val apns: List<Apn>
)

/**
 * APN 接入点信息
 */
data class Apn(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("apn") val apn: String,
    @SerializedName("proxy") val proxy: String,
    @SerializedName("port") val port: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("server") val server: String,
    @SerializedName("mmsc") val mmsc: String,
    @SerializedName("mms_proxy") val mmsProxy: String,
    @SerializedName("mms_port") val mmsPort: String,
    @SerializedName("mcc") val mcc: String,
    @SerializedName("mnc") val mnc: String,
    @SerializedName("authtype") val authType: String,
    @SerializedName("type") val type: String,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("roaming_protocol") val roamingProtocol: String,
    @SerializedName("bearer") val bearer: Int,
    @SerializedName("mvno_type") val mvnoType: String,
    @SerializedName("mvno_value") val mvnoValue: String,
    @SerializedName("recommended") val recommended: Boolean,
    @SerializedName("note") val note: String
) {
    /**
     * 获取完整的 APN 信息文本
     */
    fun toDetailText(): String {
        val sb = StringBuilder()
        sb.append("名称: ").append(name).append("\n")
        sb.append("APN: ").append(apn).append("\n")
        if (proxy.isNotEmpty()) sb.append("代理: ").append(proxy).append("\n")
        if (port.isNotEmpty()) sb.append("端口: ").append(port).append("\n")
        if (username.isNotEmpty()) sb.append("用户名: ").append(username).append("\n")
        if (password.isNotEmpty()) sb.append("密码: ").append(password).append("\n")
        if (server.isNotEmpty()) sb.append("服务器: ").append(server).append("\n")
        if (mmsc.isNotEmpty()) sb.append("MMSC: ").append(mmsc).append("\n")
        if (mmsProxy.isNotEmpty()) sb.append("MMS代理: ").append(mmsProxy).append("\n")
        if (mmsPort.isNotEmpty()) sb.append("MMS端口: ").append(mmsPort).append("\n")
        sb.append("MCC: ").append(mcc).append("\n")
        sb.append("MNC: ").append(mnc).append("\n")
        if (authType.isNotEmpty()) sb.append("认证类型: ").append(authType).append("\n")
        sb.append("类型: ").append(type).append("\n")
        sb.append("协议: ").append(protocol).append("\n")
        sb.append("漫游协议: ").append(roamingProtocol)
        return sb.toString()
    }

    /**
     * 获取简短描述
     */
    fun toShortText(): String {
        return "APN: $apn | 类型: $type"
    }
}
