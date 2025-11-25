package aaa.fucklocation.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val encoder = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
data class JsonConfig(
    var configVersion: Int = BuildConfig.CONFIG_VERSION,
    var detailLog: Boolean = false,
    var maxLogSize: Int = 512,
    var forceMountData: Boolean = true,
    val templates: MutableMap<String, Template> = mutableMapOf(),
    val scope: MutableMap<String, AppConfig> = mutableMapOf()
) {
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

    @Serializable
    data class AppConfig(
        var excludeSystemApps: Boolean = true,
        var applyTemplates: MutableSet<String> = mutableSetOf()
    ) {
        override fun toString() = encoder.encodeToString(this)
    }

    companion object {
        fun parse(json: String) = encoder.decodeFromString<JsonConfig>(json)
    }

    override fun toString() = encoder.encodeToString(this)
}
