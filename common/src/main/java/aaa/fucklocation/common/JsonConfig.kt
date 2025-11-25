package aaa.fucklocation.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** JSON编码器配置 */
private val encoder = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * JSON配置数据类
 * 
 * 用于序列化和反序列化应用程序配置，包括：
 * - 配置版本
 * - 日志设置
 * - 模板配置
 * - 应用范围配置
 */
@Serializable
data class JsonConfig(
    var configVersion: Int = BuildConfig.CONFIG_VERSION,
    var detailLog: Boolean = false,
    var maxLogSize: Int = 512,
    var forceMountData: Boolean = true,
    val templates: MutableMap<String, Template> = mutableMapOf(),
    val scope: MutableMap<String, AppConfig> = mutableMapOf()
) {
    /**
     * 位置模板数据类
     * 
     * 用于存储位置模拟所需的各项参数，包括：
     * - 经纬度
     * - 基站信息（ECI/NCI、PCI、TAC等）
     * - 频段信息（EARFCN/NRARFCN、带宽）
     */
    @Serializable
    data class Template(
        val longitude: String? = null,
        val latitude: String? = null,
        val eciNci: String? = null,
        val pci: String? = null,
        val tac: String? = null,
        val earfcnNrarfcn: String? = null,
        val bandwidth: String? = null
    ) {
        override fun toString() = encoder.encodeToString(this)
    }

    /**
     * 应用配置数据类
     * 
     * 用于存储特定应用的配置选项，包括：
     * - 是否排除系统应用
     * - 应用的模板集合
     */
    @Serializable
    data class AppConfig(
        var excludeSystemApps: Boolean = true,
        var applyTemplates: MutableSet<String> = mutableSetOf()
    ) {
        override fun toString() = encoder.encodeToString(this)
    }

    companion object {
        /**
         * 解析JSON字符串为JsonConfig对象
         * 
         * @param json JSON格式的字符串
         * @return 解析后的JsonConfig对象
         */
        fun parse(json: String) = encoder.decodeFromString<JsonConfig>(json)
    }

    /**
     * 将对象序列化为JSON字符串
     * 
     * @return JSON格式的字符串
     */
    override fun toString() = encoder.encodeToString(this)
}
