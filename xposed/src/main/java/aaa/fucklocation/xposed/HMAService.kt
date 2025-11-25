package aaa.fucklocation.xposed

import android.content.pm.ApplicationInfo
import android.content.pm.IPackageManager
import android.os.Build
import aaa.fucklocation.common.*
import aaa.fucklocation.xposed.hook.*
import java.io.File

/**
 * HMA服务主类，负责管理位置模拟和Hook功能
 * 
 * 该类实现了IHMAService.Stub接口，提供以下主要功能：
 * - 管理配置文件和日志
 * - 安装和管理各种Hook
 * - 处理应用包的隐藏逻辑
 * - 提供服务状态查询和控制接口
 * 
 * @param pms 包管理器接口，用于获取应用信息
 */
class HMAService(val pms: IPackageManager) : IHMAService.Stub() {

    companion object {
        /** 日志标签 */
        private const val TAG = "HMA-Service"
        /** 服务实例，用于全局访问 */
        var instance: HMAService? = null
    }

    /** 日志功能是否可用 */
    @Volatile
    var logcatAvailable = false

    /** 数据目录路径 */
    private lateinit var dataDir: String
    /** 配置文件 */
    private lateinit var configFile: File
    /** 当前日志文件 */
    private lateinit var logFile: File
    /** 旧日志文件 */
    private lateinit var oldLogFile: File

    /** 配置锁，用于同步配置操作 */
    private val configLock = Any()
    /** 日志锁，用于同步日志操作 */
    private val loggerLock = Any()
    /** 系统应用包名集合 */
    private val systemApps = mutableSetOf<String>()
    /** 框架Hook集合 */
    private val frameworkHooks = mutableSetOf<IFrameworkHook>()

    /** 当前配置，默认启用详细日志 */
    var config = JsonConfig().apply { detailLog = true }
        private set

    /** 过滤计数器，记录Hook调用次数 */
    var filterCount = 0
        @JvmName("getFilterCountInternal") get
        set(value) {
            field = value
            // 每增加100次保存一次计数
            if (field % 100 == 0) {
                synchronized(configLock) {
                    File("$dataDir/filter_count").writeText(field.toString())
                }
            }
        }

    init {
        // 初始化服务
        searchDataDir()
        instance = this
        loadConfig()
        installHooks()
        logI(TAG, "HMA service initialized")
    }

    /**
     * 搜索并初始化数据目录
     * 
     * 该方法会查找或创建数据目录，处理旧版本目录迁移，
     * 并创建必要的日志文件和配置文件
     */
    private fun searchDataDir() {
        File("/data/system").list()?.forEach {
            if (it.startsWith("afucklocation")) {
                if (!this::dataDir.isInitialized) {
                    val newDir = File("/data/misc/$it")
                    File("/data/system/$it").renameTo(newDir)
                    dataDir = newDir.path
                } else {
                    File("/data/system/$it").deleteRecursively()
                }
            }
        }
        File("/data/misc").list()?.forEach {
            if (it.startsWith("afucklocation")) {
                if (!this::dataDir.isInitialized) {
                    dataDir = "/data/misc/$it"
                } else if (dataDir != "/data/misc/$it") {
                    File("/data/misc/$it").deleteRecursively()
                }
            }
        }
        if (!this::dataDir.isInitialized) {
            dataDir = "/data/misc/afucklocation_" + Utils.generateRandomString(16)
        }

        File("$dataDir/log").mkdirs()
        configFile = File("$dataDir/config.json")
        logFile = File("$dataDir/log/runtime.log")
        oldLogFile = File("$dataDir/log/old.log")
        logFile.renameTo(oldLogFile)
        logFile.createNewFile()

        logcatAvailable = true
        logI(TAG, "Data dir: $dataDir")
    }

    /**
     * 加载配置文件
     * 
     * 从配置文件中读取配置信息，包括过滤计数和用户配置。
     * 如果配置文件不存在或版本不匹配，将使用默认配置
     */
    private fun loadConfig() {
        File("$dataDir/filter_count").also {
            runCatching {
                if (it.exists()) filterCount = it.readText().toInt()
            }.onFailure { e ->
                logW(TAG, "Failed to load filter count, set to 0", e)
                it.writeText("0")
            }
        }
        if (!configFile.exists()) {
            logI(TAG, "Config file not found")
            return
        }
        val loading = runCatching {
            val json = configFile.readText()
            JsonConfig.parse(json)
        }.getOrElse {
            logE(TAG, "Failed to parse config.json", it)
            return
        }
        if (loading.configVersion != BuildConfig.CONFIG_VERSION) {
            logW(TAG, "Config version mismatch, need to reload")
            return
        }
        config = loading
        logI(TAG, "Config loaded")
    }

    /**
     * 安装Hook
     * 
     * 根据系统版本安装相应的Hook，包括：
     * - 包管理器Hook
     * - 平台兼容性Hook
     * - 其他框架Hook
     */
    private fun installHooks() {
        Utils.getInstalledApplicationsCompat(pms, 0, 0).mapNotNullTo(systemApps) {
            if (it.flags and ApplicationInfo.FLAG_SYSTEM != 0) it.packageName else null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            frameworkHooks.add(PmsHookTarget34(this))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            frameworkHooks.add(PmsHookTarget33(this))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            frameworkHooks.add(PmsHookTarget30(this))
        } else {
            frameworkHooks.add(PmsHookTarget28(this))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            frameworkHooks.add(PlatformCompatHook(this))
        }

        frameworkHooks.forEach(IFrameworkHook::load)
        logI(TAG, "Hooks installed")
    }

    /**
     * 检查指定包是否启用了Hook
     * 
     * @param packageName 应用包名
     * @return 如果启用了Hook返回true，否则返回false
     */
    fun isHookEnabled(packageName: String) = config.scope.containsKey(packageName)

    /**
     * 判断是否应该隐藏指定包
     * 
     * 根据调用者和查询包名，以及配置信息判断是否应该隐藏查询包
     * 
     * @param caller 调用者包名
     * @param query 查询包名
     * @return 如果应该隐藏返回true，否则返回false
     */
    fun shouldHide(caller: String?, query: String?): Boolean {
        if (caller == null || query == null) return false
        if (caller in Constants.packagesShouldNotHide || query in Constants.packagesShouldNotHide) return false
        if ((caller == Constants.GMS_PACKAGE_NAME || caller == Constants.GSF_PACKAGE_NAME) && query == Constants.APP_PACKAGE_NAME) return false // If apply hide on gms, hma app will crash 😓
        if (caller == query) return false
        val appConfig = config.scope[caller] ?: return false
        if (appConfig.excludeSystemApps && query in systemApps) return false

        // 简化逻辑：只要包在配置中就启用 Hook
        return true
    }

    /**
     * 停止服务
     * 
     * @param cleanEnv 是否清理运行环境，如果为true将删除所有数据
     */
    override fun stopService(cleanEnv: Boolean) {
        logI(TAG, "Stop service")
        synchronized(loggerLock) {
            logcatAvailable = false
        }
        synchronized(configLock) {
            frameworkHooks.forEach(IFrameworkHook::unload)
            frameworkHooks.clear()
            if (cleanEnv) {
                logI(TAG, "Clean runtime environment")
                File(dataDir).deleteRecursively()
                return
            }
        }
        instance = null
    }

    /**
     * 添加日志
     * 
     * 将日志消息写入日志文件，如果日志文件过大则自动清理
     * 
     * @param parsedMsg 格式化后的日志消息
     */
    fun addLog(parsedMsg: String) {
        synchronized(loggerLock) {
            if (!logcatAvailable) return
            if (logFile.length() / 1024 > config.maxLogSize) clearLogs()
            logFile.appendText(parsedMsg)
        }
    }

    /**
     * 同步配置
     * 
     * 接收新的配置并更新到配置文件，同时通知所有Hook配置已更改
     * 
     * @param json 新配置的JSON字符串
     */
    override fun syncConfig(json: String) {
        synchronized(configLock) {
            configFile.writeText(json)
            val newConfig = JsonConfig.parse(json)
            if (newConfig.configVersion != BuildConfig.CONFIG_VERSION) {
                logW(TAG, "Sync config: version mismatch, need reboot")
                return
            }
            config = newConfig
            frameworkHooks.forEach(IFrameworkHook::onConfigChanged)
        }
        logD(TAG, "Config synced")
    }

    /**
     * 获取服务版本
     * 
     * @return 当前服务版本号
     */
    override fun getServiceVersion() = BuildConfig.SERVICE_VERSION

    /**
     * 获取过滤计数
     * 
     * @return 当前的过滤计数
     */
    override fun getFilterCount() = filterCount

    /**
     * 获取日志内容
     * 
     * @return 当前日志文件的内容
     */
    override fun getLogs() = synchronized(loggerLock) {
        logFile.readText()
    }

    /**
     * 清理日志
     * 
     * 将当前日志文件重命名为旧日志文件，并创建新的空日志文件
     */
    override fun clearLogs() {
        synchronized(loggerLock) {
            oldLogFile.delete()
            logFile.renameTo(oldLogFile)
            logFile.createNewFile()
        }
    }

    /**
     * 获取指定应用的模板
     * 
     * @param packageName 应用包名
     * @return 应用的模板字符串，如果没有配置则返回空字符串
     */
    override fun getTemplate(packageName: String): String {
        val appConfig = config.scope[packageName] ?: return ""
        val templateName = appConfig.applyTemplates.firstOrNull() ?: return ""
        val template = config.templates[templateName] ?: return ""
        return template.toString()
    }
}
